package com.frostlogic.warproject.server.award;

/**
 * Represents an award definition stored in the database.
 * <p>
 * Contains the metadata for an award that can be granted to players:
 * name, description, icon identifier, and creation timestamp.
 * <p>
 * Requirements: 13.5, 15.1, 15.2
 */
public record AwardDefinition(
        int id,
        String name,
        String description,
        String iconId,
        long createdAt
) {
}
