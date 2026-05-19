package com.frostlogic.warproject.server.auth;

import com.frostlogic.warproject.WpConfig;
import com.frostlogic.warproject.persistence.Database;
import com.frostlogic.warproject.persistence.dao.CooldownsDao;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory + DB-backed implementation of {@link LoginAttemptTracker}.
 * <p>
 * Tracks consecutive failed login attempts per player using a
 * {@link ConcurrentHashMap} for fast in-memory access. When the
 * configurable threshold is reached, a LOGIN cooldown is written
 * to the {@code cooldowns} table via {@link CooldownsDao}, and
 * the player is flagged for kick.
 * <p>
 * The cooldown duration is taken from {@link WpConfig#CAPTCHA_LOGIN_COOLDOWN_SECONDS}
 * (default 90s), reusing the same cooldown duration as captcha failures.
 * <p>
 * Requirements: 3.4
 * Design: §3, §5.1
 */
public final class LoginAttemptTrackerImpl implements LoginAttemptTracker {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String COOLDOWN_TYPE_LOGIN = "LOGIN";

    private final ConcurrentHashMap<UUID, AtomicInteger> failureCounts = new ConcurrentHashMap<>();
    private final Database database;
    private final CooldownsDao cooldownsDao;

    public LoginAttemptTrackerImpl(Database database, CooldownsDao cooldownsDao) {
        this.database = database;
        this.cooldownsDao = cooldownsDao;
    }

    @Override
    public void recordFailure(UUID playerUuid) {
        AtomicInteger counter = failureCounts.computeIfAbsent(playerUuid, k -> new AtomicInteger(0));
        int newCount = counter.incrementAndGet();

        int threshold = getThreshold();
        if (newCount >= threshold) {
            // Write cooldown to DB
            long cooldownSeconds = WpConfig.CAPTCHA_LOGIN_COOLDOWN_SECONDS.get();
            long expiresAt = System.currentTimeMillis() + (cooldownSeconds * 1000L);

            try {
                database.transaction(conn ->
                        cooldownsDao.upsert(conn, playerUuid.toString(), COOLDOWN_TYPE_LOGIN, expiresAt)
                );
            } catch (Exception e) {
                LOGGER.error("Failed to write LOGIN cooldown for player {}", playerUuid, e);
            }

            LOGGER.info("Player {} reached login attempt threshold ({}/{}), cooldown applied",
                    playerUuid, newCount, threshold);
        }
    }

    @Override
    public void resetFailures(UUID playerUuid) {
        failureCounts.remove(playerUuid);
    }

    @Override
    public boolean isOnCooldown(UUID playerUuid) {
        // Check in-memory first: if threshold reached, likely on cooldown
        AtomicInteger counter = failureCounts.get(playerUuid);
        if (counter != null && counter.get() >= getThreshold()) {
            // Verify against DB to confirm cooldown is still active
            return checkDbCooldown(playerUuid);
        }

        // Also check DB directly in case the server restarted
        // (in-memory map would be empty but DB cooldown may still be active)
        return checkDbCooldown(playerUuid);
    }

    @Override
    public boolean shouldKick(UUID playerUuid) {
        AtomicInteger counter = failureCounts.get(playerUuid);
        if (counter == null) {
            return false;
        }
        return counter.get() >= getThreshold();
    }

    /**
     * Returns the configured maximum login attempts threshold.
     */
    private int getThreshold() {
        return WpConfig.AUTH_LOGIN_ATTEMPTS.get();
    }

    /**
     * Checks the {@code cooldowns} table for an active LOGIN cooldown.
     */
    private boolean checkDbCooldown(UUID playerUuid) {
        try {
            long now = System.currentTimeMillis();
            return database.inTx(conn ->
                    cooldownsDao.isActive(conn, playerUuid.toString(), COOLDOWN_TYPE_LOGIN, now)
            );
        } catch (Exception e) {
            LOGGER.error("Failed to check LOGIN cooldown for player {}", playerUuid, e);
            // Fail-open: if DB is unavailable, don't block the player
            return false;
        }
    }
}
