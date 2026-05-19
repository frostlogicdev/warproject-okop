package com.frostlogic.warproject.client;

import com.frostlogic.warproject.network.payload.s2c.PlayerPublicViewPayload;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache of {@link PlayerPublicViewPayload} records keyed by player UUID.
 * <p>
 * Populated from S2C {@code PlayerPublicViewPayload} packets via
 * {@link com.frostlogic.warproject.network.ClientPayloadHandler#onPlayerPublicView}.
 * Consumed by client renderers (name tag, TAB list, HUD) to display per-player
 * public state — faction, role, status, collaborator flag, RP name.
 * <p>
 * The map is thread-safe; payload handlers enqueue work onto the client thread
 * before mutating the cache, but reads can come from the main render thread.
 * <p>
 * Requirements: 11.2, 21.4
 * Design: §9.4
 */
public final class PlayerPublicViewCache {

    private static final Map<UUID, PlayerPublicViewPayload> ENTRIES = new ConcurrentHashMap<>();

    private PlayerPublicViewCache() {
        // static cache — no instantiation
    }

    /**
     * Stores or replaces the public view for {@code view.uuid()}.
     */
    public static void put(PlayerPublicViewPayload view) {
        ENTRIES.put(view.uuid(), view);
    }

    /**
     * Returns the cached public view for {@code uuid}, or {@code null} if no
     * payload has been received for that player yet.
     */
    @Nullable
    public static PlayerPublicViewPayload get(UUID uuid) {
        return ENTRIES.get(uuid);
    }

    /**
     * Removes the cached view for {@code uuid}. Used when a player disconnects
     * or no longer needs to be tracked.
     */
    public static void remove(UUID uuid) {
        ENTRIES.remove(uuid);
    }

    /**
     * Clears the entire cache. Called on client disconnect to avoid leaking
     * state into a subsequent session.
     */
    public static void clear() {
        ENTRIES.clear();
    }

    /**
     * Convenience predicate: true if a public view is cached for {@code uuid}
     * and its {@code collaborator} flag is set.
     */
    public static boolean isCollaborator(UUID uuid) {
        PlayerPublicViewPayload view = ENTRIES.get(uuid);
        return view != null && view.collaborator();
    }
}
