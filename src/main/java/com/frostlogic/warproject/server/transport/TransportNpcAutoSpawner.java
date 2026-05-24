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
    // Search radius for existing NPCs around the configured coordinates. Was
    // 5 blocks but that's tight: any prior server run that spawned an NPC at
    // slightly different coordinates (e.g. after the layout was edited or the
    // chunk was force-loaded with the player drifted a few blocks) would put
    // the saved NPC outside the box, so the next start spawned a fresh one
    // alongside it — visible to players as "two NPCs in one spot".
    // 16 blocks is generous without risking grabbing unrelated NPCs.
    private static final double SEARCH_RADIUS = 16.0D;

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

        // Validate that every configured vehicle item id is actually a
        // registered item. This catches stale wp-server.toml entries (e.g.
        // 'iv:tiger_2' placeholders left over from an older config) and
        // logs them so admins notice before players try to claim.
        TransportVehicleService.auditCatalog();
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
        // Both factions face south by default ("в другую сторону" request from
        // the user: Chernogryad was flipped from 0°→180° so it now matches
        // Zarnavia). If the constant changes again the existing NPC needs to
        // be reoriented too, otherwise old yaw stays pinned forever because
        // TransportNpcEntity.tick() locks head yaw to body yaw when IDLE.
        // Zarnavia: south (180°). Chernogryad: south (180°) — was 0° before,
        // flipped per user request because the counter is on the opposite side
        // of the base in the world layout.
        float targetYaw = 180.0F;
        if (!existing.isEmpty()) {
            // If multiple NPCs are present (from a prior version that used a
            // smaller search radius), keep the one closest to the configured
            // coordinates and discard the rest. Otherwise the player sees two
            // overlapping NPC models at the counter.
            TransportNpcEntity keeper = existing.get(0);
            if (existing.size() > 1) {
                double bestDistSq = Double.POSITIVE_INFINITY;
                for (TransportNpcEntity npc : existing) {
                    double dx = npc.getX() - x;
                    double dy = npc.getY() - y;
                    double dz = npc.getZ() - z;
                    double d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 < bestDistSq) {
                        bestDistSq = d2;
                        keeper = npc;
                    }
                }
                int removed = 0;
                for (TransportNpcEntity npc : existing) {
                    if (npc != keeper) {
                        npc.discard();
                        removed++;
                    }
                }
                WarProject.LOGGER.info(
                        "[WP Transport] Removed {} duplicate transport NPC(s) at {} for faction {}.",
                        removed, factionId.getSerializedName(), factionId.getSerializedName());
            }
            // Snap the survivor back to the configured coordinates so prior
            // drift (e.g. an admin nudged the entity by 1 block) is corrected.
            if (Math.abs(keeper.getX() - x) > 0.5D
                    || Math.abs(keeper.getY() - y) > 0.5D
                    || Math.abs(keeper.getZ() - z) > 0.5D) {
                keeper.moveTo(x, y, z, targetYaw, 0.0F);
                keeper.setYHeadRot(targetYaw);
                WarProject.LOGGER.info("[WP Transport] Snapped existing NPC for {} back to {}, {}, {}.",
                        factionId.getSerializedName(), x, y, z);
            } else if (Math.abs(net.minecraft.util.Mth.wrapDegrees(keeper.getYRot() - targetYaw)) > 1.0F) {
                // moveTo with the same x/y/z just re-applies yaw safely;
                // setYHeadRot pins the head so TransportNpcEntity.tick()
                // (which locks head→body when IDLE) keeps the new heading.
                keeper.moveTo(keeper.getX(), keeper.getY(), keeper.getZ(), targetYaw, 0.0F);
                keeper.setYHeadRot(targetYaw);
                WarProject.LOGGER.info("[WP Transport] Reoriented existing NPC for {} to yaw={}",
                        factionId.getSerializedName(), targetYaw);
            }
            WarProject.LOGGER.debug("[WP Transport] NPC for {} already present at {} {} {}.",
                    factionId.getSerializedName(), x, y, z);
            return;
        }

        TransportNpcEntity npc = TransportNpcEntityType.TRANSPORT_NPC.get().create(level);
        if (npc == null) {
            WarProject.LOGGER.warn("[WP Transport] Failed to create TransportNpcEntity for {}.",
                    factionId.getSerializedName());
            return;
        }

        // Per user request both bases face the same direction (south, 180°);
        // Chernogryad was flipped from 0° because in the world layout the
        // counter is now on the other side. Pinning a yaw is important
        // because TransportNpcEntity.tick() locks head yaw to body yaw
        // when IDLE.
        float yaw = targetYaw;
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
