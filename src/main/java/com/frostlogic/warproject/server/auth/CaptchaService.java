package com.frostlogic.warproject.server.auth;

import com.frostlogic.warproject.WpConfig;
import com.frostlogic.warproject.attachment.PlayerState;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import com.frostlogic.warproject.persistence.Database;
import com.frostlogic.warproject.persistence.dao.CooldownsDao;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages captcha sessions for players after registration.
 * <p>
 * Lifecycle per player:
 * <ol>
 *   <li>{@link #startSession(ServerPlayer)} — generates code, teleports to captcha spawn,
 *       applies NoGravity effect, sends code via title/actionbar/chat</li>
 *   <li>{@link #submit(ServerPlayer, String)} — validates submitted code, handles
 *       success (advance to RPNAME_REQUIRED) or failure (kick + cooldown)</li>
 *   <li>{@link #tick(MinecraftServer)} — called on ServerTickEvent.Post, checks timeouts</li>
 * </ol>
 * <p>
 * The active code is never written to audit_log or general logs.
 * <p>
 * Requirements: 5.1–5.10
 * Design: §5.2
 */
public final class CaptchaService {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String COOLDOWN_TYPE_LOGIN = "LOGIN";

    private final Map<UUID, CaptchaSession> sessions = new ConcurrentHashMap<>();
    private final Database database;
    private final CooldownsDao cooldownsDao;

    public CaptchaService(Database database, CooldownsDao cooldownsDao) {
        this.database = database;
        this.cooldownsDao = cooldownsDao;
    }

    // ─── Public API ───────────────────────────────────────────────────────────────

    /**
     * Starts a new captcha session for the given player.
     * <p>
     * Effects:
     * <ul>
     *   <li>Generates a random code from the configured alphabet and length</li>
     *   <li>Teleports the player to the captcha spawn point (from config)</li>
     *   <li>Applies NoGravity and flight to keep the player suspended</li>
     *   <li>Sets player state to {@link PlayerState#CAPTCHA}</li>
     *   <li>Sends the code via title, actionbar, and system message</li>
     * </ul>
     *
     * @param player the server player who just registered
     */
    public void startSession(ServerPlayer player) {
        UUID uuid = player.getUUID();

        // Generate code from config
        String code = generateCode();

        // Get current server tick
        long currentTick = player.server.getTickCount();

        // Create and store session
        CaptchaSession session = new CaptchaSession(code, 0, currentTick);
        sessions.put(uuid, session);

        // Set player state to CAPTCHA
        player.setData(WpAttachmentTypes.PLAYER_STATE.get(), PlayerState.CAPTCHA);

        // Teleport to captcha spawn
        teleportToCaptchaSpawn(player);

        // Apply NoGravity + flight to keep player suspended
        applyNoGravity(player);

        // Send the code to the player
        sendCaptchaCode(player, code);

        LOGGER.debug("Captcha session started for player {}", uuid);
    }

    /**
     * Submits a captcha code for verification.
     *
     * @param player the player submitting the code
     * @param code   the code entered by the player
     * @return the result of the submission
     */
    public SubmitResult submit(ServerPlayer player, String code) {
        UUID uuid = player.getUUID();
        CaptchaSession session = sessions.get(uuid);

        if (session == null) {
            return SubmitResult.NOT_PENDING;
        }

        // Compare codes (case sensitivity from config)
        boolean caseSensitive = WpConfig.CAPTCHA_CASE_SENSITIVE.get();
        boolean matches = caseSensitive
                ? session.code().equals(code)
                : session.code().equalsIgnoreCase(code);

        if (matches) {
            // Success — advance to RPNAME_REQUIRED
            removeSession(uuid);
            restorePlayerAbilities(player);
            player.setData(WpAttachmentTypes.PLAYER_STATE.get(), PlayerState.RPNAME_REQUIRED);
            LOGGER.info("Player {} passed captcha successfully", uuid);
            return SubmitResult.SUCCESS;
        }

        // Wrong code — increment attempts
        int maxAttempts = WpConfig.CAPTCHA_ATTEMPTS.get();
        int newAttempts = session.incrementAttempts();

        if (newAttempts >= maxAttempts) {
            // Too many failed attempts — kick with cooldown
            failSession(player, uuid);
            return SubmitResult.FAILED_KICK;
        }

        // Still has attempts left
        int remaining = maxAttempts - newAttempts;
        return new SubmitResult.WrongRetry(remaining);
    }

    /**
     * Called on every server tick ({@code ServerTickEvent.Post}).
     * Checks all active sessions for timeout and kicks timed-out players.
     *
     * @param server the Minecraft server instance
     */
    public void tick(MinecraftServer server) {
        if (sessions.isEmpty()) {
            return;
        }

        long currentTick = server.getTickCount();
        int timeoutTicks = WpConfig.CAPTCHA_TIMEOUT_SECONDS.get() * 20; // 20 ticks per second

        // Iterate over a snapshot to avoid ConcurrentModificationException
        for (Map.Entry<UUID, CaptchaSession> entry : sessions.entrySet()) {
            UUID uuid = entry.getKey();
            CaptchaSession session = entry.getValue();

            long elapsed = currentTick - session.startTickServer();
            if (elapsed >= timeoutTicks) {
                // Timeout — find the player and kick them
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player != null) {
                    failSession(player, uuid);
                } else {
                    // Player disconnected — just clean up
                    sessions.remove(uuid);
                }
            }
        }
    }

    /**
     * Removes a session for the given UUID (cleanup on disconnect, etc.).
     *
     * @param uuid the player's UUID
     */
    public void removeSession(UUID uuid) {
        sessions.remove(uuid);
    }

    /**
     * Returns whether a captcha session is currently active for the given UUID.
     */
    public boolean hasSession(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    /**
     * Returns the current session for the given UUID, or null if none exists.
     */
    public CaptchaSession getSession(UUID uuid) {
        return sessions.get(uuid);
    }

    // ─── Result Types ─────────────────────────────────────────────────────────────

    /**
     * Result of a captcha submission.
     */
    public sealed interface SubmitResult {
        /** Code matched — player advances to RPNAME_REQUIRED. */
        SubmitResult SUCCESS = new Success();
        /** No active session for this player. */
        SubmitResult NOT_PENDING = new NotPending();
        /** All attempts exhausted — player kicked with cooldown. */
        SubmitResult FAILED_KICK = new FailedKick();

        record Success() implements SubmitResult {}
        record NotPending() implements SubmitResult {}
        record FailedKick() implements SubmitResult {}
        /** Wrong code but player still has attempts remaining. */
        record WrongRetry(int attemptsLeft) implements SubmitResult {}
    }

    // ─── Internal Helpers ─────────────────────────────────────────────────────────

    /**
     * Generates a random captcha code using the configured alphabet and length.
     */
    private String generateCode() {
        String alphabet = WpConfig.CAPTCHA_ALPHABET.get();
        int length = WpConfig.CAPTCHA_CODE_LEN.get();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    /**
     * Teleports the player to the captcha spawn point defined in config.
     */
    private void teleportToCaptchaSpawn(ServerPlayer player) {
        double x = WpConfig.CAPTCHA_SPAWN_X.get() + 0.5;
        double y = WpConfig.CAPTCHA_SPAWN_Y.get();
        double z = WpConfig.CAPTCHA_SPAWN_Z.get() + 0.5;

        ServerLevel level = player.serverLevel();
        player.teleportTo(level, x, y, z, Set.of(), player.getYRot(), player.getXRot());
        player.setDeltaMovement(0.0, 0.0, 0.0);
        player.fallDistance = 0.0F;
    }

    /**
     * Applies NoGravity effect and flight abilities to keep the player suspended.
     */
    @SuppressWarnings("deprecation")
    private void applyNoGravity(ServerPlayer player) {
        player.setNoGravity(true);
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.getAbilities().invulnerable = true;
        player.onUpdateAbilities();
    }

    /**
     * Restores player abilities after captcha completion (success or failure).
     */
    @SuppressWarnings("deprecation")
    private void restorePlayerAbilities(ServerPlayer player) {
        player.setNoGravity(false);
        boolean creative = player.isCreative();
        player.getAbilities().mayfly = creative;
        player.getAbilities().flying = false;
        player.getAbilities().invulnerable = creative;
        player.onUpdateAbilities();
    }

    /**
     * Sends the captcha code to the player via title, actionbar, and system message.
     */
    private void sendCaptchaCode(ServerPlayer player, String code) {
        // System messages
        player.sendSystemMessage(Component.translatable("wp.captcha.header"));
        player.sendSystemMessage(Component.translatable("wp.captcha.instruction", code));
        player.sendSystemMessage(Component.translatable("wp.captcha.warning",
                WpConfig.CAPTCHA_TIMEOUT_SECONDS.get() / 60,
                WpConfig.CAPTCHA_ATTEMPTS.get()));
        player.sendSystemMessage(Component.translatable("wp.captcha.header"));

        // Title
        Component title = Component.translatable("wp.captcha.title");
        Component subtitle = Component.translatable("wp.captcha.subtitle", code);
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));

        // Actionbar
        Component actionBar = Component.translatable("wp.captcha.actionbar", code);
        player.connection.send(new ClientboundSetActionBarTextPacket(actionBar));
    }

    /**
     * Handles a failed captcha session: kicks the player and writes a login cooldown.
     */
    private void failSession(ServerPlayer player, UUID uuid) {
        removeSession(uuid);
        restorePlayerAbilities(player);

        // Write cooldown to DB
        int cooldownSeconds = WpConfig.CAPTCHA_LOGIN_COOLDOWN_SECONDS.get();
        long expiresAt = System.currentTimeMillis() + (cooldownSeconds * 1000L);

        try {
            database.transaction(conn ->
                    cooldownsDao.upsert(conn, uuid.toString(), COOLDOWN_TYPE_LOGIN, expiresAt)
            );
        } catch (Exception e) {
            LOGGER.error("Failed to write captcha cooldown for player {}", uuid, e);
        }

        // Kick the player
        if (player.connection != null) {
            player.connection.disconnect(Component.translatable("wp.captcha.failed"));
        }

        LOGGER.info("Player {} failed captcha (kicked with {}s cooldown)", uuid, cooldownSeconds);
    }
}
