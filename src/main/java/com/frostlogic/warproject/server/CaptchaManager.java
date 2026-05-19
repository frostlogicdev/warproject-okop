package com.frostlogic.warproject.server;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * WGuard captcha pipeline.
 * <p>
 * On {@link #issue(ServerPlayer)} the player is teleported high into the sky and
 * frozen in a no-gravity, invulnerable, flight-locked state. They have 3 minutes
 * to type {@code /wp captcha <code>}; up to 3 wrong attempts are allowed. Any
 * failure (timeout or three wrong codes) kicks the player and writes a 90 s
 * reconnect cooldown against their UUID, enforced on next login.
 */
public final class CaptchaManager {
    /** How long the player has to answer the captcha. */
    public static final long CAPTCHA_TIMEOUT_MS = 3L * 60L * 1000L;
    /** Number of wrong attempts allowed before kicking. */
    public static final int MAX_ATTEMPTS = 3;
    /** Wait time before a kicked player can reconnect. */
    public static final long RECONNECT_COOLDOWN_MS = 90L * 1000L;
    /** Sky-cage absolute floor — we always lift the player at least to this Y. */
    public static final int SKY_Y = 250;
    /**
     * Translation key for the captcha-failed kick screen. Use as
     * {@code Component.translatable(KICK_MESSAGE_KEY)}; the legacy
     * {@code KICK_MESSAGE} String constant is kept below for code that needs
     * a plain-text fallback.
     */
    public static final String KICK_MESSAGE_KEY = "wp.captcha.failed";
    /** Standard kick text mandated by the spec (legacy plain-text fallback). */
    public static final String KICK_MESSAGE = "WGuard: Вы не смогли пройти капчу! Попробуйте чуть позже!";

    private static final ConcurrentHashMap<UUID, Integer> CODES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> ISSUED_AT = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Integer> ATTEMPTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, SavedState> SAVED = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> COOLDOWN_UNTIL = new ConcurrentHashMap<>();

    private CaptchaManager() {
    }

    /** Outcome of a {@link #attempt(ServerPlayer, int)} call. */
    public enum AttemptResult {
        /** Code matched; caller should call {@link #finishSuccess(ServerPlayer)}. */
        SUCCESS,
        /** Code wrong but the player still has another try left. */
        WRONG_RETRY,
        /** Final wrong attempt — caller must call {@link #kickForFailure(ServerPlayer)}. */
        WRONG_KICK,
        /** No captcha is currently issued for this player. */
        NOT_PENDING
    }

    /** Snapshot of the player's flight + position before we sky-caged them. */
    private record SavedState(ResourceKey<Level> dimension, double x, double y, double z, float yaw, float pitch,
                               boolean mayFly, boolean flying, boolean invulnerable) {
    }

    public static void issue(ServerPlayer player) {
        UUID uuid = player.getUUID();
        // Snapshot the player's current ability + position so we can restore on success.
        SAVED.putIfAbsent(uuid, snapshot(player));

        int code = ThreadLocalRandom.current().nextInt(1000, 10000);
        CODES.put(uuid, code);
        ISSUED_AT.put(uuid, System.currentTimeMillis());
        ATTEMPTS.putIfAbsent(uuid, 0);

        teleportToSky(player);
        sendChallenge(player, code);
    }

    /** Re-pin the player to the sky cage every tick while captcha is pending.
     *  Called from {@code OnboardingGuard} so the player can't wander off. */
    @SuppressWarnings("deprecation") // Abilities.mayfly stays usable; the NeoForge
                                       // attribute alternative is overkill for a
                                       // transient captcha-window flight flag.
    public static void enforceSkyCage(ServerPlayer player) {
        if (!isPending(player.getUUID())) {
            return;
        }
        SavedState saved = SAVED.get(player.getUUID());
        double targetY = Math.max(SKY_Y, saved == null ? SKY_Y : saved.y() + 50);
        // Clamp vertical drift, kill all velocity, ensure abilities.
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
        if (player.getY() < targetY - 2.0D || player.getY() > targetY + 6.0D) {
            player.teleportTo(
                    player.serverLevel(),
                    Math.floor(player.getX()) + 0.5D,
                    targetY,
                    Math.floor(player.getZ()) + 0.5D,
                    Set.<RelativeMovement>of(),
                    player.getYRot(),
                    player.getXRot()
            );
        }
        // Make sure the player can hover (flight on, no fall damage).
        if (!player.getAbilities().mayfly || !player.getAbilities().flying || !player.getAbilities().invulnerable) {
            player.getAbilities().mayfly = true;
            player.getAbilities().flying = true;
            player.getAbilities().invulnerable = true;
            player.onUpdateAbilities();
        }
    }

    public static void sendChallenge(ServerPlayer player, int code) {
        player.sendSystemMessage(Component.translatable("wp.captcha.header").withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.translatable("wp.captcha.legacy.header").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.translatable("wp.captcha.instruction", code).withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.translatable("wp.captcha.legacy.warning", MAX_ATTEMPTS).withStyle(ChatFormatting.RED));
        player.sendSystemMessage(Component.translatable("wp.captcha.header").withStyle(ChatFormatting.GOLD));

        Component title = Component.translatable("wp.captcha.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        Component subtitle = Component.translatable("wp.captcha.subtitle", code).withStyle(ChatFormatting.YELLOW);
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(title));
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(subtitle));
    }

    /** Tries to verify the player's submitted code. Returns the next-step decision. */
    public static AttemptResult attempt(ServerPlayer player, int code) {
        UUID uuid = player.getUUID();
        Integer expected = CODES.get(uuid);
        if (expected == null) {
            return AttemptResult.NOT_PENDING;
        }
        if (expected.intValue() == code) {
            return AttemptResult.SUCCESS;
        }
        int used = ATTEMPTS.merge(uuid, 1, Integer::sum);
        if (used >= MAX_ATTEMPTS) {
            return AttemptResult.WRONG_KICK;
        }
        return AttemptResult.WRONG_RETRY;
    }

    /** Cleans up server-side state and restores the player's abilities after a
     *  successful captcha. Caller is responsible for teleporting the player to
     *  the hall-of-faction-choice / RP-name prompt. */
    public static void finishSuccess(ServerPlayer player) {
        UUID uuid = player.getUUID();
        SavedState saved = SAVED.remove(uuid);
        CODES.remove(uuid);
        ISSUED_AT.remove(uuid);
        ATTEMPTS.remove(uuid);
        restoreAbilities(player, saved);
    }

    /**
     * Same cleanup as {@link #finishSuccess(ServerPlayer)} but does NOT restore
     * abilities — the player remains in flight + invuln state. Used after a
     * successful captcha when the player must continue floating in the sky-cage
     * until they submit {@code /wp rpname}.
     */
    public static void finishSuccessKeepFlying(ServerPlayer player) {
        UUID uuid = player.getUUID();
        SAVED.remove(uuid);
        CODES.remove(uuid);
        ISSUED_AT.remove(uuid);
        ATTEMPTS.remove(uuid);
    }

    /**
     * Restores normal (non-flying, non-invulnerable) abilities for the player.
     * Defensive default — drops all captcha-style privileges. Mayfly is enabled
     * only for creative-mode players. Use after the rp-name step to bring the
     * player back to ground-state survival before teleporting them to spawn.
     */
    @SuppressWarnings("deprecation")
    public static void restoreNormalAbilities(ServerPlayer player) {
        if (player == null) {
            return;
        }
        boolean creative = player.isCreative();
        player.getAbilities().mayfly = creative;
        player.getAbilities().flying = false;
        player.getAbilities().invulnerable = creative;
        player.onUpdateAbilities();
    }

    /** Kicks the player with the standard message and writes a 90 s reconnect cooldown. */
    public static void kickForFailure(ServerPlayer player) {
        UUID uuid = player.getUUID();
        COOLDOWN_UNTIL.put(uuid, System.currentTimeMillis() + RECONNECT_COOLDOWN_MS);
        SavedState saved = SAVED.remove(uuid);
        CODES.remove(uuid);
        ISSUED_AT.remove(uuid);
        ATTEMPTS.remove(uuid);
        // Restore so the player isn't stuck in flying+invuln on reconnect (defence in depth).
        restoreAbilities(player, saved);
        if (player.connection != null) {
            player.connection.disconnect(Component.translatable(KICK_MESSAGE_KEY));
        }
    }

    /** True if {@code uuid} is still within their post-failure 90 s cooldown. */
    public static boolean isOnCooldown(UUID uuid) {
        Long until = COOLDOWN_UNTIL.get(uuid);
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() >= until) {
            COOLDOWN_UNTIL.remove(uuid);
            return false;
        }
        return true;
    }

    public static long getCooldownRemainingMs(UUID uuid) {
        Long until = COOLDOWN_UNTIL.get(uuid);
        if (until == null) {
            return 0L;
        }
        return Math.max(0L, until - System.currentTimeMillis());
    }

    public static boolean isPending(UUID uuid) {
        return CODES.containsKey(uuid);
    }

    public static long getIssuedAt(UUID uuid) {
        Long value = ISSUED_AT.get(uuid);
        return value == null ? 0L : value;
    }

    public static int getAttemptsUsed(UUID uuid) {
        Integer value = ATTEMPTS.get(uuid);
        return value == null ? 0 : value;
    }

    public static void clear(UUID uuid) {
        CODES.remove(uuid);
        ISSUED_AT.remove(uuid);
        ATTEMPTS.remove(uuid);
        SAVED.remove(uuid);
    }

    /* ====================== internal ====================== */

    @SuppressWarnings("deprecation") // see enforceSkyCage above
    private static SavedState snapshot(ServerPlayer player) {
        return new SavedState(
                player.serverLevel().dimension(),
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(),
                player.getAbilities().mayfly,
                player.getAbilities().flying,
                player.getAbilities().invulnerable
        );
    }

    private static void teleportToSky(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        double targetY = Math.max(SKY_Y, player.getY() + 50);
        // Round x/z down to mid-block so we don't snap mid-step.
        double tx = Math.floor(player.getX()) + 0.5D;
        double tz = Math.floor(player.getZ()) + 0.5D;

        // Abilities first: flight on, invuln, no-clip off (we WANT the player
        // to read the air as solid so they can stand still mentally).
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.getAbilities().invulnerable = true;
        player.onUpdateAbilities();

        player.teleportTo(level, tx, targetY, tz, Collections.<RelativeMovement>emptySet(), player.getYRot(), player.getXRot());
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
    }

    private static void restoreAbilities(ServerPlayer player, SavedState saved) {
        if (player == null) {
            return;
        }
        if (saved != null) {
            player.getAbilities().mayfly = saved.mayFly();
            player.getAbilities().flying = saved.flying();
            player.getAbilities().invulnerable = saved.invulnerable();
        } else {
            // Defensive default: drop privileges so we never leak captcha flight.
            boolean creative = player.isCreative();
            player.getAbilities().mayfly = creative;
            player.getAbilities().flying = false;
            player.getAbilities().invulnerable = creative;
        }
        player.onUpdateAbilities();
    }
}
