package com.frostlogic.warproject.server.diplomacy;

import com.frostlogic.warproject.attachment.FactionId;

/**
 * Represents an active truce between two factions.
 * <p>
 * Tracks the proposing and target factions, the agreed duration, the start time,
 * and the database row ID for persistence.
 * <p>
 * Requirements: 10.1, 10.6
 */
public record TruceState(
        FactionId proposingFaction,
        FactionId targetFaction,
        int durationMinutes,
        long startedAt,
        int dbId
) {
    /**
     * Returns {@code true} if the truce duration has elapsed based on the current system time.
     */
    public boolean isExpired() {
        return System.currentTimeMillis() > startedAt + (durationMinutes * 60_000L);
    }
}
