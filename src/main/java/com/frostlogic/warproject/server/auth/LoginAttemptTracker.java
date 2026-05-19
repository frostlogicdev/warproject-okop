package com.frostlogic.warproject.server.auth;

import java.util.UUID;

/**
 * Tracks consecutive failed login attempts per player and enforces
 * cooldown/kick policies when the threshold is reached.
 * <p>
 * This is a placeholder interface for task 4.3. The full implementation
 * (in-memory tracking + {@code cooldowns} table persistence) will be
 * provided in task 4.5.
 * <p>
 * Requirements: 3.4
 */
public interface LoginAttemptTracker {

    /**
     * Records a failed login attempt for the given player.
     *
     * @param playerUuid the UUID of the player who failed to log in
     */
    void recordFailure(UUID playerUuid);

    /**
     * Resets the failure counter for the given player (called on successful login).
     *
     * @param playerUuid the UUID of the player
     */
    void resetFailures(UUID playerUuid);

    /**
     * Returns whether the player is currently in a login cooldown
     * (too many failed attempts).
     *
     * @param playerUuid the UUID of the player
     * @return {@code true} if the player must wait before attempting login again
     */
    boolean isOnCooldown(UUID playerUuid);

    /**
     * Returns whether the player should be kicked due to exceeding
     * the maximum number of failed attempts.
     *
     * @param playerUuid the UUID of the player
     * @return {@code true} if the player should be disconnected
     */
    boolean shouldKick(UUID playerUuid);
}
