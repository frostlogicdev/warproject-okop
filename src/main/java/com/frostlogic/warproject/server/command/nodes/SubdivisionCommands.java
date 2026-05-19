package com.frostlogic.warproject.server.command.nodes;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.attachment.Role;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import com.frostlogic.warproject.persistence.Database;
import com.frostlogic.warproject.persistence.dao.AuditLogDao;
import com.frostlogic.warproject.persistence.dao.PlayersDao;
import com.frostlogic.warproject.persistence.dao.SubdivisionsDao;
import com.frostlogic.warproject.server.role.RoleResolver;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Brigadier nodes for the subdivision-management subcommands of {@code /wp ...}.
 * <p>
 * Implements the six commands listed in design §7 / Req. 16 that operate on
 * subdivision lifecycle and membership:
 * <ul>
 *   <li>{@code /wp create subdivision <name>} (COMMANDER+ of own faction) —
 *       creates a new subdivision in the initiator's faction (Req. 16.1, 16.2).</li>
 *   <li>{@code /wp delete subdivision <name> <reason>} (COMMANDER+ of own faction) —
 *       deletes a subdivision in the initiator's faction; the reason is recorded
 *       in {@code audit_log} (Req. 16.3, 16.4).</li>
 *   <li>{@code /wp subdivision invite <player>} (COMMANDER+ of own faction) —
 *       adds the target player to the initiator's subdivision (Req. 16.5).</li>
 *   <li>{@code /wp subdivision kick <player> <reason>} (COMMANDER+ of own faction) —
 *       removes the target from their subdivision; the reason is recorded
 *       in {@code audit_log} (Req. 16.5).</li>
 *   <li>{@code /wp subdivision list} (any role) — prints all subdivisions in the
 *       caller's faction, or every faction for OP (Req. 16.5).</li>
 *   <li>{@code /wp subdivision info <name>} (any role) — prints details of a
 *       specific subdivision in the caller's faction (Req. 16.5).</li>
 * </ul>
 * <p>
 * <strong>Atomicity.</strong> Every state-changing command performs its primary
 * effect <em>and</em> writes a corresponding {@code audit_log} row inside a single
 * {@link Database#transaction(java.util.function.Consumer)} (Req. 18.5 / §7).
 * Post-commit side effects (attachment refresh, target-side notifications) run
 * only if the transaction commits.
 * <p>
 * <strong>Authorization.</strong> The Brigadier {@code requires(...)} clause acts
 * as a <em>visibility</em> gate so SOLDIERs do not see commands they cannot use.
 * The actual permission check is repeated inside {@code executes(...)} via
 * {@link RoleResolver#atLeast(CommandSourceStack, Role)} so that a role change
 * between auto-complete and execution still gets rejected (design §7).
 * <p>
 * <strong>Scope.</strong> This class is the Brigadier wrapper layer; it performs
 * the DAO write + audit but does <em>not</em> implement the full
 * {@code SubdivisionService} (invitation accept/decline, online notifications,
 * cascade-on-delete to {@code players.subdivision_id}). Those are the
 * responsibility of task 17.1.
 * <p>
 * Requirements: 16.1, 16.2, 16.3, 16.4, 16.5
 * Design: §7
 */
public final class SubdivisionCommands {

    /** Brigadier permission level corresponding to vanilla OP. */
    private static final int OP_LEVEL = 2;

    /** Maximum length of a subdivision name (matches {@code subdivisions.name VARCHAR(32)}). */
    private static final int MAX_NAME_LENGTH = 32;

    private static Database database;
    private static PlayersDao playersDao;
    private static SubdivisionsDao subdivisionsDao;
    private static AuditLogDao auditLogDao;

    private SubdivisionCommands() {
        // utility class — no instantiation
    }

    /**
     * Wires up the static service dependencies. Must be called from
     * {@code WpCommandRoot.onServerStarted} before commands run.
     */
    public static void init(Database db, PlayersDao players, SubdivisionsDao subdivisions, AuditLogDao audit) {
        database = db;
        playersDao = players;
        subdivisionsDao = subdivisions;
        auditLogDao = audit;
    }

    /**
     * Registers all six subdivision subcommands onto the given {@code /wp} root node.
     */
    public static void register(LiteralArgumentBuilder<CommandSourceStack> wpRoot) {
        // ── /wp create subdivision <name> ───────────────────────────────────
        wpRoot.then(Commands.literal("create")
                .requires(src -> RoleResolver.atLeast(src, Role.COMMANDER))
                .then(Commands.literal("subdivision")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(SubdivisionCommands::runCreate))));

        // ── /wp delete subdivision <name> <reason> ──────────────────────────
        // "name" is a single word here so the trailing greedy "reason" can be parsed.
        wpRoot.then(Commands.literal("delete")
                .requires(src -> RoleResolver.atLeast(src, Role.COMMANDER))
                .then(Commands.literal("subdivision")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(SubdivisionCommands::runDelete)))));

        // ── /wp subdivision <invite|kick|list|info> ─────────────────────────
        wpRoot.then(Commands.literal("subdivision")
                .then(Commands.literal("invite")
                        .requires(src -> RoleResolver.atLeast(src, Role.COMMANDER))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(SubdivisionCommands::runInvite)))
                .then(Commands.literal("kick")
                        .requires(src -> RoleResolver.atLeast(src, Role.COMMANDER))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(SubdivisionCommands::runKick))))
                .then(Commands.literal("list")
                        .executes(SubdivisionCommands::runList))
                .then(Commands.literal("info")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(SubdivisionCommands::runInfo))));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Command implementations
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * {@code /wp create subdivision <name>} (Req. 16.1, 16.2).
     * <p>
     * Creates a new subdivision attached to the initiator's faction. Rejects when:
     * <ul>
     *   <li>initiator is not a player (console);</li>
     *   <li>initiator has no faction (factionless / candidate);</li>
     *   <li>name is empty / too long after trimming;</li>
     *   <li>a subdivision with the same name already exists in the same faction.</li>
     * </ul>
     */
    private static int runCreate(CommandContext<CommandSourceStack> context) {
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

        Optional<FactionId> initFaction = initiator.getData(WpAttachmentTypes.FACTION.get());
        if (initFaction.isEmpty()) {
            source.sendFailure(Component.translatable("wp.command.subdivision.no_faction"));
            return 0;
        }

        String rawName = StringArgumentType.getString(context, "name");
        String name = normalizeName(rawName);
        if (!isValidName(name)) {
            source.sendFailure(Component.translatable("wp.command.subdivision.invalid_name"));
            return 0;
        }

        // Persist the faction in the same case used elsewhere in the codebase
        // (FactionChoiceHandler stores uppercase serialized name; SubdivisionsDao
        // queries with that same value). We follow the same convention here.
        String factionName = initFaction.get().getSerializedName().toUpperCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        String actorUuid = initiator.getStringUUID();
        String actorName = initiator.getGameProfile().getName();

        try {
            database.transaction(conn -> {
                if (subdivisionsDao.findByFactionAndName(conn, factionName, name).isPresent()) {
                    // Surface "already exists" through the rollback path so the
                    // transaction never commits a half-written row.
                    throw new SubdivisionAlreadyExistsException();
                }
                int id = subdivisionsDao.insert(conn, factionName, name, actorUuid, now);
                auditLogDao.insert(conn, now, actorUuid, actorName,
                        null, null, "SUBDIV_CREATE", null,
                        "{\"faction\":\"" + factionName + "\","
                                + "\"name\":\"" + escapeJson(name) + "\","
                                + "\"id\":" + id + "}");
            });
        } catch (SubdivisionAlreadyExistsException e) {
            source.sendFailure(Component.translatable("wp.command.subdivision.create.already_exists", name));
            return 0;
        } catch (RuntimeException e) {
            WarProject.LOGGER.error("[WP /create subdivision] Transaction failed for {}: {}",
                    actorName, e.getMessage(), e);
            source.sendFailure(Component.translatable("wp.command.error.internal"));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("wp.command.subdivision.create.success", name), true);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code /wp delete subdivision <name> <reason>} (Req. 16.3, 16.4).
     * <p>
     * Deletes a subdivision belonging to the initiator's faction. The
     * {@code subdivision_members} rows are removed by the {@code ON DELETE CASCADE}
     * FK; the {@code players.subdivision_id} column for each affected player is
     * cleared application-side via {@link PlayersDao#clearSubdivisionId}. The
     * {@code reason} is preserved in {@code audit_log.reason}.
     */
    private static int runDelete(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!servicesReady(source)) return 0;

        if (!RoleResolver.atLeast(source, Role.COMMANDER)) {
            source.sendFailure(Component.translatable("wp.command.error.no_permission"));
            return 0;
        }

        ServerPlayer initiator = source.getPlayer();
        if (initiator == null) {
            source.sendFailure(Component.translatable("wp.error.player_only"));
            return 0;
        }

        Optional<FactionId> initFaction = initiator.getData(WpAttachmentTypes.FACTION.get());
        if (initFaction.isEmpty()) {
            source.sendFailure(Component.translatable("wp.command.subdivision.no_faction"));
            return 0;
        }

        String name = normalizeName(StringArgumentType.getString(context, "name"));
        String reason = StringArgumentType.getString(context, "reason");
        String factionName = initFaction.get().getSerializedName().toUpperCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        String actorUuid = initiator.getStringUUID();
        String actorName = initiator.getGameProfile().getName();
        Role initiatorRole = RoleResolver.resolve(source);
        boolean isOp = initiatorRole == Role.OP;
        java.util.List<String> affectedMemberUuids = new java.util.ArrayList<>();

        try {
            database.transaction(conn -> {
                // Resolve the subdivision by name within the initiator's faction.
                Optional<SubdivisionsDao.Subdivision> primary =
                        subdivisionsDao.findByFactionAndName(conn, factionName, name);
                Optional<SubdivisionsDao.Subdivision> resolved = primary;
                if (resolved.isEmpty() && isOp) {
                    // OP fallback: walk the other faction(s) to allow cross-faction
                    // admin deletes. Req. 16.4's faction-mismatch rejection only
                    // applies to non-OP initiators.
                    for (FactionId f : FactionId.values()) {
                        String fName = f.getSerializedName().toUpperCase(Locale.ROOT);
                        if (fName.equals(factionName)) continue;
                        Optional<SubdivisionsDao.Subdivision> alt =
                                subdivisionsDao.findByFactionAndName(conn, fName, name);
                        if (alt.isPresent()) {
                            resolved = alt;
                            break;
                        }
                    }
                }
                if (resolved.isEmpty()) {
                    throw new SubdivisionNotFoundException();
                }
                SubdivisionsDao.Subdivision sub = resolved.get();

                // Req. 16.4: faction-mismatch rejection (non-OP only).
                if (!isOp && !sub.faction().equalsIgnoreCase(factionName)) {
                    throw new SubdivisionFactionMismatchException();
                }

                // Capture member UUIDs before deletion for Military ID card updates
                java.util.List<SubdivisionsDao.SubdivisionMember> members =
                        subdivisionsDao.findMembers(conn, sub.id());
                for (SubdivisionsDao.SubdivisionMember m : members) {
                    affectedMemberUuids.add(m.playerUuid());
                }

                // Clear FK references in players, then drop the subdivision row.
                // ON DELETE CASCADE on subdivision_members handles the join table.
                playersDao.clearSubdivisionId(conn, sub.id());
                subdivisionsDao.delete(conn, sub.id());

                auditLogDao.insert(conn, now, actorUuid, actorName,
                        null, null, "SUBDIV_DELETE", reason,
                        "{\"faction\":\"" + sub.faction() + "\","
                                + "\"name\":\"" + escapeJson(sub.name()) + "\","
                                + "\"id\":" + sub.id() + "}");
            });
        } catch (SubdivisionNotFoundException e) {
            source.sendFailure(Component.translatable("wp.command.subdivision.not_found", name));
            return 0;
        } catch (SubdivisionFactionMismatchException e) {
            source.sendFailure(Component.translatable("wp.command.subdivision.faction_mismatch"));
            return 0;
        } catch (RuntimeException e) {
            WarProject.LOGGER.error("[WP /delete subdivision] Transaction failed for {}: {}",
                    actorName, e.getMessage(), e);
            source.sendFailure(Component.translatable("wp.command.error.internal"));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("wp.command.subdivision.delete.success", name, reason), true);

        // Clear Military ID card subdivision field for all online affected members (Req. 5.3)
        if (!affectedMemberUuids.isEmpty() && source.getServer() != null) {
            com.frostlogic.warproject.server.militaryid.MilitaryIdService midService =
                    com.frostlogic.warproject.network.ServiceRegistry.militaryId();
            if (midService != null) {
                for (String memberUuid : affectedMemberUuids) {
                    ServerPlayer onlineMember = source.getServer().getPlayerList()
                            .getPlayer(java.util.UUID.fromString(memberUuid));
                    if (onlineMember != null) {
                        try {
                            midService.updateSubdivision(onlineMember, "");
                        } catch (Exception e) {
                            WarProject.LOGGER.error("[WP /delete subdivision] Failed to clear Military ID for {}: {}",
                                    onlineMember.getGameProfile().getName(), e.getMessage(), e);
                        }
                    }
                }
            }
        }

        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code /wp subdivision invite <player>} (Req. 16.5).
     * <p>
     * Adds the target to the initiator's subdivision. The initiator must already
     * belong to a subdivision (since this command does not take a subdivision
     * name — it operates on the initiator's own subdivision). Same-faction is
     * required because subdivisions are scoped to a faction.
     */
    private static int runInvite(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!servicesReady(source)) return 0;

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
            target = EntityArgument.getPlayer(context, "player");
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.translatable("wp.command.error.player_not_found", "?"));
            return 0;
        }

        Optional<FactionId> initFaction = initiator.getData(WpAttachmentTypes.FACTION.get());
        Optional<FactionId> targetFaction = target.getData(WpAttachmentTypes.FACTION.get());
        if (initFaction.isEmpty()) {
            source.sendFailure(Component.translatable("wp.command.subdivision.no_faction"));
            return 0;
        }
        if (targetFaction.isEmpty() || initFaction.get() != targetFaction.get()) {
            source.sendFailure(Component.translatable("wp.command.subdivision.faction_mismatch"));
            return 0;
        }

        long now = System.currentTimeMillis();
        String actorUuid = initiator.getStringUUID();
        String actorName = initiator.getGameProfile().getName();
        String targetUuid = target.getStringUUID();
        String targetName = target.getGameProfile().getName();
        String factionName = initFaction.get().getSerializedName().toUpperCase(Locale.ROOT);
        String[] subdivisionName = new String[1]; // captured from transaction for Military ID update

        try {
            database.transaction(conn -> {
                // Look up the initiator's subdivision via the players row.
                Optional<PlayersDao.Player> initRow = playersDao.findByUuid(conn, actorUuid);
                if (initRow.isEmpty() || initRow.get().subdivisionId() == null) {
                    throw new InitiatorNoSubdivisionException();
                }
                int subId = initRow.get().subdivisionId();

                Optional<SubdivisionsDao.Subdivision> sub = subdivisionsDao.findById(conn, subId);
                if (sub.isEmpty()) {
                    throw new SubdivisionNotFoundException();
                }
                if (!sub.get().faction().equalsIgnoreCase(factionName)) {
                    throw new SubdivisionFactionMismatchException();
                }

                // If the target already belongs to a subdivision, refuse — full
                // reassignment flow lives in SubdivisionService (task 17.1).
                Optional<PlayersDao.Player> targetRow = playersDao.findByUuid(conn, targetUuid);
                if (targetRow.isPresent() && targetRow.get().subdivisionId() != null) {
                    throw new TargetAlreadyMemberException();
                }

                subdivisionsDao.addMember(conn, subId, targetUuid, now);
                playersDao.setSubdivisionId(conn, targetUuid, subId);
                auditLogDao.insert(conn, now, actorUuid, actorName,
                        targetUuid, targetName, "SUBDIV_INVITE", null,
                        "{\"subdivision_id\":" + subId + ","
                                + "\"name\":\"" + escapeJson(sub.get().name()) + "\"}");
                subdivisionName[0] = sub.get().name();
            });
        } catch (InitiatorNoSubdivisionException e) {
            source.sendFailure(Component.translatable("wp.command.subdivision.invite.no_subdivision"));
            return 0;
        } catch (SubdivisionNotFoundException e) {
            source.sendFailure(Component.translatable("wp.command.subdivision.not_found", "?"));
            return 0;
        } catch (SubdivisionFactionMismatchException e) {
            source.sendFailure(Component.translatable("wp.command.subdivision.faction_mismatch"));
            return 0;
        } catch (TargetAlreadyMemberException e) {
            source.sendFailure(Component.translatable("wp.command.subdivision.invite.already_member", targetName));
            return 0;
        } catch (RuntimeException e) {
            WarProject.LOGGER.error("[WP /subdivision invite] Transaction failed for {} → {}: {}",
                    actorName, targetName, e.getMessage(), e);
            source.sendFailure(Component.translatable("wp.command.error.internal"));
            return 0;
        }

        // Update Military ID card with new subdivision name (Req. 5.2)
        updateMilitaryIdSubdivision(target, subdivisionName[0] != null ? subdivisionName[0] : "");

        source.sendSuccess(() -> Component.translatable("wp.command.subdivision.invite.success", targetName), true);
        target.sendSystemMessage(Component.translatable("wp.command.subdivision.invite.notify"));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code /wp subdivision kick <player> <reason>} (Req. 16.5).
     * <p>
     * Removes the target from their subdivision. The target must currently belong
     * to a subdivision in the initiator's faction.
     */
    private static int runKick(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!servicesReady(source)) return 0;

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
            target = EntityArgument.getPlayer(context, "player");
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.translatable("wp.command.error.player_not_found", "?"));
            return 0;
        }

        String reason = StringArgumentType.getString(context, "reason");
        Optional<FactionId> initFaction = initiator.getData(WpAttachmentTypes.FACTION.get());
        if (initFaction.isEmpty()) {
            source.sendFailure(Component.translatable("wp.command.subdivision.no_faction"));
            return 0;
        }
        Role initiatorRole = RoleResolver.resolve(source);
        boolean isOp = initiatorRole == Role.OP;

        long now = System.currentTimeMillis();
        String actorUuid = initiator.getStringUUID();
        String actorName = initiator.getGameProfile().getName();
        String targetUuid = target.getStringUUID();
        String targetName = target.getGameProfile().getName();
        String factionName = initFaction.get().getSerializedName().toUpperCase(Locale.ROOT);

        try {
            database.transaction(conn -> {
                Optional<PlayersDao.Player> targetRow = playersDao.findByUuid(conn, targetUuid);
                if (targetRow.isEmpty() || targetRow.get().subdivisionId() == null) {
                    throw new TargetNoSubdivisionException();
                }
                int subId = targetRow.get().subdivisionId();

                Optional<SubdivisionsDao.Subdivision> sub = subdivisionsDao.findById(conn, subId);
                if (sub.isEmpty()) {
                    // Inconsistent state — the players row points at a non-existent
                    // subdivision. Surface as not-found; full cleanup belongs in
                    // SubdivisionService (task 17.1) or a separate maintenance task.
                    throw new SubdivisionNotFoundException();
                }

                // Req. 16.5: faction-of-subdivision must equal initiator's faction
                // (skipped for OP — admin tool).
                if (!isOp && !sub.get().faction().equalsIgnoreCase(factionName)) {
                    throw new SubdivisionFactionMismatchException();
                }

                subdivisionsDao.removeMember(conn, subId, targetUuid);
                playersDao.setSubdivisionId(conn, targetUuid, null);
                auditLogDao.insert(conn, now, actorUuid, actorName,
                        targetUuid, targetName, "SUBDIV_KICK", reason,
                        "{\"subdivision_id\":" + subId + ","
                                + "\"name\":\"" + escapeJson(sub.get().name()) + "\"}");
            });
        } catch (TargetNoSubdivisionException e) {
            source.sendFailure(Component.translatable("wp.command.subdivision.kick.not_member", targetName));
            return 0;
        } catch (SubdivisionNotFoundException e) {
            source.sendFailure(Component.translatable("wp.command.subdivision.not_found", "?"));
            return 0;
        } catch (SubdivisionFactionMismatchException e) {
            source.sendFailure(Component.translatable("wp.command.subdivision.faction_mismatch"));
            return 0;
        } catch (RuntimeException e) {
            WarProject.LOGGER.error("[WP /subdivision kick] Transaction failed for {} → {}: {}",
                    actorName, targetName, e.getMessage(), e);
            source.sendFailure(Component.translatable("wp.command.error.internal"));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("wp.command.subdivision.kick.success", targetName, reason), true);
        target.sendSystemMessage(Component.translatable("wp.command.subdivision.kick.notify", reason));

        // Clear Military ID card subdivision field (Req. 5.3)
        updateMilitaryIdSubdivision(target, "");

        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code /wp subdivision list} (Req. 16.5). Read-only; no audit row.
     * <p>
     * Players see subdivisions of their own faction. OP / console see all.
     */
    private static int runList(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!servicesReady(source)) return 0;

        Role role = RoleResolver.resolve(source);
        ServerPlayer player = source.getPlayer();
        Optional<FactionId> playerFaction =
                player != null ? player.getData(WpAttachmentTypes.FACTION.get()) : Optional.empty();

        boolean adminScope = role == Role.OP || player == null;
        if (!adminScope && playerFaction.isEmpty()) {
            source.sendFailure(Component.translatable("wp.command.subdivision.no_faction"));
            return 0;
        }

        List<SubdivisionsDao.Subdivision> rows;
        try {
            rows = database.inTx(conn -> {
                if (adminScope) {
                    // Concatenate per-faction listings — deterministic ordering.
                    java.util.ArrayList<SubdivisionsDao.Subdivision> all = new java.util.ArrayList<>();
                    for (FactionId f : FactionId.values()) {
                        all.addAll(subdivisionsDao.findByFaction(conn,
                                f.getSerializedName().toUpperCase(Locale.ROOT)));
                    }
                    return all;
                }
                return subdivisionsDao.findByFaction(conn,
                        playerFaction.get().getSerializedName().toUpperCase(Locale.ROOT));
            });
        } catch (RuntimeException e) {
            WarProject.LOGGER.error("[WP /subdivision list] Query failed: {}", e.getMessage(), e);
            source.sendFailure(Component.translatable("wp.command.error.internal"));
            return 0;
        }

        if (rows.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("wp.command.subdivision.list.empty"), false);
            return Command.SINGLE_SUCCESS;
        }

        source.sendSuccess(() -> Component.translatable("wp.command.subdivision.list.header", rows.size())
                .withStyle(ChatFormatting.GOLD), false);
        for (SubdivisionsDao.Subdivision sub : rows) {
            source.sendSuccess(() -> Component.translatable("wp.command.subdivision.list.entry",
                    sub.name(), sub.faction(), sub.id()), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code /wp subdivision info <name>} (Req. 16.5). Read-only; no audit row.
     * <p>
     * Players see info for subdivisions in their own faction. OP / console may
     * inspect either faction by walking both lookups.
     */
    private static int runInfo(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!servicesReady(source)) return 0;

        String name = normalizeName(StringArgumentType.getString(context, "name"));
        Role role = RoleResolver.resolve(source);
        ServerPlayer player = source.getPlayer();
        Optional<FactionId> playerFaction =
                player != null ? player.getData(WpAttachmentTypes.FACTION.get()) : Optional.empty();

        boolean adminScope = role == Role.OP || player == null;
        if (!adminScope && playerFaction.isEmpty()) {
            source.sendFailure(Component.translatable("wp.command.subdivision.no_faction"));
            return 0;
        }

        SubdivisionsDao.Subdivision found;
        List<SubdivisionsDao.SubdivisionMember> members;
        try {
            InfoQueryResult result = database.inTx(conn -> {
                Optional<SubdivisionsDao.Subdivision> sub = Optional.empty();
                if (adminScope) {
                    for (FactionId f : FactionId.values()) {
                        sub = subdivisionsDao.findByFactionAndName(conn,
                                f.getSerializedName().toUpperCase(Locale.ROOT), name);
                        if (sub.isPresent()) break;
                    }
                } else {
                    sub = subdivisionsDao.findByFactionAndName(conn,
                            playerFaction.get().getSerializedName().toUpperCase(Locale.ROOT), name);
                }
                if (sub.isEmpty()) {
                    return null;
                }
                List<SubdivisionsDao.SubdivisionMember> ms = subdivisionsDao.findMembers(conn, sub.get().id());
                return new InfoQueryResult(sub.get(), ms);
            });
            if (result == null) {
                source.sendFailure(Component.translatable("wp.command.subdivision.not_found", name));
                return 0;
            }
            found = result.sub;
            members = result.members;
        } catch (RuntimeException e) {
            WarProject.LOGGER.error("[WP /subdivision info] Query failed: {}", e.getMessage(), e);
            source.sendFailure(Component.translatable("wp.command.error.internal"));
            return 0;
        }

        final SubdivisionsDao.Subdivision sub = found;
        final int memberCount = members.size();
        source.sendSuccess(() -> Component.translatable("wp.command.subdivision.info.header", sub.name())
                .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.translatable("wp.command.subdivision.info.faction", sub.faction()), false);
        source.sendSuccess(() -> Component.translatable("wp.command.subdivision.info.id", sub.id()), false);
        source.sendSuccess(() -> Component.translatable("wp.command.subdivision.info.created_by", sub.createdBy()), false);
        source.sendSuccess(() -> Component.translatable("wp.command.subdivision.info.members", memberCount), false);
        return Command.SINGLE_SUCCESS;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Returns true if all required services have been wired up. Otherwise sends
     * a localized failure message to the source and returns false.
     */
    private static boolean servicesReady(CommandSourceStack source) {
        if (database == null || playersDao == null || subdivisionsDao == null || auditLogDao == null) {
            source.sendFailure(Component.translatable("wp.error.service_unavailable"));
            return false;
        }
        return true;
    }

    /**
     * Updates the Military ID card subdivision field for the target player.
     * <p>
     * Failures are logged but do not affect the subdivision operation result.
     * <p>
     * Requirements: 5.2, 5.3
     */
    private static void updateMilitaryIdSubdivision(ServerPlayer target, String subdivisionName) {
        com.frostlogic.warproject.server.militaryid.MilitaryIdService midService =
                com.frostlogic.warproject.network.ServiceRegistry.militaryId();
        if (midService == null) {
            WarProject.LOGGER.debug("[WP SubdivisionCommands] MilitaryIdService not available — skipping card update for {}",
                    target.getGameProfile().getName());
            return;
        }

        try {
            midService.updateSubdivision(target, subdivisionName);
        } catch (Exception e) {
            WarProject.LOGGER.error("[WP SubdivisionCommands] Failed to update Military ID subdivision for {}: {}",
                    target.getGameProfile().getName(), e.getMessage(), e);
        }
    }

    /** Normalizes whitespace in a subdivision name (collapse + trim). */
    private static String normalizeName(String raw) {
        if (raw == null) return "";
        return raw.trim().replaceAll("\\s+", " ");
    }

    /** Validates a subdivision name (non-empty, length-bounded). */
    private static boolean isValidName(String name) {
        return !name.isEmpty() && name.length() <= MAX_NAME_LENGTH;
    }

    /** Minimal JSON-string escape for {@code audit_log.extra_json}. */
    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 2);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Internal control-flow exceptions (used to bail out of a transaction
    // with a specific user-facing error). These are intentionally private and
    // never escape the class.
    // ═══════════════════════════════════════════════════════════════════════

    private static final class SubdivisionAlreadyExistsException extends RuntimeException {
        SubdivisionAlreadyExistsException() { super(null, null, false, false); }
    }

    private static final class SubdivisionNotFoundException extends RuntimeException {
        SubdivisionNotFoundException() { super(null, null, false, false); }
    }

    private static final class SubdivisionFactionMismatchException extends RuntimeException {
        SubdivisionFactionMismatchException() { super(null, null, false, false); }
    }

    private static final class InitiatorNoSubdivisionException extends RuntimeException {
        InitiatorNoSubdivisionException() { super(null, null, false, false); }
    }

    private static final class TargetAlreadyMemberException extends RuntimeException {
        TargetAlreadyMemberException() { super(null, null, false, false); }
    }

    private static final class TargetNoSubdivisionException extends RuntimeException {
        TargetNoSubdivisionException() { super(null, null, false, false); }
    }

    private record InfoQueryResult(SubdivisionsDao.Subdivision sub,
                                    List<SubdivisionsDao.SubdivisionMember> members) {}
}
