package com.frostlogic.warproject.server.event;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * In-memory representation of an event. The {@code participants} set is used only
 * during ACTIVE status to track who joined; it is not persisted directly.
 * <p>
 * Requirements: 7.1, 8.1, 9.1
 * Design: §3 Event System — EventData
 */
public record EventData(
        int id,
        String name,
        EventState status,
        UUID creatorUuid,
        @Nullable BlockPos spawnPos,
        @Nullable ResourceKey<Level> dimension,
        long createdAt,
        @Nullable Long completedAt,
        Set<UUID> participants
) {
    /**
     * Creates a new EventData in PENDING state with no spawn, no completion time,
     * and an empty participant set.
     */
    public static EventData createPending(int id, String name, UUID creatorUuid, long createdAt) {
        return new EventData(id, name, EventState.PENDING, creatorUuid, null, null, createdAt, null, new HashSet<>());
    }
}
