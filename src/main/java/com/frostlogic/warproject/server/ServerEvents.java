package com.frostlogic.warproject.server;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.WpConfig;
import com.frostlogic.warproject.network.payload.c2s.RegisterRequestPayload;
import com.frostlogic.warproject.persistence.Database;
import com.frostlogic.warproject.persistence.dao.AuditLogDao;
import com.frostlogic.warproject.persistence.dao.BansDao;
import com.frostlogic.warproject.persistence.dao.MutesDao;
import com.frostlogic.warproject.persistence.dao.PassportsDao;
import com.frostlogic.warproject.persistence.dao.PlayersDao;
import com.frostlogic.warproject.server.captivity.CaptivityService;
import com.frostlogic.warproject.server.captivity.CaptivityTimeoutService;
import com.frostlogic.warproject.server.collab.CollaboratorService;
import com.frostlogic.warproject.server.wguard.WGuardEventHandler;
import com.frostlogic.warproject.server.wguard.WGuardService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.List;
import java.util.Optional;

/**
 * Server-side event subscriber for WarProject (NeoForge game event bus).
 * <p>
 * Handles server lifecycle events, payload registration, command registration,
 * and player connection events for the new WarProject subsystems.
 * <p>
 * This class complements the existing {@link WarProjectServerEvents} which handles
 * legacy systems (data stores, commands, login flow). As subsystems are migrated
 * to the new architecture, their event handlers will move here.
 * <p>
 * Requirements: 2.1, 3.1, 4.1, 4.4, 19.1
 * Design: §3, §5.1, §6
 */
@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class ServerEvents {

    private static Database database;
    private static WGuardService wguardService;
    private static CaptivityService captivityService;

    /**
     * Returns the shared Database instance. May be null if the server has not started yet.
     */
    public static Database getDatabase() {
        return database;
    }

    /**
     * Returns the shared WGuardService instance. May be null if the server has not started yet.
     */
    public static WGuardService getWGuardService() {
        return wguardService;
    }

    /**
     * Returns the shared CaptivityService instance. May be null if the server has not started yet.
     */
    public static CaptivityService getCaptivityService() {
        return captivityService;
    }

    private ServerEvents() {
        // static event subscriber — no instantiation
    }

    /**
     * Initializes the database and runs migrations before the server fully starts.
     * <p>
     * Uses {@link ServerAboutToStartEvent} (which fires before {@link net.neoforged.neoforge.event.server.ServerStartedEvent})
     * so that any other handler subscribed to {@code ServerStartedEvent} (e.g. command registration
     * in {@code WpCommandRoot}) can rely on the database being initialized. NeoForge does not
     * guarantee handler ordering between unrelated subscribers of the same event, so DB init
     * must happen on a strictly earlier lifecycle event.
     */
    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        database = Database.fromConfig();
        database.initialize();
        com.frostlogic.warproject.server.map.MapBroadcastService.install();
        com.frostlogic.warproject.server.map.FactionMarkerService.install();

        // WGuard anti-cheat: instantiate the service and hand it to the static
        // event handler. Without this every check in WGuardEventHandler short-
        // circuits because `service` stays null and the anti-cheat silently no-ops.
        wguardService = new WGuardService(database, new AuditLogDao());
        WGuardEventHandler.init(wguardService);
        WarProject.LOGGER.info("[WarProject] WGuard anti-cheat initialised (enabled={}).",
                WpConfig.WGUARD_ENABLED.get());

        // Captivity: instantiate the shared service and publish it via the
        // static handle so CaptivityTimeoutService (a static event-bus
        // subscriber) can perform per-minute auto-release / escape sweeps.
        // Without this the timeout sweep no-ops silently and captives stay
        // captured forever.
        captivityService = new CaptivityService(
                database,
                new PassportsDao(),
                new PlayersDao(),
                new AuditLogDao()
        );
        CaptivityService.init(captivityService);
        WarProject.LOGGER.info("[WarProject] Captivity service initialised (timeout=30 min, escape interval=5 min).");

        // Boot-time configuration sanity check: faction spawns must be set before
        // the server is opened to the public. The [0,64,0] placeholder will drop
        // newcomers in the void with no way to recover.
        warnIfPlaceholderFactionSpawns();

        WarProject.LOGGER.debug("[WarProject] ServerEvents: database initialized, new subsystems ready.");
    }

    private static void warnIfPlaceholderFactionSpawns() {
        if (isPlaceholderSpawn(WpConfig.FACTIONS_ZARNAVIA_SPAWN.get())
                || isPlaceholderSpawn(WpConfig.FACTIONS_CHERNOGRYAD_SPAWN.get())
                || isPlaceholderSpawn(WpConfig.FACTIONS_CHOICE_HALL_SPAWN.get())) {
            WarProject.LOGGER.warn(
                    "[WarProject] One or more faction spawn coordinates are still at the [0,64,0] placeholder. "
                            + "Set factions.zarnaviaSpawn / factions.chernogryadSpawn / factions.choiceHallSpawn "
                            + "in server/config/warproject-server.toml before opening the server to players.");
        }
    }

    private static boolean isPlaceholderSpawn(List<? extends Integer> coords) {
        return coords != null
                && coords.size() == 3
                && coords.get(0) == 0
                && coords.get(1) == 64
                && coords.get(2) == 0;
    }

    /**
     * Cleans up the database reference on server stop.
     */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // Each cleanup step is wrapped individually so a failure in one
        // (e.g. NoClassDefFoundError when the jar was hot-swapped) does not
        // abort the rest of shutdown and leave subsequent restarts in a
        // half-initialized state.
        safeRun("CollaboratorService.uninstall", CollaboratorService::uninstall);
        safeRun("RankService.uninstall", com.frostlogic.warproject.server.rank.RankService::uninstall);
        safeRun("DiplomacyTickHandler.clear", com.frostlogic.warproject.server.diplomacy.DiplomacyTickHandler::clear);
        safeRun("MapBroadcastService.uninstall", com.frostlogic.warproject.server.map.MapBroadcastService::uninstall);
        safeRun("FactionMarkerService.uninstall", com.frostlogic.warproject.server.map.FactionMarkerService::uninstall);
        safeRun("ServiceRegistry.clear", com.frostlogic.warproject.network.ServiceRegistry::clear);
        safeRun("WGuardEventHandler.clear", () -> WGuardEventHandler.init(null));
        safeRun("CaptivityTimeoutService.clear", CaptivityTimeoutService::clear);
        safeRun("CaptivityService.clear", () -> CaptivityService.init(null));
        wguardService = null;
        captivityService = null;
        database = null;
    }

    /**
     * Runs a cleanup step and logs (without rethrowing) any Throwable. Used during
     * shutdown to guarantee best-effort cleanup of every subsystem regardless of
     * classloader / hot-swap issues that occasionally surface only on stop.
     */
    private static void safeRun(String name, Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            WarProject.LOGGER.warn("[WarProject] Shutdown step '{}' failed: {}", name, t.toString());
        }
    }

    /**
     * Rejects players who connect without the WarProject client mod installed.
     * <p>
     * Since payloads are registered with {@code .optional()}, the server does not
     * reject vanilla/unmodded clients at the network negotiation level. Instead,
     * we check here whether the client has the warproject payload channel available.
     * If not, the player is disconnected with a localized message.
     * <p>
     * Uses {@link EventPriority#HIGHEST} to run before any other login handlers
     * (e.g. the legacy onboarding flow in {@link WarProjectServerEvents}).
     * <p>
     * Requirements: 4.4
     * Design: §6 (last bullet)
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // Single-player / integrated server: the client and server are in the same
        // JVM, so the mod is present by definition. Skip the check entirely —
        // hasChannel() may also race with channel registration on integrated
        // servers, leading to false-positive kicks ("Неверные данные игрока").
        if (player.getServer() == null || !player.getServer().isDedicatedServer()) {
            return;
        }

        // Check if the client has the warproject payload channel registered.
        // When the client mod is installed, it will have negotiated the "warproject"
        // channels during the configuration phase. We check for any known C2S payload
        // type — if absent, the client does not have the mod.
        if (!player.connection.hasChannel(RegisterRequestPayload.TYPE)) {
            player.connection.disconnect(Component.translatable("wp.error.client_mod_required"));
            WarProject.LOGGER.info("[WarProject] Disconnected player {} — client mod not detected.",
                    player.getGameProfile().getName());
            return;
        }

        // ─── Ban enforcement ──────────────────────────────────
        // Check if the player has an active ban in the database. If so,
        // disconnect them immediately before any auth/onboarding flow runs.
        if (database != null) {
            try {
                String uuid = player.getStringUUID();
                Optional<BansDao.Ban> activeBan = database.inTx(conn ->
                        new BansDao().findActive(conn, uuid, System.currentTimeMillis()));
                if (activeBan.isPresent()) {
                    player.connection.disconnect(
                            Component.translatable("wp.command.ban.kick_message", activeBan.get().reason()));
                    WarProject.LOGGER.info("[WarProject] Disconnected banned player {} (reason: {})",
                            player.getGameProfile().getName(), activeBan.get().reason());
                    return;
                }
            } catch (Throwable t) {
                // Ban check failure should not lock out players — log and continue.
                WarProject.LOGGER.warn("[WarProject] Ban check failed for {}: {}",
                        player.getGameProfile().getName(), t.getMessage());
            }
        }
    }

    // ─── Mute enforcement ───────────────────────────────────────────
    // Blocks chat messages from players who have an active mute in the database.
    // Runs at HIGH priority so it fires after FreezeService (HIGHEST) but before
    // normal chat processing.
    // ────────────────────────────────────────────────────────────────────────────

    /**
     * Enforces mute on chat messages. If the player has an active mute in the
     * {@code mutes} table, their message is canceled and they receive a notification
     * with the remaining mute time.
     * <p>
     * Requirements: 10.3
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onServerChatMuteCheck(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        if (database == null) {
            return;
        }
        try {
            String uuid = player.getStringUUID();
            long now = System.currentTimeMillis();
            Optional<MutesDao.Mute> activeMute = database.inTx(conn ->
                    new MutesDao().findActive(conn, uuid, now));
            if (activeMute.isPresent()) {
                long remainingMs = activeMute.get().expiresAt() - now;
                long remainingMin = Math.max(1L, (remainingMs + 59_999L) / 60_000L);
                player.sendSystemMessage(
                        Component.translatable("wp.command.mute.active", remainingMin));
                event.setCanceled(true);
            }
        } catch (Throwable t) {
            // Mute check failure should not block chat — log and allow through.
            WarProject.LOGGER.warn("[WarProject] Mute check failed for {}: {}",
                    player.getGameProfile().getName(), t.getMessage());
        }
    }

    /**
     * Handles the authentication flow after the client mod check passes.
     * <p>
     * Checks whether the player has an existing account in the database:
     * <ul>
     *   <li>If no account exists → sets state to {@link com.frostlogic.warproject.attachment.PlayerState#NEW} and sends
     *       AuthScreenStatePayload with REGISTER to open the registration screen on the client.</li>
     *   <li>If an account exists → sets state to LOGIN_PENDING and sends
     *       AuthScreenStatePayload with LOGIN to open the login screen.</li>
     * </ul>
     * <p>
     * The {@link com.frostlogic.warproject.server.lifecycle.FreezeService} automatically
     * freezes the player because both {@code NEW} and {@code LOGIN_PENDING} are frozen states.
     * <p>
     * Uses {@link EventPriority#HIGH} to run after the HIGHEST-priority client mod check
     * but before normal-priority handlers.
     * <p>
     * Requirements: 2.1, 3.1
     * Design: §5.1
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlayerLoginAuthFlow(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (database == null) {
            // DB not ready — fall through to legacy flow
            return;
        }

        // ─── Pipeline guard ────────────────────────────────────────
        // If the player already has a legacy JSON profile and NO DB account
        // yet, they belong to the legacy pipeline. Sending AuthScreenStatePayload
        // here would race with the legacy WarLoginHandler.promptLogin sending
        // OpenLoginScreenPayload — the client would receive both and stack two
        // auth screens, breaking onboarding non-deterministically.
        // The new pipeline takes over only for genuinely fresh players (no JSON
        // profile, no DB account) or for players who already have a DB account.
        // ───────────────────────────────────────────────────────────────────────────
        String uuid = player.getStringUUID();
        // Account is "real" only if a row exists AND the stored password hash
        // is a valid BCrypt string. Healing inserts (FactionChoiceHandler /
        // AccountsDao.ensureExists) write a placeholder 'OP_NO_PASSWORD' which
        // would otherwise route the player to LOGIN and crash the auth
        // pipeline with BCrypt's "Invalid salt version" on every reconnect.
        boolean accountExists = database.inTx(conn ->
                new com.frostlogic.warproject.persistence.dao.AccountsDao()
                        .findByUuid(conn, uuid)
                        .map(a -> com.frostlogic.warproject.server.auth.PasswordHasher.isLikelyBcryptHash(a.passwordHash()))
                        .orElse(false)
        );

        if (!accountExists) {
            // No DB account — check if a legacy JSON profile exists.
            // If yes, defer to the legacy pipeline (which fires after this on
            // EventPriority.NORMAL via WarProjectServerEvents).
            try {
                java.util.Optional<com.frostlogic.warproject.server.WarPlayerProfile> legacy =
                        com.frostlogic.warproject.server.WarPlayerDataStore.get()
                                .findByUuid(player.getUUID());
                if (legacy.isPresent() && legacy.get().isRegistered()) {
                    WarProject.LOGGER.debug("[WarProject] Player {} has legacy JSON profile, deferring to legacy pipeline.",
                            player.getGameProfile().getName());
                    return;
                }
            } catch (Throwable t) {
                // If legacy lookup fails for any reason, prefer the new pipeline
                // rather than locking the player out entirely.
                WarProject.LOGGER.warn("[WarProject] Legacy profile lookup failed for {}: {}",
                        player.getGameProfile().getName(), t.getMessage());
            }
        }

        com.frostlogic.warproject.attachment.PlayerState newState;
        com.frostlogic.warproject.network.payload.AuthMode authMode;

        if (accountExists) {
            newState = com.frostlogic.warproject.attachment.PlayerState.LOGIN_PENDING;
            authMode = com.frostlogic.warproject.network.payload.AuthMode.LOGIN;
        } else {
            newState = com.frostlogic.warproject.attachment.PlayerState.NEW;
            authMode = com.frostlogic.warproject.network.payload.AuthMode.REGISTER;
        }

        player.setData(com.frostlogic.warproject.attachment.WpAttachmentTypes.PLAYER_STATE.get(), newState);

        // Send auth screen state to client
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new com.frostlogic.warproject.network.payload.s2c.AuthScreenStatePayload(authMode, null));

        WarProject.LOGGER.debug("[WarProject] Player {} auth flow: state={}, mode={}",
                player.getGameProfile().getName(), newState, authMode);
    }
}
