package com.frostlogic.warproject.server.transport;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.attachment.FactionId;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.List;

/**
 * Auto-spawns the transport-technician NPCs at both faction bases on server
 * start. Idempotent — if a transport NPC for the same faction is already
 * within {@link #SEARCH_RADIUS} blocks of the configured coordinates it is
 * left alone, so restarts don't pile up duplicates.
 * <p>
 * Coordinates are hard-coded per requirement from the user:
 * <ul>
 *   <li>Zarnavia base: (2070.521, -25, 2519.540) overworld</li>
 *   <li>Chernogryad base: (2731.312, -29, 1331.040) overworld</li>
 * </ul>
 */
@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class TransportNpcAutoSpawner {

    private static final double ZARNAVIA_X = 2070.521D;
    private static final double ZARNAVIA_Y = -25.0D;
    private static final double ZARNAVIA_Z = 2519.540D;

    private static final double CHERNOGRYAD_X = 2731.312D;
    private static final double CHERNOGRYAD_Y = -29.0D;
    private static final double CHERNOGRYAD_Z = 1331.040D;

    /** Search radius (in blocks) for an existing transport NPC around the target xyz. */
    private static final double SEARCH_RADIUS = 5.0D;

    private TransportNpcAutoSpawner() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ServerLevel overworld = event.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            WarProject.LOGGER.warn("[WP Transport] Overworld not available; skipping transport NPC spawn.");
            return;
        }

        // Force-load the chunks so getEntitiesOfClass actually sees previously
        // saved NPCs (mirrors FactionNpcAutoSpawner behaviour).
        forceLoadChunk(overworld, ZARNAVIA_X, ZARNAVIA_Z);
        forceLoadChunk(overworld, CHERNOGRYAD_X, CHERNOGRYAD_Z);

        ensureNpc(overworld, FactionId.ZARNAVIA, ZARNAVIA_X, ZARNAVIA_Y, ZARNAVIA_Z);
        ensureNpc(overworld, FactionId.CHERNOGRYAD, CHERNOGRYAD_X, CHERNOGRYAD_Y, CHERNOGRYAD_Z);
    }

    private static void forceLoadChunk(ServerLevel level, double x, double z) {
        int cx = ((int) Math.floor(x)) >> 4;
        int cz = ((int) Math.floor(z)) >> 4;
        level.getChunk(cx, cz);
    }

    private static void ensureNpc(ServerLevel level, FactionId factionId, double x, double y, double z) {
        AABB searchBox = new AABB(
                x - SEARCH_RADIUS, y - SEARCH_RADIUS, z - SEARCH_RADIUS,
                x + SEARCH_RADIUS, y + SEARCH_RADIUS, z + SEARCH_RADIUS
        );
        List<TransportNpcEntity> existing = level.getEntitiesOfClass(TransportNpcEntity.class, searchBox,
                npc -> npc.getFactionId() == factionId);
        if (!existing.isEmpty()) {
            WarProject.LOGGER.debug("[WP Transport] NPC for {} already present at {} {} {} (count={}).",
                    factionId.getSerializedName(), x, y, z, existing.size());
            return;
        }

        TransportNpcEntity npc = TransportNpcEntityType.TRANSPORT_NPC.get().create(level);
        if (npc == null) {
            WarProject.LOGGER.warn("[WP Transport] Failed to create TransportNpcEntity for {}.",
                    factionId.getSerializedName());
            return;
        }

        // Face the appropriate "towards player" direction. We use 180° (south)
        // for Zarnavia and 0° (north) for Chernogryad as defaults — admins can
        // re-orient via /summon if the bases are laid out differently. Pinning
        // a yaw is important because TransportNpcEntity.tick() locks the head
        // yaw to body yaw when IDLE, so the NPC will keep facing this direction.
        float yaw = factionId == FactionId.CHERNOGRYAD ? 0.0F : 180.0F;
        npc.moveTo(x, y, z, yaw, 0.0F);
        npc.setYHeadRot(yaw);
        npc.setFactionId(factionId);

        if (level.addFreshEntity(npc)) {
            WarProject.LOGGER.info("[WP Transport] Spawned transport NPC at {} {} {} for {}",
                    x, y, z, factionId.getSerializedName());
        } else {
            WarProject.LOGGER.warn("[WP Transport] addFreshEntity rejected NPC for {}.",
                    factionId.getSerializedName());
        }
    }
}
