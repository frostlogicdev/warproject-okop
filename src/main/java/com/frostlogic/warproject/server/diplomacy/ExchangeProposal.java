package com.frostlogic.warproject.server.diplomacy;

import com.frostlogic.warproject.attachment.FactionId;

/**
 * Represents a pending prisoner exchange proposal between factions.
 * <p>
 * Tracks the proposing faction, the names of the two prisoners to be exchanged,
 * and the creation timestamp for timeout checking (300s timeout).
 * <p>
 * Requirements: 11.1
 */
public record ExchangeProposal(
        FactionId proposingFaction,
        String ownPrisoner,
        String enemyPrisoner,
        long createdAt
) {
    /**
     * Returns {@code true} if the proposal has exceeded the 300-second timeout.
     */
    public boolean isExpired() {
        return System.currentTimeMillis() > createdAt + 300_000L;
    }
}
