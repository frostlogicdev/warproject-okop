package com.frostlogic.warproject.server.auth;

import com.frostlogic.warproject.WpConfig;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link LoginAttemptTracker}.
 * <p>
 * Tracks consecutive failed login attempts per player and enforces
 * cooldown/kick policies when the configured threshold is reached.
 * <p>
 * State is lost on server restart — this is acceptable because the
 * {@code accounts.failed_login_cnt} column in the database provides
 * persistent tracking across restarts.
 * <p>
 * Requirements: 3.4
 */
public final class InMemoryLoginAttemptTracker implements LoginAttemptTracker {

    private static final long COOLDOWN_DURATION_MS = 60_000L; // 60 seconds

    private final Map<UUID, AttemptState> states = new ConcurrentHashMap<>();

    private record AttemptState(int failures, long cooldownUntil) {}

    @Override
    public void recordFailure(UUID playerUuid) {
        states.compute(playerUuid, (uuid, existing) -> {
            int newFailures = (existing == null ? 0 : existing.failures()) + 1;
            int maxAttempts = WpConfig.AUTH_LOGIN_ATTEMPTS.get();
            long cooldownUntil = 0L;
            if (newFailures >= maxAttempts) {
                cooldownUntil = System.currentTimeMillis() + COOLDOWN_DURATION_MS;
            }
            return new AttemptState(newFailures, cooldownUntil);
        });
    }

    @Override
    public void resetFailures(UUID playerUuid) {
        states.remove(playerUuid);
    }

    @Override
    public boolean isOnCooldown(UUID playerUuid) {
        AttemptState state = states.get(playerUuid);
        if (state == null) {
            return false;
        }
        if (state.cooldownUntil() <= 0L) {
            return false;
        }
        if (System.currentTimeMillis() >= state.cooldownUntil()) {
            // Cooldown expired — reset
            states.remove(playerUuid);
            return false;
        }
        return true;
    }

    @Override
    public boolean shouldKick(UUID playerUuid) {
        AttemptState state = states.get(playerUuid);
        if (state == null) {
            return false;
        }
        return state.failures() >= WpConfig.AUTH_LOGIN_ATTEMPTS.get();
    }
}
