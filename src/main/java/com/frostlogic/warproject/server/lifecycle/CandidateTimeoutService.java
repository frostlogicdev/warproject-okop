package com.frostlogic.warproject.server.lifecycle;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.WpConfig;
import com.frostlogic.warproject.attachment.PlayerState;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforces the {@link PlayerState#CANDIDATE} timeout (Req. 7.8).
 * <p>
 * For every player in the {@code CANDIDATE} state we accumulate one tick per
 * {@link PlayerTickEvent.Post} while they are online. When the cumulative
 * <em>active</em> session time reaches {@code cfg.candidate.timeoutSeconds},
 * the player is kicked with the localized message
 * {@code wp.candidate.timeout}.
 * <p>
 * <strong>Persistence model.</strong> The counter is kept in an in-memory
 * {@link ConcurrentHashMap} keyed by player UUID. It is dropped on
 * {@link PlayerEvent.PlayerLoggedOutEvent}, so a re-login restarts the
 * countdown. This is intentional and matches the wording of Requirement 7.8
 * ("активной сессии"): only time the candidate is actually online counts
 * towards the timeout.
 * <p>
 * The counter is also dropped as soon as the player's state transitions away
 * from {@code CANDIDATE} (e.g. acceptance into the faction), which is detected
 * lazily on the next tick where the state no longer equals {@code CANDIDATE}.
 * <p>
 * Requirements: 7.8
 * Design: §12 Property 16
 */
@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class CandidateTimeoutService {

    /** Minecraft tick rate (ticks per second). */
    private static final int TICKS_PER_SECOND = 20;

    /**
     * Active-session tick counter per player UUID.
     * <p>
     * Increments on each {@link PlayerTickEvent.Post} while the owning player
     * is in {@link PlayerState#CANDIDATE}. Cleared on logout and on the first
     * tick during which the state is no longer {@code CANDIDATE}.
     */
    private static final Map<UUID, Long> ACTIVE_TICKS = new ConcurrentHashMap<>();

    private CandidateTimeoutService() {
        // static event subscriber — no instantiation
    }

    // ─── Public helpers ───────────────────────────────────────────────────────────

    /**
     * Property 16: returns {@code true} iff the supplied elapsed active-session
     * time, in milliseconds, has reached or exceeded the configured candidate
     * timeout ({@link WpConfig#CANDIDATE_TIMEOUT_SECONDS}).
     * <p>
     * This pure-function form is the one verified by the PBT in task 14.4.
     *
     * @param elapsedMs accumulated active-session time, in milliseconds
     * @return {@code true} if the player should be kicked
     */
    public static boolean shouldKick(long elapsedMs) {
        return shouldKick(elapsedMs, WpConfig.CANDIDATE_TIMEOUT_SECONDS.get());
    }

    /**
     * Pure-predicate variant of {@link #shouldKick(long)} that takes the timeout
     * value explicitly instead of reading it from {@link WpConfig}.
     * <p>
     * Used by property-based tests (task 14.4) to exercise Property 16 across
     * the entire input space without bootstrapping the NeoForge config spec.
     *
     * @param elapsedMs       accumulated active-session time, in milliseconds
     * @param timeoutSeconds  configured candidate timeout, in seconds
     * @return {@code true} iff {@code elapsedMs >= timeoutSeconds * 1000L}
     */
    public static boolean shouldKick(long elapsedMs, int timeoutSeconds) {
        long timeoutMs = (long) timeoutSeconds * 1000L;
        return elapsedMs >= timeoutMs;
    }

    /**
     * Returns the current cumulative active-session tick count for the given
     * player UUID, or {@code 0} if no counter is currently being tracked.
     * <p>
     * Intended for diagnostics and tests; not part of the design API.
     */
    public static long activeTicks(UUID uuid) {
        return ACTIVE_TICKS.getOrDefault(uuid, 0L);
    }

    /**
     * Drops the per-player counter. Safe to call when no entry exists.
     * <p>
     * Used by {@link PlayerLifecycleService} (or any state mutator) when a
     * player leaves the {@code CANDIDATE} state by acceptance, capture, etc.,
     * but it is also self-corrected on the next server tick.
     */
    public static void reset(UUID uuid) {
        ACTIVE_TICKS.remove(uuid);
    }

    // ─── Event subscribers ────────────────────────────────────────────────────────

    /**
     * Increments the active-tick counter for every {@code CANDIDATE} player and
     * kicks them when the configured threshold is reached.
     * <p>
     * Players in any other state get their counter cleared on the next tick.
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID uuid = player.getUUID();
        PlayerState state = player.getData(WpAttachmentTypes.PLAYER_STATE.get());
        if (state != PlayerState.CANDIDATE) {
            ACTIVE_TICKS.remove(uuid);
            return;
        }

        long ticks = ACTIVE_TICKS.merge(uuid, 1L, Long::sum);

        long timeoutTicks = (long) WpConfig.CANDIDATE_TIMEOUT_SECONDS.get() * TICKS_PER_SECOND;
        if (ticks >= timeoutTicks) {
            ACTIVE_TICKS.remove(uuid);
            if (player.connection != null) {
                player.connection.disconnect(Component.translatable("wp.candidate.timeout"));
                WarProject.LOGGER.info(
                        "[WP Candidate] Kicked player {} ({}); candidate timeout reached after {} active ticks.",
                        player.getGameProfile().getName(), uuid, ticks);
            }
        }
    }

    /**
     * Drops the per-player counter on disconnect so the next session starts
     * with a fresh countdown (active-session semantics, Req. 7.8).
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ACTIVE_TICKS.remove(player.getUUID());
        }
    }
}
