package com.frostlogic.warproject.server.diplomacy;

import com.frostlogic.warproject.attachment.FactionId;

/**
 * Represents a pending truce proposal awaiting acceptance from the opposing faction.
 * <p>
 * Tracks the proposing faction, target faction, proposed duration, and creation
 * timestamp for timeout checking (60s timeout).
 * <p>
 * Requirements: 10.1, 10.10
 */
public record TruceProposal(
        FactionId proposingFaction,
        FactionId targetFaction,
        int durationMinutes,
        long createdAt
) {
    /**
     * Returns {@code true} if the proposal has exceeded the 60-second timeout.
     */
    public boolean isExpired() {
        return System.currentTimeMillis() > createdAt + 60_000L;
    }
}
