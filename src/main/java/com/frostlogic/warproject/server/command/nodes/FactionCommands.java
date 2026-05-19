package com.frostlogic.warproject.server.command.nodes;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.attachment.PlayerState;
import com.frostlogic.warproject.attachment.Role;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import com.frostlogic.warproject.persistence.Database;
import com.frostlogic.warproject.persistence.dao.AuditLogDao;
import com.frostlogic.warproject.persistence.dao.PassportsDao;
import com.frostlogic.warproject.persistence.dao.PlayersDao;
import com.frostlogic.warproject.server.chat.GeneralChatService;
import com.frostlogic.warproject.server.collab.CollaboratorService;
import com.frostlogic.warproject.server.command.AcceptCommandHandler;
import com.frostlogic.warproject.server.command.argument.FactionArgument;
import com.frostlogic.warproject.server.command.argument.RoleArgument;
import com.frostlogic.warproject.server.role.RoleResolver;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.Optional;

/**
 * Brigadier nodes for the faction-management subcommands of {@code /wp ...}.
 * <p>
 * Implements the six commands listed in design §7 / Req. 10.2 that operate on
 * faction lifecycle and chat:
 * <ul>
 *   <li>{@code /wp accept <name>} (COMMANDER+) — accept a candidate; delegates to
 *       {@link AcceptCommandHandler#acceptCandidate(ServerPlayer, ServerPlayer,
 *       AcceptCommandHandler.AcceptSource)} with {@link AcceptCommandHandler.AcceptSource#COMMAND}.</li>
 *   <li>{@code /wp up <name> <role>} (COMMANDER+) — promote/demote target's role.
 *       For non-OP initiators: target role must be strictly below initiator's role and
 *       the target's faction must equal the initiator's faction (Req. 10.4).</li>
 *   <li>{@code /wp set comand <player> <faction>} (OP) — appoint a commander for the
 *       given faction. Sets target {@code role = COMMANDER} and {@code faction}.</li>
 *   <li>{@code /wp collab <name> <reason>} (OP) — mark target as collaborator
 *       (Req. 11.1). Full collaborator service implementation lands in task 18.1; this
 *       node performs the DB update + audit and sets the attachment.</li>
 *   <li>{@code /wp uncollab <name> <reason>} (OP) — clear target's collaborator flag
 *       (Req. 11.3). Same notes as {@code /wp collab}.</li>
 *   <li>{@code /wp generalchat <message>} (COMMANDER+) — placeholder for the closed
 *       chat (Req. 15.1). Full delivery and 30-second cooldown logic lands in task 16.1
 *       once {@code GeneralChatService} exists; this node only validates the role gate
 *       and writes a {@code GENERALCHAT_SEND} audit row.</li>
 * </ul>
 * <p>
 * <strong>Atomicity.</strong> Every state-changing command performs its primary
 * effect <em>and</em> writes a corresponding {@code audit_log} row inside a single
 * {@link Database#transaction(java.util.function.Consumer)} (Req. 10.5). Post-commit
 * side effects (attachment update, target-side notifications) run only if the
 * transaction commits.
 * <p>
 * <strong>Authorization.</strong> The Brigadier {@code requires(...)} clause acts as
 * a <em>visibility</em> gate (so a SOLDIER does not see commands they cannot use).
 * The actual permission check is repeated inside {@code executes(...)} via
 * {@link RoleResolver#atLeast(CommandSourceStack, Role)} so that a role change
 * between auto-complete and execution still gets rejected (design §7).
 * <p>
 * Requirements: 10.2, 10.4, 10.5, 11.1, 11.3, 15.1
 * Design: §7
 */
public final class FactionCommands {

    /** Brigadier permission level corresponding to vanilla OP. */
    private static final int OP_LEVEL = 2;
    /**
     * Brigadier permission level acting as a visibility floor for COMMANDER-or-higher
     * commands. The actual COMMANDER role check lives inside the executor and is
     * resolved through {@link RoleResolver}, which inspects the player's
     * {@link WpAttachmentTypes#ROLE} attachment.
     */
    private static final int COMMANDER_VISIBILITY_LEVEL = 0;

    private static Database database;
    private static PlayersDao playersDao;
    private static PassportsDao passportsDao;
    private static AuditLogDao auditLogDao;
    private static GeneralChatService generalChatService;
    private static CollaboratorService collaboratorService;

    private FactionCommands() {
        // utility class — no instantiation
    }

    /**
     * Wires up the static service dependencies. Must be called from
     * {@code WpCommandRoot.onServerStarted} before commands run.
     */
    public static void init(Database db, PlayersDao players, PassportsDao passports, AuditLogDao audit,
                            GeneralChatService generalChat, CollaboratorService collaborator) {
        database = db;
        playersDao = players;
        passportsDao = passports;
        auditLogDao = audit;
        generalChatService = generalChat;
        collaboratorService = collaborator;
    }

    /**
     * Registers all six faction subcommands onto the given {@code /wp} root node.
     */
    public static void register(LiteralArgumentBuilder<CommandSourceStack> wpRoot) {
        // ── /wp accept <name> ───────────────────────────────────────────────
        wpRoot.then(Commands.literal("accept")
                .requires(src -> RoleResolver.atLeast(src, Role.COMMANDER))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(FactionCommands::runAccept)));

        // ── /wp up <name> <role> ────────────────────────────────────────────
        // The role argument is intentionally a vanilla string (with custom
        // suggestions) rather than a custom Brigadier argument type. Custom
        // argument types require both a registry binding *and* an
        // ArgumentTypeInfos.BY_CLASS binding to be safely serialised in the
        // command tree the server pushes to the client during the
        // configuration phase. If only the registry side is wired, the client
        // disconnects with the generic "Неверные данные игрока" /
        // multiplayer.disconnect.invalid_player_data error the moment a
        // player gains visibility into the gated subcommand (e.g. after /op).
        // We resolve the enum inside the executor instead.
        wpRoot.then(Commands.literal("up")
                .requires(src -> RoleResolver.atLeast(src, Role.COMMANDER))
                .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("role", StringArgumentType.word())
                                .suggests((ctx, builder) ->
                                        SharedSuggestionProvider.suggest(RoleArgument.allRoleNames(), builder))
                                .executes(FactionCommands::runUp))));

        // ── /wp set comand <player> <faction> ───────────────────────────────
        // Two-word literal as documented in design §7 ("set comand" is a deliberate
        // typo preserved verbatim). Children attach onto the inner literal.
        // Same rationale as /wp up: the faction argument is a vanilla string
        // with custom suggestions to avoid the custom-arg-type sync hazard.
        wpRoot.then(Commands.literal("set")
                .requires(src -> src.hasPermission(OP_LEVEL))
                .then(Commands.literal("comand")
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("faction", StringArgumentType.word())
                                        .suggests((ctx, builder) ->
                                                SharedSuggestionProvider.suggest(FactionArgument.allFactionNames(), builder))
                                        .executes(FactionCommands::runSetCommander)))));

        // ── /wp collab <name> <reason> ──────────────────────────────────────
        wpRoot.then(Commands.literal("collab")
                .requires(src -> src.hasPermission(OP_LEVEL))
                .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(FactionCommands::runCollab))));

        // ── /wp uncollab <name> <reason> ────────────────────────────────────
        wpRoot.then(Commands.literal("uncollab")
                .requires(src -> src.hasPermission(OP_LEVEL))
                .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(FactionCommands::runUncollab))));

        // ── /wp generalchat <message> ───────────────────────────────────────
        wpRoot.then(Commands.literal("generalchat")
                .requires(src -> src.hasPermission(COMMANDER_VISIBILITY_LEVEL))
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(FactionCommands::runGeneralChat)));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Command implementations
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * {@code /wp accept <name>}. Re-checks the COMMANDER role gate (defence in
     * depth against role changes between auto-complete and execution), then
     * delegates to the shared {@link AcceptCommandHandler}.
     */
    private static int runAccept(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!servicesReady(source)) return 0;

        // Role re-check — design §7: requires(...) is visibility, executor enforces.
        if (!RoleResolver.atLeast(source, Role.COMMANDER)) {
            source.sendFailure(Component.translatable("wp.command.error.no_permission"));
            return 0;
        }

        ServerPlayer initiator = source.getPlayer();
        if (initiator == null) {
            source.sendFailure(Component.translatable("wp.error.player_only"));
            return 0;
        }

        ServerPlayer target;
        try {
            target = EntityArgument.getPlayer(context, "target");
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.translatable("wp.command.error.player_not_found", "?"));
            return 0;
        }

        AcceptCommandHandler handler = new AcceptCommandHandler(database, playersDao, passportsDao, auditLogDao);
        AcceptCommandHandler.AcceptResult result =
                handler.acceptCandidate(initiator, target, AcceptCommandHandler.AcceptSource.COMMAND);

        return switch (result) {
            case AcceptCommandHandler.AcceptResult.Success ignored -> {
                source.sendSuccess(() -> Component.translatable("wp.command.accept.success",
                        target.getGameProfile().getName()), true);
                yield Command.SINGLE_SUCCESS;
            }
            case AcceptCommandHandler.AcceptResult.AlreadyAccepted ignored -> {
                source.sendFailure(Component.translatable("wp.command.accept.already_accepted",
                        target.getGameProfile().getName()));
                yield 0;
            }
            case AcceptCommandHandler.AcceptResult.Failure failure -> {
                source.sendFailure(Component.translatable(failure.errorKey()));
                yield 0;
            }
        };
    }

    /**
     * {@code /wp up <name> <role>}. Promotes/demotes the target's administrative
     * role with the additional checks required by Req. 10.4:
     * <ul>
     *   <li>The new role must be strictly below the initiator's role
     *       ({@code newRole.level &lt; initiatorRole.level}).</li>
     *   <li>For COMMANDER initiators, the target must belong to the same faction.</li>
     *   <li>OP initiators bypass the faction restriction (cross-faction admin tool).</li>
     * </ul>
     */
    private static int runUp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!servicesReady(source)) return 0;

        if (!RoleResolver.atLeast(source, Role.COMMANDER)) {
            source.sendFailure(Component.translatable("wp.command.error.no_permission"));
            return 0;
        }

        ServerPlayer target;
        try {
            target = EntityArgument.getPlayer(context, "target");
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.translatable("wp.command.error.player_not_found", "?"));
            return 0;
        }
        String roleInput = StringArgumentType.getString(context, "role");
        Role newRole = parseRole(roleInput);
        if (newRole == null) {
            source.sendFailure(Component.translatable("wp.command.argument.role.invalid", roleInput));
            return 0;
        }

        Role initiatorRole = RoleResolver.resolve(source);
        boolean isOp = initiatorRole == Role.OP;

        // Strict-below-initiator gate (Req. 10.4). OP can hand out anything strictly
        // below OP (i.e. up to GENERAL); a COMMANDER can hand out SOLDIER/CANDIDATE.
        if (newRole.level() >= initiatorRole.level()) {
            source.sendFailure(Component.translatable("wp.command.up.role_too_high"));
            return 0;
        }

        // Same-faction gate for non-OP initiators (Req. 10.4).
        if (!isOp) {
            ServerPlayer initiator = source.getPlayer();
            if (initiator == null) {
                source.sendFailure(Component.translatable("wp.error.player_only"));
                return 0;
            }
            Optional<FactionId> initFaction = initiator.getData(WpAttachmentTypes.FACTION.get());
            Optional<FactionId> targetFaction = target.getData(WpAttachmentTypes.FACTION.get());
            if (initFaction.isEmpty() || targetFaction.isEmpty()
                    || initFaction.get() != targetFaction.get()) {
                source.sendFailure(Component.translatable("wp.command.up.faction_mismatch"));
                return 0;
            }
        }

        long now = System.currentTimeMillis();
        String actorUuid = actorUuid(source);
        String actorName = source.getTextName();
        String targetUuid = target.getStringUUID();
        String targetName = target.getGameProfile().getName();
        String newRoleName = newRole.getSerializedName();
        Role oldRole = target.getData(WpAttachmentTypes.ROLE.get());
        String oldRoleName = oldRole.getSerializedName();
        // RANK_UP is the historical audit action used elsewhere in the codebase
        // (DaoRoundTripProperties seed list). We keep the same name for either
        // direction (promote / demote) and put the direction in extra_json.
        String action = "RANK_UP";

        try {
            database.transaction(conn -> {
                playersDao.setRole(conn, targetUuid, newRoleName);
                auditLogDao.insert(conn, now, actorUuid, actorName,
                        targetUuid, targetName, action, null,
                        "{\"from\":\"" + oldRoleName + "\","
                                + "\"to\":\"" + newRoleName + "\"}");
            });
        } catch (RuntimeException e) {
            WarProject.LOGGER.error("[WP /up] Transaction failed for {} → {}: {}",
                    actorName, targetName, e.getMessage(), e);
            source.sendFailure(Component.translatable("wp.command.error.internal"));
            return 0;
        }

        // Post-commit: refresh attachment so subsequent role-gated commands see
        // the new role immediately without waiting for a relog.
        target.setData(WpAttachmentTypes.ROLE.get(), newRole);

        source.sendSuccess(() -> Component.translatable("wp.command.up.success",
                targetName, newRoleName), true);
        target.sendSystemMessage(Component.translatable("wp.command.up.notify", newRoleName));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code /wp set comand <player> <faction>}. OP-only: appoint a commander for
     * the given faction by atomically setting {@code role = COMMANDER} and
     * {@code faction = <faction>} on the target.
     */
    private static int runSetCommander(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!servicesReady(source)) return 0;

        // Re-check OP gate at execution time.
        if (!source.hasPermission(OP_LEVEL)) {
            source.sendFailure(Component.translatable("wp.command.error.no_permission"));
            return 0;
        }

        ServerPlayer target;
        try {
            target = EntityArgument.getPlayer(context, "target");
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.translatable("wp.command.error.player_not_found", "?"));
            return 0;
        }
        String factionInput = StringArgumentType.getString(context, "faction");
        FactionId faction = parseFaction(factionInput);
        if (faction == null) {
            source.sendFailure(Component.translatable("wp.command.argument.faction.invalid", factionInput));
            return 0;
        }

        long now = System.currentTimeMillis();
        String actorUuid = actorUuid(source);
        String actorName = source.getTextName();
        String targetUuid = target.getStringUUID();
        String targetName = target.getGameProfile().getName();
        String factionName = faction.getSerializedName();
        String roleName = Role.COMMANDER.getSerializedName();

        try {
            database.transaction(conn -> {
                playersDao.setRoleAndFaction(conn, targetUuid, roleName, factionName);
                auditLogDao.insert(conn, now, actorUuid, actorName,
                        targetUuid, targetName, "RANK_UP",
                        "set commander",
                        "{\"to\":\"" + roleName + "\","
                                + "\"faction\":\"" + factionName + "\"}");
            });
        } catch (RuntimeException e) {
            WarProject.LOGGER.error("[WP /set comand] Transaction failed for {} → {}: {}",
                    actorName, targetName, e.getMessage(), e);
            source.sendFailure(Component.translatable("wp.command.error.internal"));
            return 0;
        }

        target.setData(WpAttachmentTypes.ROLE.get(), Role.COMMANDER);
        target.setData(WpAttachmentTypes.FACTION.get(), Optional.of(faction));

        source.sendSuccess(() -> Component.translatable("wp.command.set_commander.success",
                targetName, Component.translatable(faction.displayNameKey())), true);
        target.sendSystemMessage(Component.translatable("wp.command.set_commander.notify",
                Component.translatable(faction.displayNameKey())));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code /wp collab <name> <reason>} (Req. 11.1). Marks the target as a
     * collaborator by delegating to {@link CollaboratorService#set}, which
     * performs the {@code players} update + {@code COLLAB} audit row in a
     * single transaction and refreshes the
     * {@link WpAttachmentTypes#COLLABORATOR} attachment post-commit.
     */
    private static int runCollab(CommandContext<CommandSourceStack> context) {
        return runCollabInternal(context, true);
    }

    /**
     * {@code /wp uncollab <name> <reason>} (Req. 11.3). Clears the collaborator
     * flag by delegating to {@link CollaboratorService#unset}. The reason is
     * preserved as the {@code reason} field of the {@code UNCOLLAB} audit row
     * but is not stored in the {@code players} table after a successful
     * uncollab (the column is set back to {@code NULL}).
     */
    private static int runUncollab(CommandContext<CommandSourceStack> context) {
        return runCollabInternal(context, false);
    }

    /**
     * Shared dispatch for {@code /wp collab} and {@code /wp uncollab}: extracts
     * arguments, runs the OP gate, and delegates to {@link CollaboratorService}.
     */
    private static int runCollabInternal(CommandContext<CommandSourceStack> context, boolean mark) {
        CommandSourceStack source = context.getSource();
        if (!servicesReady(source)) return 0;
        if (collaboratorService == null) {
            source.sendFailure(Component.translatable("wp.error.service_unavailable"));
            return 0;
        }

        if (!source.hasPermission(OP_LEVEL)) {
            source.sendFailure(Component.translatable("wp.command.error.no_permission"));
            return 0;
        }

        ServerPlayer target;
        try {
            target = EntityArgument.getPlayer(context, "target");
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.translatable("wp.command.error.player_not_found", "?"));
            return 0;
        }
        String reason = StringArgumentType.getString(context, "reason");

        String actorUuid = actorUuid(source);
        String actorName = source.getTextName();
        String targetName = target.getGameProfile().getName();

        CollaboratorService.Result result = mark
                ? collaboratorService.set(target, actorUuid, actorName, reason)
                : collaboratorService.unset(target, actorUuid, actorName, reason);

        if (result instanceof CollaboratorService.Result.Failure) {
            // Cause is already logged by the service.
            source.sendFailure(Component.translatable("wp.command.error.internal"));
            return 0;
        }

        if (mark) {
            source.sendSuccess(() -> Component.translatable("wp.command.collab.success",
                    targetName, reason), true);
        } else {
            source.sendSuccess(() -> Component.translatable("wp.command.uncollab.success",
                    targetName, reason), true);
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code /wp generalchat <message>} (Req. 15.1–15.4). Delegates to
     * {@link GeneralChatService#send(ServerPlayer, String)} which performs:
     * <ul>
     *   <li>Role gate (≥ COMMANDER) — Req. 15.1.</li>
     *   <li>30-second per-initiator cooldown via {@code cooldowns(uuid, GC)} —
     *       Req. 15.1, 15.2, 15.4.</li>
     *   <li>Broadcast to all online {@code COMMANDER}/{@code GENERAL}/{@code OP}
     *       across both factions — Req. 15.3.</li>
     *   <li>{@code GENERALCHAT_SEND} audit row in the same transaction as the
     *       cooldown upsert — Req. 20.3, 10.5.</li>
     * </ul>
     * <p>
     * The Brigadier {@code requires(...)} clause acts only as a visibility
     * gate; the executor here re-checks the COMMANDER+ role and the
     * {@code ACCEPTED} player state defensively before delegating, so a role
     * change between auto-complete and execution still gets rejected
     * (design §7).
     */
    private static int runGeneralChat(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!servicesReady(source)) return 0;
        if (generalChatService == null) {
            source.sendFailure(Component.translatable("wp.error.service_unavailable"));
            return 0;
        }

        if (!RoleResolver.atLeast(source, Role.COMMANDER)) {
            source.sendFailure(Component.translatable("wp.command.error.no_permission"));
            return 0;
        }

        ServerPlayer initiator = source.getPlayer();
        if (initiator == null) {
            source.sendFailure(Component.translatable("wp.error.player_only"));
            return 0;
        }

        // Initiator must be ACCEPTED (otherwise faction lifecycle invariants
        // in design §5.1 are violated — only ACCEPTED players hold a real role).
        PlayerState state = initiator.getData(WpAttachmentTypes.PLAYER_STATE.get());
        if (state != PlayerState.ACCEPTED) {
            source.sendFailure(Component.translatable("wp.command.generalchat.not_accepted"));
            return 0;
        }

        String message = StringArgumentType.getString(context, "message");
        GeneralChatService.SendResult result = generalChatService.send(initiator, message);

        return switch (result) {
            case GeneralChatService.SendResult.Success ignored -> Command.SINGLE_SUCCESS;
            case GeneralChatService.SendResult.InsufficientRole ignored -> {
                source.sendFailure(Component.translatable("wp.command.error.no_permission"));
                yield 0;
            }
            case GeneralChatService.SendResult.CooldownActive cd -> {
                long seconds = (cd.remainingMillis() + 999L) / 1000L;
                source.sendFailure(Component.translatable("wp.gc.cooldown", seconds));
                yield 0;
            }
            case GeneralChatService.SendResult.EmptyMessage ignored -> {
                source.sendFailure(Component.translatable("wp.command.error.internal"));
                yield 0;
            }
            case GeneralChatService.SendResult.NoRecipients ignored -> {
                source.sendFailure(Component.translatable("wp.gc.no_recipients"));
                yield 0;
            }
            case GeneralChatService.SendResult.PersistenceFailure failure -> {
                WarProject.LOGGER.error("[WP /generalchat] Persistence failure for {}: {}",
                        initiator.getGameProfile().getName(), failure.cause().getMessage(), failure.cause());
                source.sendFailure(Component.translatable("wp.command.error.internal"));
                yield 0;
            }
        };
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Returns true if all required services have been wired up. Otherwise sends
     * a localized failure message to the source and returns false.
     */
    private static boolean servicesReady(CommandSourceStack source) {
        if (database == null || playersDao == null || passportsDao == null || auditLogDao == null) {
            source.sendFailure(Component.translatable("wp.error.service_unavailable"));
            return false;
        }
        return true;
    }

    /**
     * Returns the actor UUID for audit, or {@code null} for non-player sources
     * (console, command block).
     */
    private static String actorUuid(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player != null ? player.getStringUUID() : null;
    }

    /**
     * Case-insensitive lookup of a {@link Role} by its serialized name. Returns
     * {@code null} for unknown input. Used by {@link #runUp} to resolve the
     * {@code <role>} string argument into the enum value.
     */
    private static Role parseRole(String input) {
        if (input == null) return null;
        String normalized = input.toLowerCase(Locale.ROOT);
        for (Role role : Role.values()) {
            if (role.getSerializedName().equals(normalized)) {
                return role;
            }
        }
        return null;
    }

    /**
     * Case-insensitive lookup of a {@link FactionId} by its serialized name.
     * Returns {@code null} for unknown input. Used by {@link #runSetCommander}.
     */
    private static FactionId parseFaction(String input) {
        if (input == null) return null;
        String normalized = input.toLowerCase(Locale.ROOT);
        for (FactionId faction : FactionId.values()) {
            if (faction.getSerializedName().equals(normalized)) {
                return faction;
            }
        }
        return null;
    }
}
