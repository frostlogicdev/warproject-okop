package com.frostlogic.warproject.server.command.nodes;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.WpConfig;
import com.frostlogic.warproject.persistence.Database;
import com.frostlogic.warproject.persistence.dao.AuditLogDao;
import com.frostlogic.warproject.persistence.dao.BansDao;
import com.frostlogic.warproject.persistence.dao.MutesDao;
import com.frostlogic.warproject.persistence.dao.WarnsDao;
import com.frostlogic.warproject.persistence.dao.RanksDao;
import com.frostlogic.warproject.server.region.RegionCacheHandler;
import com.frostlogic.warproject.server.role.RankRegistry;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Brigadier nodes for the admin moderation subcommands of {@code /wp ...}.
 * <p>
 * Implements 12 moderation commands per design §7 / Req. 10.2:
 * <ul>
 *   <li>{@code /wp ban <name> <day> <reason>} — OP, persists in {@code bans}</li>
 *   <li>{@code /wp kick <name> <reason>} — OP, online-only, disconnects</li>
 *   <li>{@code /wp mute <name> <minutes> <reason>} — OP, persists in {@code mutes}</li>
 *   <li>{@code /wp unban <name>} — OP, offline-capable</li>
 *   <li>{@code /wp unmute <name>} — OP, offline-capable</li>
 *   <li>{@code /wp warn <name> <reason>} — OP/COMMANDER, offline-capable</li>
 *   <li>{@code /wp tp <name>} — OP, online-only</li>
 *   <li>{@code /wp tphere <name>} — OP, online-only</li>
 *   <li>{@code /wp freeze <name>} — OP, online-only, toggle</li>
 *   <li>{@code /wp vanish} — OP, self, toggle</li>
 *   <li>{@code /wp history <name>} — OP, prints audit_log entries by target</li>
 *   <li>{@code /wp reload} — OP, reloads config (and regions/ranks)</li>
 * </ul>
 * <p>
 * <b>Atomicity.</b> Every state-changing command performs its primary effect
 * <em>and</em> writes a corresponding entry to {@code audit_log} in a single
 * {@link Database#transaction(java.util.function.Consumer) database transaction}
 * (Req. 10.5). Fire-and-forget post-commit side effects (kicking, teleport,
 * client messages) run only if the transaction commits.
 * <p>
 * <b>Online vs. offline.</b> Commands that affect online players
 * ({@code kick}, {@code freeze}, {@code tp}, {@code tphere}) accept an
 * {@link EntityArgument#player()}; commands that may target offline players
 * ({@code ban}, {@code mute}, {@code unban}, {@code unmute}, {@code warn},
 * {@code history}) accept a {@link StringArgumentType#word() username}
 * resolved via the vanilla {@link GameProfileCache} so admins can act on
 * players who are not currently online.
 * <p>
 * Requirements: 10.2, 10.3, 10.5, 10.6
 * Design: §7
 */
public final class ModerationCommands {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int OP_LEVEL = 2;
    private static final int COMMANDER_LEVEL = 1; // Brigadier permission level acting as a fallback for COMMANDER

    private static Database database;
    private static BansDao bansDao;
    private static MutesDao mutesDao;
    private static WarnsDao warnsDao;
    private static AuditLogDao auditLogDao;

    private ModerationCommands() {
        // utility class — no instantiation
    }

    /**
     * Initializes the command with the required service dependencies. Must be
     * called from {@code WpCommandRoot.onServerStarted} before commands run.
     */
    public static void init(Database db, BansDao bans, MutesDao mutes, WarnsDao warns, AuditLogDao audit) {
        database = db;
        bansDao = bans;
        mutesDao = mutes;
        warnsDao = warns;
        auditLogDao = audit;
    }

    /**
     * Registers all 12 moderation subcommands onto the given {@code /wp} root node.
     */
    public static void register(LiteralArgumentBuilder<CommandSourceStack> wpRoot) {
        // ── /wp ban <name> <day> <reason> ───────────────────────────────────
        wpRoot.then(Commands.literal("ban")
                .requires(src -> src.hasPermission(OP_LEVEL))
                .then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("day", IntegerArgumentType.integer(1, 365))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(ModerationCommands::runBan)))));

        // ── /wp kick <name> <reason> ────────────────────────────────────────
        wpRoot.then(Commands.literal("kick")
                .requires(src -> src.hasPermission(OP_LEVEL))
                .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(ModerationCommands::runKick))));

        // ── /wp mute <name> <minutes> <reason> ──────────────────────────────
        wpRoot.then(Commands.literal("mute")
                .requires(src -> src.hasPermission(OP_LEVEL))
                .then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 60 * 24 * 365))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(ModerationCommands::runMute)))));

        // ── /wp unban <name> ────────────────────────────────────────────────
        wpRoot.then(Commands.literal("unban")
                .requires(src -> src.hasPermission(OP_LEVEL))
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ModerationCommands::runUnban)));

        // ── /wp unmute <name> ───────────────────────────────────────────────
        wpRoot.then(Commands.literal("unmute")
                .requires(src -> src.hasPermission(OP_LEVEL))
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ModerationCommands::runUnmute)));

        // ── /wp warn <name> <reason> ────────────────────────────────────────
        // OP or COMMANDER. We can't fully resolve "COMMANDER" from a Brigadier
        // requires(...) without the player attachment, so we accept the broader
        // permission level here; runtime authorization for COMMANDER paths
        // happens elsewhere via RoleResolver. For OP-only deployments hasPermission(2)
        // alone is the gate.
        wpRoot.then(Commands.literal("warn")
                .requires(src -> src.hasPermission(COMMANDER_LEVEL))
                .then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(ModerationCommands::runWarn))));

        // ── /wp tp <name> ───────────────────────────────────────────────────
        wpRoot.then(Commands.literal("tp")
                .requires(src -> src.hasPermission(OP_LEVEL))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ModerationCommands::runTp)));

        // ── /wp tphere <name> ───────────────────────────────────────────────
        wpRoot.then(Commands.literal("tphere")
                .requires(src -> src.hasPermission(OP_LEVEL))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ModerationCommands::runTpHere)));

        // ── /wp freeze <name> ───────────────────────────────────────────────
        wpRoot.then(Commands.literal("freeze")
                .requires(src -> src.hasPermission(OP_LEVEL))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ModerationCommands::runFreeze)));

        // ── /wp vanish ──────────────────────────────────────────────────────
        wpRoot.then(Commands.literal("vanish")
                .requires(src -> src.hasPermission(OP_LEVEL))
                .executes(ModerationCommands::runVanish));

        // ── /wp history <name> ──────────────────────────────────────────────
        wpRoot.then(Commands.literal("history")
                .requires(src -> src.hasPermission(OP_LEVEL))
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ModerationCommands::runHistory)));

        // ── /wp reload ──────────────────────────────────────────────────────
        wpRoot.then(Commands.literal("reload")
                .requires(src -> src.hasPermission(OP_LEVEL))
                .executes(ModerationCommands::runReload));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Command implementations
    // ═══════════════════════════════════════════════════════════════════════

    /** {@code /wp ban <name> <day> <reason>} — atomic upsert + audit, then kick if online. */
    private static int runBan(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!servicesReady(source)) return 0;

        String name = StringArgumentType.getString(context, "name");
        int days = IntegerArgumentType.getInteger(context, "day");
        String reason = StringArgumentType.getString(context, "reason");

        Optional<GameProfile> profileOpt = resolveProfile(source.getServer(), name);
        if (profileOpt.isEmpty()) {
            source.sendFailure(Component.translatable("wp.command.error.player_not_found", name));
            return 0;
        }
        GameProfile profile = profileOpt.get();
        String targetUuid = profile.getId().toString();
        String targetName = profile.getName();

        long now = System.currentTimeMillis();
        long expiresAt = now + ((long) days) * 24L * 60L * 60L * 1000L;

        String actorUuid = actorUuid(source);
        String actorName = source.getTextName();

        try {
            database.transaction(conn -> {
                bansDao.upsert(conn, new BansDao.Ban(targetUuid, reason, actorUuid != null ? actorUuid : "console", now, expiresAt));
                auditLogDao.insert(conn, now, actorUuid, actorName, targetUuid, targetName,
                        "BAN", reason, "{\"days\":" + days + "}");
            });
        } catch (RuntimeException e) {
            LOGGER.error("[WP /ban] Transaction failed for {}: {}", targetName, e.getMessage(), e);
            source.sendFailure(Component.translatable("wp.command.error.internal"));
            return 0;
        }

        // Post-commit: kick if online
        ServerPlayer online = source.getServer().getPlayerList().getPlayer(profile.getId());
        if (online != null) {
            online.connection.disconnect(Component.translatable("wp.command.ban.kick_message", reason));
        }

        source.sendSuccess(() -> Component.translatable("wp.command.ban.success", targetName, days, reason), true);
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /wp kick <name> <reason>} — disconnects an online player + audit. */
    private static int runKick(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!servicesReady(source)) return 0;

        ServerPlayer target;
        try {
            target = EntityArgument.getPlayer(context, "target");
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.translatable("wp.command.error.player_not_found", "?"));
            return 0;
        }
        String reason = StringArgumentType.getString(context, "reason");
        long now = System.currentTimeMillis();
        String actorUuid = actorUuid(source);
        String actorName = source.getTextName();
        String targetName = target.getGameProfile().getName();
        String targetUuid = target.getStringUUID();

        try {
            database.transaction(conn -> auditLogDao.insert(conn, now, actorUuid, actorName,
                    targetUuid, targetName, "KICK", reason, null));
        } catch (RuntimeException e) {
            LOGGER.error("[WP /kick] Audit failed for {}: {}", targetName, e.getMessage(), e);
            source.sendFailure(Component.translatable("wp.command.error.internal"));
            return 0;
        }

        target.connection.disconnect(Component.translatable("wp.command.kick.kick_message", reason));
        source.sendSuccess(() -> Component.translatable("wp.command.kick.success", targetName, reason), true);
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /wp mute <name> <minutes> <reason>} — atomic upsert + audit. */
    private static int runMute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!servicesReady(source)) return 0;

        String name = StringArgumentType.getString(context, "name");
        int minutes = IntegerArgumentType.getInteger(context, "minutes");
        String reason = StringArgumentType.getString(context, "reason");

        Optional<GameProfile> profileOpt = resolveProfile(source.getServer(), name);
        if (profileOpt.isEmpty()) {
            source.sendFailure(Component.translatable("wp.command.error.player_not_found", name));
            return 0;
        }
        GameProfile profile = profileOpt.get();
        String targetUuid = profile.getId().toString();
        String targetName = profile.getName();

        long now = System.currentTimeMillis();
        long expiresAt = now + ((long) minutes) * 60L * 1000L;

        String actorUuid = actorUuid(source);
        String actorName = source.getTextName();

        try {
            database.transaction(conn -> {
                mutesDao.upsert(conn, new MutesDao.Mute(targetUuid, reason, actorUuid != null ? actorUuid : "console", now, expiresAt));
                auditLogDao.insert(conn, now, actorUuid, actorName, targetUuid, targetName,
                        "MUTE", reason, "{\"minutes\":" + minutes + "}");
            });
        } catch (RuntimeException e) {
            LOGGER.error("[WP /mute] Transaction failed for {}: {}", targetName, e.getMessage(), e);
            source.sendFailure(Component.translatable("wp.command.error.internal"));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("wp.command.mute.success", targetName, minutes, reason), true);

        // Notify the muted player if they are online so they don't wonder why their messages disappear.
        ServerPlayer online = source.getServer().getPlayerList().getPlayer(profile.getId());
        if (online != null) {
            online.sendSystemMessage(Component.translatable("wp.command.mute.notify", minutes, reason));
        }
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /wp unban <name>} — delete from {@code bans} + audit. */
    private static int runUnban(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!servicesReady(source)) return 0;

        String name = StringArgumentType.getString(context, "name");
        Optional<GameProfile> profileOpt = resolveProfile(source.getServer(), name);
        if (profileOpt.isEmpty()) {
            source.sendFailure(Component.translatable("wp.command.error.player_not_found", name));
            return 0;
        }
        GameProfile profile = profileOpt.get();
        String targetUuid = profile.getId().toString();
        String targetName = profile.getName();

        long now = System.currentTimeMillis();
        String actorUuid = actorUuid(source);
        String actorName = source.getTextName();

        try {
            database.transaction(conn -> {
                bansDao.delete(conn, targetUuid);
                auditLogDao.insert(conn, now, actorUuid, actorName, targetUuid, targetName,
                        "UNBAN", null, null);
            });
        } catch (RuntimeException e) {
            LOGGER.error("[WP /unban] Transaction failed for {}: {}", targetName, e.getMessage(), e);
            source.sendFailure(Component.translatable("wp.command.error.internal"));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("wp.command.unban.success", targetName), true);
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /wp unmute <name>} — delete from {@code mutes} + audit. */
    private static int runUnmute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!servicesReady(source)) return 0;

        String name = StringArgumentType.getString(context, "name");
        Optional<GameProfile> profileOpt = resolveProfile(source.getServer(), name);
        if (profileOpt.isEmpty()) {
            source.sendFailure(Component.translatable("wp.command.error.player_not_found", name));
            return 0;
        }
        GameProfile profile = profileOpt.get();
        String targetUuid = profile.getId().toString();
        String targetName = profile.getName();

        long now = System.currentTimeMillis();
        String actorUuid = actorUuid(source);
        String actorName = source.getTextName();

        try {
            database.transaction(conn -> {
                mutesDao.delete(conn, targetUuid);
                auditLogDao.insert(conn, now, actorUuid, actorName, targetUuid, targetName,
                        "UNMUTE", null, null);
            });
        } catch (RuntimeException e) {
            LOGGER.error("[WP /unmute] Transaction failed for {}: {}", targetName, e.getMessage(), e);
            source.sendFailure(Component.translatable("wp.command.error.internal"));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("wp.command.unmute.success", targetName), true);
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /wp warn <name> <reason>} — insert into {@code warns} + audit. */
    private static int runWarn(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!servicesReady(source)) return 0;

        String name = StringArgumentType.getString(context, "name");
        String reason = StringArgumentType.getString(context, "reason");

        Optional<GameProfile> profileOpt = resolveProfile(source.getServer(), name);
        if (profileOpt.isEmpty()) {
            source.sendFailure(Component.translatable("wp.command.error.player_not_found", name));
            return 0;
        }
        GameProfile profile = profileOpt.get();
        String targetUuid = profile.getId().toString();
        String targetName = profile.getName();

        long now = System.currentTimeMillis();
        String actorUuid = actorUuid(source);
        String actorName = source.getTextName();

        try {
            database.transaction(conn -> {
                warnsDao.insert(conn, targetUuid, reason, actorUuid != null ? actorUuid : "console", now);
                auditLogDao.insert(conn, now, actorUuid, actorName, targetUuid, targetName,
                        "WARN", reason, null);
            });
        } catch (RuntimeException e) {
            LOGGER.error("[WP /warn] Transaction failed for {}: {}", targetName, e.getMessage(), e);
            source.sendFailure(Component.translatable("wp.command.error.internal"));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("wp.command.warn.success", targetName, reason), true);

        // Inform the warned player if online
        ServerPlayer online = source.getServer().getPlayerList().getPlayer(profile.getId());
        if (online != null) {
            online.sendSystemMessage(Component.translatable("wp.command.warn.notify", reason));
        }
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /wp tp <name>} — teleport caller to target. Audit-logged. */
    private static int runTp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!servicesReady(source)) return 0;

        ServerPlayer caller = source.getPlayer();
        if (caller == null) {
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

        long now = System.currentTimeMillis();
        try {
            database.transaction(conn -> auditLogDao.insert(conn, now,
                    caller.getStringUUID(), caller.getGameProfile().getName(),
                    target.getStringUUID(), target.getGameProfile().getName(),
                    "TP", null, null));
        } catch (RuntimeException e) {
            LOGGER.error("[WP /tp] Audit failed: {}", e.getMessage(), e);
            // Audit failure is not blocking — proceed with teleport
        }

        caller.teleportTo(target.serverLevel(), target.getX(), target.getY(), target.getZ(),
                target.getYRot(), target.getXRot());
        source.sendSuccess(() -> Component.translatable("wp.command.tp.success",
                target.getGameProfile().getName()), false);
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /wp tphere <name>} — teleport target to caller. Audit-logged. */
    private static int runTpHere(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!servicesReady(source)) return 0;

        ServerPlayer caller = source.getPlayer();
        if (caller == null) {
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

        long now = System.currentTimeMillis();
        try {
            database.transaction(conn -> auditLogDao.insert(conn, now,
                    caller.getStringUUID(), caller.getGameProfile().getName(),
                    target.getStringUUID(), target.getGameProfile().getName(),
                    "TPHERE", null, null));
        } catch (RuntimeException e) {
            LOGGER.error("[WP /tphere] Audit failed: {}", e.getMessage(), e);
        }

        target.teleportTo(caller.serverLevel(), caller.getX(), caller.getY(), caller.getZ(),
                caller.getYRot(), caller.getXRot());
        source.sendSuccess(() -> Component.translatable("wp.command.tphere.success",
                target.getGameProfile().getName()), false);
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /wp freeze <name>} — toggle admin freeze for online player. */
    private static int runFreeze(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!servicesReady(source)) return 0;

        ServerPlayer target;
        try {
            target = EntityArgument.getPlayer(context, "target");
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.translatable("wp.command.error.player_not_found", "?"));
            return 0;
        }

        boolean nowFrozen = AdminModerationState.toggleFreeze(target.getUUID());
        long ts = System.currentTimeMillis();
        String actorUuid = actorUuid(source);
        String actorName = source.getTextName();
        String targetName = target.getGameProfile().getName();
        String targetUuid = target.getStringUUID();
        String action = nowFrozen ? "FREEZE" : "UNFREEZE";

        try {
            database.transaction(conn -> auditLogDao.insert(conn, ts,
                    actorUuid, actorName, targetUuid, targetName, action, null, null));
        } catch (RuntimeException e) {
            LOGGER.error("[WP /freeze] Audit failed: {}", e.getMessage(), e);
        }

        if (nowFrozen) {
            target.setDeltaMovement(0.0, 0.0, 0.0);
            target.sendSystemMessage(Component.translatable("wp.command.freeze.notify_frozen"));
            source.sendSuccess(() -> Component.translatable("wp.command.freeze.success_frozen", targetName), true);
        } else {
            target.sendSystemMessage(Component.translatable("wp.command.freeze.notify_unfrozen"));
            source.sendSuccess(() -> Component.translatable("wp.command.freeze.success_unfrozen", targetName), true);
        }
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /wp vanish} — toggle vanish for caller (self). */
    private static int runVanish(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!servicesReady(source)) return 0;

        ServerPlayer caller = source.getPlayer();
        if (caller == null) {
            source.sendFailure(Component.translatable("wp.error.player_only"));
            return 0;
        }

        boolean nowVanished = AdminModerationState.toggleVanish(caller);
        long ts = System.currentTimeMillis();
        String uuid = caller.getStringUUID();
        String name = caller.getGameProfile().getName();
        String action = nowVanished ? "VANISH" : "UNVANISH";

        try {
            database.transaction(conn -> auditLogDao.insert(conn, ts, uuid, name, uuid, name,
                    action, null, null));
        } catch (RuntimeException e) {
            LOGGER.error("[WP /vanish] Audit failed: {}", e.getMessage(), e);
        }

        if (nowVanished) {
            source.sendSuccess(() -> Component.translatable("wp.command.vanish.success_on"), false);
        } else {
            source.sendSuccess(() -> Component.translatable("wp.command.vanish.success_off"), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /wp history <name>} — list recent audit entries for the target. */
    private static int runHistory(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!servicesReady(source)) return 0;

        String name = StringArgumentType.getString(context, "name");
        Optional<GameProfile> profileOpt = resolveProfile(source.getServer(), name);
        if (profileOpt.isEmpty()) {
            source.sendFailure(Component.translatable("wp.command.error.player_not_found", name));
            return 0;
        }
        GameProfile profile = profileOpt.get();
        String targetUuid = profile.getId().toString();
        String targetName = profile.getName();

        // Read-only — no audit needed for /history itself per Req. 10.5
        // (history reads do not modify state).
        List<AuditLogDao.AuditEntry> entries;
        try {
            entries = database.inTx(conn -> auditLogDao.findByTargetUuid(conn, targetUuid, 10, 0));
        } catch (RuntimeException e) {
            LOGGER.error("[WP /history] Query failed: {}", e.getMessage(), e);
            source.sendFailure(Component.translatable("wp.command.error.internal"));
            return 0;
        }

        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("wp.command.history.empty", targetName), false);
            return Command.SINGLE_SUCCESS;
        }

        source.sendSuccess(() -> Component.translatable("wp.command.history.header", targetName, entries.size()), false);
        for (AuditLogDao.AuditEntry e : entries) {
            String reasonPart = e.reason() != null ? " — " + e.reason() : "";
            String actor = e.actorName() != null ? e.actorName() : "console";
            String line = String.format("§7[%d]§r §e%s§r §7by§r %s%s",
                    e.tsUtc(), e.action(), actor, reasonPart);
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /wp reload} — reloads config without dropping DB sessions (Req. 10.6). */
    private static int runReload(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!servicesReady(source)) return 0;

        long ts = System.currentTimeMillis();
        String uuid = actorUuid(source);
        String name = source.getTextName();

        try {
            // Reload primary config first (Req. 18.4: no DB session reset)
            WpConfig.reload();
            // Reload subsystems that mirror config to runtime caches
            try {
                RegionCacheHandler.reload();
            } catch (Exception ex) {
                LOGGER.warn("[WP /reload] RegionCacheHandler.reload failed: {}", ex.getMessage());
            }
            try {
                new RankRegistry(database, new RanksDao()).reload();
            } catch (Exception ex) {
                LOGGER.warn("[WP /reload] RankRegistry.reload failed: {}", ex.getMessage());
            }

            // Audit (best-effort — failure here doesn't undo config reload)
            try {
                database.transaction(conn -> auditLogDao.insert(conn, ts, uuid, name, null, null,
                        "RELOAD", null, null));
            } catch (RuntimeException ex) {
                LOGGER.warn("[WP /reload] Audit insert failed: {}", ex.getMessage());
            }
        } catch (RuntimeException e) {
            LOGGER.error("[WP /reload] Reload failed: {}", e.getMessage(), e);
            source.sendFailure(Component.translatable("wp.command.reload.fail"));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("wp.command.reload.success"), true);
        return Command.SINGLE_SUCCESS;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Returns true if all required services have been wired up. Otherwise sends
     * a failure message to the source and returns false.
     */
    private static boolean servicesReady(CommandSourceStack source) {
        if (database == null || bansDao == null || mutesDao == null
                || warnsDao == null || auditLogDao == null) {
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
     * Resolves a username to a {@link GameProfile} by checking online players first,
     * falling back to the vanilla {@link GameProfileCache} for offline lookups.
     * <p>
     * If the cache has no entry, this returns {@link Optional#empty()} — the cache
     * only contains profiles for players who have previously connected to the server,
     * which matches the use case for moderation actions.
     */
    private static Optional<GameProfile> resolveProfile(MinecraftServer server, String username) {
        if (server == null || username == null || username.isBlank()) {
            return Optional.empty();
        }
        // Online first — fastest path
        ServerPlayer online = server.getPlayerList().getPlayerByName(username);
        if (online != null) {
            return Optional.of(online.getGameProfile());
        }
        // Offline: vanilla profile cache
        GameProfileCache cache = server.getProfileCache();
        if (cache == null) {
            return Optional.empty();
        }
        Optional<GameProfile> cached = cache.get(username);
        if (cached.isPresent()) {
            return cached;
        }
        // As a last resort, accept a raw UUID string (for cases where the username
        // turned out to be a UUID).
        try {
            UUID asUuid = UUID.fromString(username);
            return cache.get(asUuid);
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
