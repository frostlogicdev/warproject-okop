package com.frostlogic.warproject.server.captivity;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.attachment.PlayerState;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import com.frostlogic.warproject.persistence.dao.PassportsDao;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Periodic sweep service that closes the captivity loop.
 * <p>
 * Two responsibilities per minute-long sweep:
 * <ul>
 *   <li><b>Auto-release</b> — any passport whose {@code captured_at} is older than
 *       {@link #CAPTIVITY_MAX_DURATION_MS} (30 min) is ransomed back to its owner
 *       via {@link CaptivityService#ransom(String, String, String)}.</li>
 *   <li><b>Escape attempts</b> — every captive who is currently online gets one
 *       roll every {@link #ESCAPE_ATTEMPT_INTERVAL_MS} (5 min). The roll succeeds
 *       at {@link #ESCAPE_SUCCESS_CHANCE} probability, but only if the captor is
 *       offline, in a different dimension, or more than
 *       {@link #ESCAPE_DISTANCE_THRESHOLD} blocks away.</li>
 * </ul>
 * <p>
 * The service is wired by {@code ServerEvents.onServerAboutToStart} via
 * {@link CaptivityService#init(CaptivityService)} — if that handle is null, every
 * sweep no-ops and there is no observable cost.
 * <p>
 * Per-captive escape-attempt timestamps live only in memory; a server restart
 * resets them, which is fine — a restart already takes longer than the 5-min
 * attempt interval in practice.
 */
@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class CaptivityTimeoutService {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Maximum captivity duration in milliseconds (30 minutes). */
    private static final long CAPTIVITY_MAX_DURATION_MS = 30L * 60L * 1000L;

    /** Minimum interval between escape attempts per captive, in milliseconds (5 minutes). */
    private static final long ESCAPE_ATTEMPT_INTERVAL_MS = 5L * 60L * 1000L;

    /** Captor distance threshold beyond which escape attempts are eligible (blocks). */
    private static final double ESCAPE_DISTANCE_THRESHOLD = 32.0;
    private static final double ESCAPE_DISTANCE_THRESHOLD_SQ = ESCAPE_DISTANCE_THRESHOLD * ESCAPE_DISTANCE_THRESHOLD;

    /** Probability of an eligible escape attempt succeeding (0.30 = 30%). */
    private static final double ESCAPE_SUCCESS_CHANCE = 0.30;

    /** Sweep cadence in server ticks (1200 = 60 s at 20 TPS). */
    private static final int SWEEP_TICK_INTERVAL = 1200;

    /** Tick counter, advanced once per ServerTickEvent.Post. Modulo-style sweep trigger. */
    private static int tickCounter = 0;

    /**
     * Per-captive timestamp of the last escape-attempt roll, indexed by owner UUID.
     * In-memory only — cleared on server stop and lost across restarts by design.
     */
    private static final Map<UUID, Long> lastEscapeAttempt = new HashMap<>();

    private static final Random RNG = new Random();

    private CaptivityTimeoutService() {
        // static event subscriber — no instantiation
    }

    /**
     * Clears in-memory sweep state. Called by {@code ServerEvents.onServerStopping}
     * so that a clean reload on the same JVM does not carry stale escape-attempt
     * timestamps from the previous world.
     */
    public static void clear() {
        tickCounter = 0;
        lastEscapeAttempt.clear();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        if (tickCounter < SWEEP_TICK_INTERVAL) {
            return;
        }
        tickCounter = 0;

        CaptivityService service = CaptivityService.instance();
        if (service == null) {
            // Service not initialised yet (or already torn down). No-op.
            return;
        }

        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }

        try {
            sweep(service, server);
        } catch (Throwable t) {
            // A transient DB hiccup or malformed row must not nuke the sweep loop.
            // Log and continue; the next sweep is a minute away.
            LOGGER.warn("[Captivity] sweep failed: {}", t.toString());
        }
    }

    private static void sweep(CaptivityService service, MinecraftServer server) {
        long now = System.currentTimeMillis();
        long timeoutThreshold = now - CAPTIVITY_MAX_DURATION_MS;

        List<PassportsDao.Passport> captives = service.database().inTx(conn ->
                service.passportsDao().findAllCaptured(conn)
        );

        for (PassportsDao.Passport passport : captives) {
            if (passport.capturedByUuid() == null) {
                continue;
            }

            UUID ownerUuid;
            UUID captorUuid;
            try {
                ownerUuid = UUID.fromString(passport.ownerUuid());
                captorUuid = UUID.fromString(passport.capturedByUuid());
            } catch (IllegalArgumentException e) {
                LOGGER.warn("[Captivity] malformed UUID on passport {}; skipping", passport.passportId());
                continue;
            }

            // 1) Timeout sweep.
            //    captured_at may be NULL for captures from before the V3 migration —
            //    those rows are skipped here, never auto-released. The next capture
            //    cycle re-populates captured_at correctly.
            Long capturedAt = passport.capturedAt();
            if (capturedAt != null && capturedAt < timeoutThreshold) {
                releaseDueToTimeout(service, server, passport, ownerUuid, captorUuid);
                lastEscapeAttempt.remove(ownerUuid);
                continue;
            }

            // 2) Escape-attempt sweep.
            //    Per-captive throttle: at most one roll per ESCAPE_ATTEMPT_INTERVAL_MS.
            Long lastAttempt = lastEscapeAttempt.get(ownerUuid);
            if (lastAttempt != null && (now - lastAttempt) < ESCAPE_ATTEMPT_INTERVAL_MS) {
                continue;
            }

            // Owner must be online to attempt escape. An offline captive does not
            // generate roll opportunities — the timeout sweep is their only path out.
            ServerPlayer ownerPlayer = server.getPlayerList().getPlayer(ownerUuid);
            if (ownerPlayer == null) {
                continue;
            }

            // Eligibility: captor offline OR captor in another dimension OR > 32 blocks away.
            ServerPlayer captorPlayer = server.getPlayerList().getPlayer(captorUuid);
            boolean eligible;
            if (captorPlayer == null) {
                eligible = true;
            } else if (captorPlayer.level() != ownerPlayer.level()) {
                eligible = true;
            } else {
                double distSq = captorPlayer.position().distanceToSqr(ownerPlayer.position());
                eligible = distSq > ESCAPE_DISTANCE_THRESHOLD_SQ;
            }

            if (!eligible) {
                // Captor is on top of the captive. Don't burn the attempt timer —
                // just nudge the captive that they need to wait for an opening.
                ownerPlayer.displayClientMessage(
                        Component.translatable("wp.captivity.escape.too_close"),
                        true // action bar
                );
                continue;
            }

            // Eligible. Commit the attempt timestamp before rolling so a thrown
            // exception inside the success branch does not let the captive
            // re-roll within the cooldown.
            lastEscapeAttempt.put(ownerUuid, now);

            if (RNG.nextDouble() < ESCAPE_SUCCESS_CHANCE) {
                releaseDueToEscape(service, server, passport, ownerPlayer, captorUuid);
                lastEscapeAttempt.remove(ownerUuid);
            } else {
                ownerPlayer.displayClientMessage(
                        Component.translatable("wp.captivity.escape.failed"),
                        true
                );
            }
        }
    }

    private static void releaseDueToTimeout(CaptivityService service, MinecraftServer server,
                                            PassportsDao.Passport passport,
                                            UUID ownerUuid, UUID captorUuid) {
        CaptivityService.RansomResult result = service.ransom(
                passport.passportId(),
                passport.capturedByUuid(),
                passport.ownerUuid()
        );
        if (!(result instanceof CaptivityService.RansomResult.Success)) {
            LOGGER.warn("[Captivity] timeout ransom failed for passport {}: {}",
                    passport.passportId(),
                    ((CaptivityService.RansomResult.Failure) result).errorKey());
            return;
        }

        ServerPlayer ownerPlayer = server.getPlayerList().getPlayer(ownerUuid);
        if (ownerPlayer != null) {
            applyFreeStateOnline(ownerPlayer);
            ownerPlayer.sendSystemMessage(Component.translatable("wp.captivity.timeout.released"));
        }

        ServerPlayer captorPlayer = server.getPlayerList().getPlayer(captorUuid);
        if (captorPlayer != null) {
            CaptivityService.removeStaleTrophy(captorPlayer, passport.passportId());
            captorPlayer.sendSystemMessage(Component.translatable(
                    "wp.captivity.timeout.captor_notified",
                    passport.rpName() + " " + passport.rpSurname()));
        }

        LOGGER.info("[Captivity] auto-released passport {} after 30-min timeout (owner={}, captor={})",
                passport.passportId(), ownerUuid, captorUuid);
    }

    private static void releaseDueToEscape(CaptivityService service, MinecraftServer server,
                                           PassportsDao.Passport passport,
                                           ServerPlayer ownerPlayer, UUID captorUuid) {
        CaptivityService.RansomResult result = service.ransom(
                passport.passportId(),
                passport.capturedByUuid(),
                passport.ownerUuid()
        );
        if (!(result instanceof CaptivityService.RansomResult.Success)) {
            LOGGER.warn("[Captivity] escape ransom failed for passport {}: {}",
                    passport.passportId(),
                    ((CaptivityService.RansomResult.Failure) result).errorKey());
            return;
        }

        applyFreeStateOnline(ownerPlayer);
        ownerPlayer.sendSystemMessage(Component.translatable("wp.captivity.escape.success"));

        ServerPlayer captorPlayer = server.getPlayerList().getPlayer(captorUuid);
        if (captorPlayer != null) {
            CaptivityService.removeStaleTrophy(captorPlayer, passport.passportId());
            captorPlayer.sendSystemMessage(Component.translatable(
                    "wp.captivity.escape.captor_notified",
                    passport.rpName() + " " + passport.rpSurname()));
        }

        LOGGER.info("[Captivity] passport {} escaped (owner={}, captor={})",
                passport.passportId(), ownerPlayer.getUUID(), captorUuid);
    }

    /**
     * Mirrors the attachment / legacy-store fixups that {@code CaptivityService.ransom(String, ServerPlayer, ServerPlayer)}
     * normally applies. The DB-only ransom path used here does not touch attachments because
     * the captor is usually not the one initiating, so we apply them on the freed captive here.
     */
    private static void applyFreeStateOnline(ServerPlayer ownerPlayer) {
        ownerPlayer.setData(WpAttachmentTypes.CAPTURED.get(), false);
        ownerPlayer.setData(WpAttachmentTypes.PLAYER_STATE.get(), PlayerState.ACCEPTED);

        com.frostlogic.warproject.server.WarPlayerProfile legacy =
                com.frostlogic.warproject.server.WarPlayerDataStore.get().getOrCreate(ownerPlayer);
        legacy.setCaptive(false);
        legacy.setCapturedBy(null);
        com.frostlogic.warproject.server.WarPlayerDataStore.get().save();
        com.frostlogic.warproject.server.WarPrefixManager.refresh(ownerPlayer);
    }
}
