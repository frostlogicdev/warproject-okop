package com.frostlogic.warproject.server.map;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.attachment.Role;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import com.frostlogic.warproject.network.payload.c2s.SetFactionMarkerPayload;
import com.frostlogic.warproject.network.payload.s2c.FactionMarkerActivePayload;
import com.frostlogic.warproject.network.payload.s2c.FactionMarkerClearPayload;
import com.frostlogic.warproject.persistence.Database;
import com.frostlogic.warproject.persistence.dao.PlayersDao;
import com.frostlogic.warproject.server.ServerEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * Squad-style command marker service.
 * <p>
 * Holds at most one marker per <i>scope</i>:
 * <ul>
 *   <li>Faction-wide marker — placed by COMMANDER or higher; visible to every
 *       member of that faction.</li>
 *   <li>Subdivision marker — placed by any SOLDIER+ member assigned to a
 *       subdivision; visible only to members of that subdivision.</li>
 * </ul>
 * Each marker has a server-capped TTL (≤ 600 seconds). On TTL expiry the
 * service emits {@link FactionMarkerClearPayload} to all subscribers; on a
 * new {@code set} for the same scope the previous marker is replaced atomically.
 * <p>
 * No persistence: markers live in memory only and are wiped on server restart.
 */
public final class FactionMarkerService {

    @Nullable
    private static FactionMarkerService INSTANCE;

    /** Snapshot of one active marker. */
    private record Marker(
            FactionMarkerActivePayload.Scope scope,
            String scopeKey,
            String dimensionId,
            int x,
            int y,
            int z,
            long expiresAtEpochMs,
            String ownerName
    ) {
        FactionMarkerActivePayload toActivePayload() {
            return new FactionMarkerActivePayload(scope, scopeKey, dimensionId, x, y, z, expiresAtEpochMs, ownerName);
        }
    }

    /** Key = "FACTION:<id>" or "SUBDIVISION:<id>". Lookup is O(1). */
    private final Map<String, Marker> active = new HashMap<>();

    private FactionMarkerService() {}

    public static synchronized void install() {
        if (INSTANCE != null) {
            uninstall();
        }
        INSTANCE = new FactionMarkerService();
        NeoForge.EVENT_BUS.register(INSTANCE);
        WarProject.LOGGER.debug("[WP Marker] FactionMarkerService installed");
    }

    public static synchronized void uninstall() {
        if (INSTANCE != null) {
            try { NeoForge.EVENT_BUS.unregister(INSTANCE); } catch (Throwable ignored) {}
            INSTANCE = null;
            WarProject.LOGGER.debug("[WP Marker] FactionMarkerService uninstalled");
        }
    }

    @Nullable
    public static FactionMarkerService get() {
        return INSTANCE;
    }

    /**
     * Handles an incoming {@code SetFactionMarkerPayload} from a client.
     * Validates the player's permissions, computes the scope, replaces any
     * prior marker for that scope, and broadcasts to the right audience.
     */
    public void handleSet(ServerPlayer player, SetFactionMarkerPayload payload) {
        Optional<FactionId> factionOpt = player.getData(WpAttachmentTypes.FACTION.get());
        if (factionOpt.isEmpty()) {
            return; // candidate / unaffiliated players are ignored silently
        }
        FactionId faction = factionOpt.get();
        Role role = player.getData(WpAttachmentTypes.ROLE.get());
        if (role == null || role == Role.CANDIDATE) {
            return;
        }

        // Determine scope. COMMANDER+ → faction-wide; SOLDIER → subdivision (if any).
        Database db = ServerEvents.getDatabase();
        if (db == null) return;

        FactionMarkerActivePayload.Scope scope;
        String scopeKey;

        if (role.ordinal() >= Role.COMMANDER.ordinal()) {
            scope = FactionMarkerActivePayload.Scope.FACTION;
            scopeKey = faction.getSerializedName();
        } else {
            // SOLDIER — must have a subdivision
            Integer subId = db.inTx(conn -> new PlayersDao()
                    .findByUuid(conn, player.getStringUUID())
                    .map(PlayersDao.Player::subdivisionId)
                    .orElse(null));
            if (subId == null) {
                return; // SOLDIER without a subdivision can't drop a marker
            }
            scope = FactionMarkerActivePayload.Scope.SUBDIVISION;
            scopeKey = "sub:" + subId;
        }

        // Clamp TTL into [10s, MAX]. Anything ≤ 0 is rejected; UI sends 60..600 typically.
        int ttl = Math.max(10, Math.min(payload.ttlSeconds(), SetFactionMarkerPayload.MAX_TTL_SECONDS));
        long expires = System.currentTimeMillis() + ttl * 1000L;

        Marker marker = new Marker(
                scope,
                scopeKey,
                player.level().dimension().location().toString(),
                payload.x(), payload.y(), payload.z(),
                expires,
                player.getGameProfile().getName()
        );

        String mapKey = scope.name() + ":" + scopeKey;
        active.put(mapKey, marker);
        broadcast(player.getServer(), marker);
        WarProject.LOGGER.debug("[WP Marker] {} placed {}/{} marker @ ({},{},{}) ttl={}s",
                player.getGameProfile().getName(), scope, scopeKey,
                marker.x(), marker.y(), marker.z(), ttl);
    }

    /** Broadcast active marker to all current eligible players. */
    private void broadcast(@Nullable MinecraftServer server, Marker marker) {
        if (server == null) return;
        FactionMarkerActivePayload payload = marker.toActivePayload();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (canSee(p, marker)) {
                PacketDistributor.sendToPlayer(p, payload);
            }
        }
    }

    /** Send all currently active markers to a single player (used on login). */
    public void resyncTo(ServerPlayer player) {
        long now = System.currentTimeMillis();
        for (Marker m : active.values()) {
            if (m.expiresAtEpochMs() > now && canSee(player, m)) {
                PacketDistributor.sendToPlayer(player, m.toActivePayload());
            }
        }
    }

    private static boolean canSee(ServerPlayer p, Marker m) {
        Optional<FactionId> factionOpt = p.getData(WpAttachmentTypes.FACTION.get());
        if (factionOpt.isEmpty()) return false;

        if (m.scope() == FactionMarkerActivePayload.Scope.FACTION) {
            return factionOpt.get().getSerializedName().equals(m.scopeKey());
        }

        // SUBDIVISION: must be assigned to the same subdivision id.
        Database db = ServerEvents.getDatabase();
        if (db == null) return false;
        Integer subId = db.inTx(conn -> new PlayersDao()
                .findByUuid(conn, p.getStringUUID())
                .map(PlayersDao.Player::subdivisionId)
                .orElse(null));
        return subId != null && ("sub:" + subId).equals(m.scopeKey());
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server == null || server.getTickCount() % 20 != 0 || active.isEmpty()) return;

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Marker>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Marker> e = it.next();
            if (e.getValue().expiresAtEpochMs() <= now) {
                Marker m = e.getValue();
                it.remove();
                FactionMarkerClearPayload clear = new FactionMarkerClearPayload(m.scope(), m.scopeKey());
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    if (canSee(p, m)) {
                        PacketDistributor.sendToPlayer(p, clear);
                    }
                }
                WarProject.LOGGER.debug("[WP Marker] expired {}/{}", m.scope(), m.scopeKey());
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            resyncTo(p);
        }
    }
}
