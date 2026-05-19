package com.frostlogic.warproject.server.command.nodes;

import com.frostlogic.warproject.WpConfig;
import com.frostlogic.warproject.attachment.PlayerState;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import com.frostlogic.warproject.persistence.Database;
import com.frostlogic.warproject.persistence.dao.AuditLogDao;
import com.frostlogic.warproject.server.lifecycle.PlayerLifecycleService;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.List;

/**
 * Brigadier node for {@code /wp rpname <name> <surname>} (player) and
 * {@code /wp rpname <player> <name> <surname>} (admin override).
 * <p>
 * Player command:
 * <ul>
 *   <li>Only accessible when the player is in {@link PlayerState#RPNAME_REQUIRED} state</li>
 *   <li>Checks that {@code RP_NAME_LOCKED == false}</li>
 *   <li>On success: calls {@link PlayerLifecycleService#setRpName}, teleports to choice hall spawn,
 *       transitions to FACTIONLESS, writes audit RPNAME_SET</li>
 * </ul>
 * <p>
 * Admin override:
 * <ul>
 *   <li>Requires OP (permission level 2)</li>
 *   <li>Bypasses RP_NAME_LOCKED check</li>
 *   <li>Writes audit RPNAME_OVERRIDE</li>
 * </ul>
 * <p>
 * Requirements: 5.10, 10.2, 23 (note #17)
 * Design: §7, §16 (RP-name)
 */
public final class RpNameCommand {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static PlayerLifecycleService lifecycleService;
    private static Database database;
    private static AuditLogDao auditLogDao;

    private RpNameCommand() {
        // utility class — no instantiation
    }

    /**
     * Initializes the command with the required service dependencies.
     * Must be called before the command is executed (typically during server setup).
     *
     * @param lifecycle the PlayerLifecycleService instance
     * @param db        the Database instance for audit logging
     * @param audit     the AuditLogDao instance
     */
    public static void init(PlayerLifecycleService lifecycle, Database db, AuditLogDao audit) {
        lifecycleService = lifecycle;
        database = db;
        auditLogDao = audit;
    }

    /**
     * Registers the {@code rpname} subcommand onto the given {@code /wp} root node.
     * <p>
     * Registers two variants:
     * <ol>
     *   <li>{@code /wp rpname <name> <surname>} — player self-command (RPNAME_REQUIRED state)</li>
     *   <li>{@code /wp rpname <player> <name> <surname>} — admin override (OP)</li>
     * </ol>
     *
     * @param wpRoot the literal "wp" command builder to attach the rpname subcommand to
     */
    public static void register(LiteralArgumentBuilder<CommandSourceStack> wpRoot) {
        wpRoot.then(Commands.literal("rpname")
                // Player command: /wp rpname <Имя Фамилия>
                // Uses greedyString so Cyrillic and multi-word names work without
                // quoting. We split on the first space inside runPlayer().
                .then(Commands.argument("fullname", StringArgumentType.greedyString())
                        .executes(RpNameCommand::runPlayer))
                // Admin override: /wp rpname set <player> <Имя> <Фамилия>
                .then(Commands.literal("set")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("admin_fullname", StringArgumentType.greedyString())
                                        .executes(RpNameCommand::runAdminOverride)))));
    }

    // ─── Player Execution ─────────────────────────────────────────────────────────

    private static int runPlayer(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.translatable("wp.error.player_only"));
            return 0;
        }

        // Allow the command if EITHER:
        //   A) New pipeline: PlayerState == RPNAME_REQUIRED
        //   B) Legacy pipeline: captcha passed but RP name not yet set
        PlayerState state = player.getData(WpAttachmentTypes.PLAYER_STATE.get());
        boolean newPipelineOk = (state == PlayerState.RPNAME_REQUIRED);
        boolean legacyPipelineOk = false;
        try {
            com.frostlogic.warproject.server.WarPlayerProfile profile =
                    com.frostlogic.warproject.server.WarPlayerDataStore.get().getOrCreate(player);
            legacyPipelineOk = profile.isCaptchaPassed() && !profile.hasRpName();
        } catch (Throwable ignored) {}

        if (!newPipelineOk && !legacyPipelineOk) {
            source.sendFailure(Component.translatable("wp.rpname.not_in_rpname_state"));
            return 0;
        }

        // Check RP_NAME_LOCKED
        boolean locked = player.getData(WpAttachmentTypes.RP_NAME_LOCKED.get());
        if (locked) {
            source.sendFailure(Component.translatable("wp.rpname.already_locked"));
            return 0;
        }

        if (lifecycleService == null || database == null) {
            // Legacy fallback: new services not initialized, delegate to legacy handler.
            String fullName = StringArgumentType.getString(context, "fullname");
            return com.frostlogic.warproject.server.command.WarProjectCommands.setRpNameDirect(source, fullName);
        }

        // Even if new services are available, if the player is on the legacy
        // pipeline (no DB account), delegate to legacy to avoid writing orphan rows.
        boolean isNewPipeline;
        try {
            isNewPipeline = database.inTx(conn ->
                    new com.frostlogic.warproject.persistence.dao.AccountsDao()
                            .findByUuid(conn, player.getStringUUID()).isPresent());
        } catch (Throwable t) {
            isNewPipeline = false;
        }
        if (!isNewPipeline) {
            String fullName = StringArgumentType.getString(context, "fullname");
            return com.frostlogic.warproject.server.command.WarProjectCommands.setRpNameDirect(source, fullName);
        }

        String fullName = StringArgumentType.getString(context, "fullname").trim();
        int sep = fullName.indexOf(' ');
        if (sep <= 0 || sep >= fullName.length() - 1) {
            source.sendFailure(Component.translatable("wp.rpname.invalid"));
            return 0;
        }
        String name = fullName.substring(0, sep).trim();
        String surname = fullName.substring(sep + 1).trim();
        if (name.isEmpty() || surname.isEmpty() || name.length() > 32 || surname.length() > 32) {
            source.sendFailure(Component.translatable("wp.rpname.invalid"));
            return 0;
        }

        // Set RP name via lifecycle service (handles DB + attachment + state transition).
        // Wrap in try/catch so a state-machine race (e.g. another packet flipped
        // the player out of RPNAME_REQUIRED between our requires() check and now)
        // surfaces as a clean error instead of an unhandled command exception.
        try {
            lifecycleService.setRpName(player, name, surname);
        } catch (IllegalStateException e) {
            source.sendFailure(Component.translatable("wp.rpname.not_in_rpname_state"));
            return 0;
        }

        // Teleport to choice hall spawn
        teleportToChoiceHall(player);

        // Write audit RPNAME_SET
        writeAudit(player.getStringUUID(), player.getGameProfile().getName(),
                player.getStringUUID(), player.getGameProfile().getName(),
                "RPNAME_SET", name + " " + surname);

        source.sendSuccess(() -> Component.translatable("wp.rpname.success", name, surname), false);

        LOGGER.debug("[WarProject] Player {} set RP name: {} {}",
                player.getGameProfile().getName(), name, surname);

        return Command.SINGLE_SUCCESS;
    }

    // ─── Admin Override Execution ─────────────────────────────────────────────────

    private static int runAdminOverride(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        ServerPlayer target;
        try {
            target = EntityArgument.getPlayer(context, "target");
        } catch (Exception e) {
            source.sendFailure(Component.translatable("wp.error.player_not_found"));
            return 0;
        }

        if (lifecycleService == null || database == null) {
            source.sendFailure(Component.translatable("wp.error.service_unavailable"));
            return 0;
        }

        String adminFullName = StringArgumentType.getString(context, "admin_fullname").trim();
        int sep = adminFullName.indexOf(' ');
        if (sep <= 0 || sep >= adminFullName.length() - 1) {
            source.sendFailure(Component.translatable("wp.rpname.invalid"));
            return 0;
        }
        String name = adminFullName.substring(0, sep).trim();
        String surname = adminFullName.substring(sep + 1).trim();

        // Admin override: bypass RP_NAME_LOCKED, directly set RP name in DB and attachment
        String uuid = target.getStringUUID();
        database.transaction(conn -> {
            new com.frostlogic.warproject.persistence.dao.PlayersDao().setRpName(conn, uuid, name, surname);
        });

        // Update attachment
        target.setData(WpAttachmentTypes.RP_NAME.get(),
                java.util.Optional.of(new com.frostlogic.warproject.attachment.RpName(name, surname)));
        target.setData(WpAttachmentTypes.RP_NAME_LOCKED.get(), true);

        // Write audit RPNAME_OVERRIDE
        String actorUuid = source.getPlayer() != null ? source.getPlayer().getStringUUID() : null;
        String actorName = source.getTextName();
        writeAudit(actorUuid, actorName,
                target.getStringUUID(), target.getGameProfile().getName(),
                "RPNAME_OVERRIDE", name + " " + surname);

        source.sendSuccess(() -> Component.translatable("wp.rpname.admin_success",
                target.getGameProfile().getName(), name, surname), true);

        // Notify the target player
        target.sendSystemMessage(Component.translatable("wp.rpname.admin_changed", name, surname));

        LOGGER.debug("[WarProject] Admin {} overrode RP name for {}: {} {}",
                actorName, target.getGameProfile().getName(), name, surname);

        return Command.SINGLE_SUCCESS;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────────

    /**
     * Predicate for {@code requires(...)}: only players in RPNAME_REQUIRED state can see/use this command.
     */
    private static boolean isInRpNameRequiredState(CommandSourceStack source) {
        if (!source.isPlayer()) {
            return false;
        }
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return false;
        }
        PlayerState state = player.getData(WpAttachmentTypes.PLAYER_STATE.get());
        return state == PlayerState.RPNAME_REQUIRED;
    }

    /**
     * Teleports the player to the configured spawn / choice hall, with a
     * fallback chain so the player is never left floating in the sky-cage
     * with no destination.
     * <p>
     * Resolution order:
     * <ol>
     *   <li>{@code WarServerSettings.spawnPoint} — admin-set via {@code /wp setpoint spawn}.
     *       Authoritative because it preserves dimension + yaw/pitch.</li>
     *   <li>{@code WpConfig.FACTIONS_CHOICE_HALL_SPAWN} — config XYZ if non-default.</li>
     *   <li>{@link com.frostlogic.warproject.server.SpawnTeleporter#toWorldSpawn} — last resort.</li>
     * </ol>
     */
    private static void teleportToChoiceHall(ServerPlayer player) {
        // 1) Admin-set spawn point (preferred — has dimension + yaw/pitch).
        boolean ok = com.frostlogic.warproject.server.WarServerSettings.get()
                .getSpawnPoint()
                .map(point -> point.teleport(player))
                .orElse(false);
        if (ok) {
            return;
        }

        // 2) Config-based choice hall spawn — only if it looks intentionally set
        //    (not the default [0, 64, 0] sentinel).
        List<? extends Integer> coords = WpConfig.FACTIONS_CHOICE_HALL_SPAWN.get();
        boolean isDefault = coords.size() >= 3
                && coords.get(0) == 0 && coords.get(1) == 64 && coords.get(2) == 0;
        if (coords.size() >= 3 && !isDefault) {
            double x = coords.get(0) + 0.5;
            double y = coords.get(1);
            double z = coords.get(2) + 0.5;
            player.teleportTo(player.serverLevel(), x, y, z, player.getYRot(), player.getXRot());
            return;
        }

        // 3) Last-resort fallback — world spawn so the player has solid ground.
        LOGGER.warn("[WarProject] No spawnPoint configured and FACTIONS_CHOICE_HALL_SPAWN is default. Falling back to world spawn for {}. Admin: run /wp setpoint spawn at the choice hall.",
                player.getGameProfile().getName());
        com.frostlogic.warproject.server.SpawnTeleporter.toWorldSpawn(player);
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "[WP] Точка зала выбора фракций не настроена админом — телепортировал на мировой спавн.")
                .withStyle(net.minecraft.ChatFormatting.YELLOW));
    }

    /**
     * Writes an audit log entry. Uses a separate transaction to avoid coupling with the main operation.
     */
    private static void writeAudit(String actorUuid, String actorName,
                                   String targetUuid, String targetName,
                                   String action, String reason) {
        if (database == null || auditLogDao == null) {
            LOGGER.warn("[WarProject] Audit logging unavailable for action: {}", action);
            return;
        }
        try {
            database.transaction(conn -> {
                auditLogDao.insert(conn, System.currentTimeMillis(),
                        actorUuid, actorName, targetUuid, targetName,
                        action, reason, null);
            });
        } catch (Exception e) {
            LOGGER.error("[WarProject] Failed to write audit log for action: {}", action, e);
        }
    }
}
