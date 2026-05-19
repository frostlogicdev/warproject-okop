package com.frostlogic.warproject.server.map;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import com.frostlogic.warproject.network.payload.s2c.AllyPositionsPayload;
import com.frostlogic.warproject.network.payload.s2c.EnemyVisiblePayload;
import com.frostlogic.warproject.network.payload.s2c.FactionBasesPayload;
import com.frostlogic.warproject.server.region.BaseRegion;
import com.frostlogic.warproject.server.region.RegionCacheHandler;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Server-side broadcaster for the JourneyMap integration.
 * <p>
 * On a periodic tick (1 Hz by default) sends each authenticated player:
 * <ul>
 *     <li>{@link AllyPositionsPayload} — every other player of the same faction
 *         in the same dimension. Always shared inside the faction.</li>
 *     <li>{@link EnemyVisiblePayload} — enemy players whose current position
 *         lies inside the receiver's faction base region. (Spotters / scouts
 *         only — outside-of-base enemies are intentionally hidden.)</li>
 * </ul>
 * On player login it sends a one-shot {@link FactionBasesPayload} with all
 * configured base regions so the JM plugin can draw coloured polygons.
 * <p>
 * The service subscribes itself to the NeoForge game event bus when
 * {@link #install()} is called (from {@code ServerEvents.onServerAboutToStart})
 * and tears its subscription down in {@link #uninstall()}. Holding a single
 * static instance keeps the subscriber stateful (we need access to {@code this})
 * while still surviving repeated single-player world reloads.
 */
public final class MapBroadcastService {

    /** Broadcast cadence — every 20 ticks ≈ 1 Hz. */
    private static final int BROADCAST_INTERVAL_TICKS = 20;

    @Nullable
    private static MapBroadcastService INSTANCE;

    private MapBroadcastService() {}

    /**
     * Installs the service: registers the singleton on the game event bus.
     * Must be called from {@code ServerAboutToStartEvent}.
     */
    public static synchronized void install() {
        if (INSTANCE != null) {
            uninstall();
        }
        INSTANCE = new MapBroadcastService();
        NeoForge.EVENT_BUS.register(INSTANCE);
        WarProject.LOGGER.debug("[WP Map] MapBroadcastService installed");
    }

    /**
     * Detaches the service from the event bus. Idempotent.
     */
    public static synchronized void uninstall() {
        if (INSTANCE != null) {
            try {
                NeoForge.EVENT_BUS.unregister(INSTANCE);
            } catch (Throwable ignored) {
                // EVENT_BUS.unregister may throw if the listener is missing on a
                // partial install. Swallow — we are tearing down anyway.
            }
            INSTANCE = null;
            WarProject.LOGGER.debug("[WP Map] MapBroadcastService uninstalled");
        }
    }

    /**
     * Periodic tick: broadcasts ally positions and visible enemies once per second.
     */
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server == null || server.getTickCount() % BROADCAST_INTERVAL_TICKS != 0) {
            return;
        }

        List<ServerPlayer> all = server.getPlayerList().getPlayers();
        if (all.isEmpty()) return;

        for (ServerPlayer receiver : all) {
            FactionId receiverFaction = factionOf(receiver);
            if (receiverFaction == null) continue; // not yet assigned — nothing to share

            // --- Allies: same faction, same dimension, excluding the receiver itself ---
            List<AllyPositionsPayload.Ally> allies = new ArrayList<>(all.size());
            for (ServerPlayer other : all) {
                if (other == receiver) continue;
                if (other.level() != receiver.level()) continue;
                FactionId otherFaction = factionOf(other);
                if (otherFaction != receiverFaction) continue;
                Vec3 p = other.position();
                allies.add(new AllyPositionsPayload.Ally(
                        other.getUUID(),
                        other.getGameProfile().getName(),
                        other.level().dimension().location().toString(),
                        (int) Math.floor(p.x),
                        (int) Math.floor(p.y),
                        (int) Math.floor(p.z)
                ));
                if (allies.size() >= AllyPositionsPayload.MAX_ALLIES) break;
            }
            PacketDistributor.sendToPlayer(receiver, new AllyPositionsPayload(allies));

            // --- Visible enemies: enemy faction players whose current position
            //     lies inside the RECEIVER's faction base region. Limits the
            //     "spy on enemy movement" exploit to defenders inside the wall. ---
            List<EnemyVisiblePayload.Enemy> enemies = new ArrayList<>();
            for (ServerPlayer other : all) {
                if (other == receiver) continue;
                if (other.level() != receiver.level()) continue;
                FactionId otherFaction = factionOf(other);
                if (otherFaction == null || otherFaction == receiverFaction) continue;
                BaseRegion region = RegionCacheHandler.regionService()
                        .regionAt(other.level().dimension(), other.position());
                if (region == null || region.faction() != receiverFaction) continue;
                Vec3 p = other.position();
                enemies.add(new EnemyVisiblePayload.Enemy(
                        other.getUUID(),
                        other.getGameProfile().getName(),
                        other.level().dimension().location().toString(),
                        (int) Math.floor(p.x),
                        (int) Math.floor(p.y),
                        (int) Math.floor(p.z)
                ));
                if (enemies.size() >= EnemyVisiblePayload.MAX_ENEMIES) break;
            }
            PacketDistributor.sendToPlayer(receiver, new EnemyVisiblePayload(enemies));
        }
    }

    /**
     * On login, send the full set of faction-base regions so the JM plugin
     * can paint persistent coloured polygons immediately.
     */
    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        List<BaseRegion> regions = RegionCacheHandler.regionService().allRegions();
        List<FactionBasesPayload.Base> bases = new ArrayList<>(regions.size());
        for (BaseRegion r : regions) {
            ResourceKey<Level> dim = r.dimension();
            AABB box = r.aabb();
            bases.add(new FactionBasesPayload.Base(
                    r.faction(),
                    dim.location().toString(),
                    (int) Math.floor(box.minX),
                    (int) Math.floor(box.minY),
                    (int) Math.floor(box.minZ),
                    (int) Math.ceil(box.maxX),
                    (int) Math.ceil(box.maxY),
                    (int) Math.ceil(box.maxZ)
            ));
        }
        PacketDistributor.sendToPlayer(player, new FactionBasesPayload(bases));
        WarProject.LOGGER.debug("[WP Map] Sent {} base region(s) to {}", bases.size(), player.getGameProfile().getName());
    }

    @Nullable
    private static FactionId factionOf(ServerPlayer p) {
        Optional<FactionId> opt = p.getData(WpAttachmentTypes.FACTION.get());
        return opt.orElse(null);
    }
}
