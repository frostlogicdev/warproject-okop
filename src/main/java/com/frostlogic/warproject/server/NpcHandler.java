package com.frostlogic.warproject.server;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.server.faction.npc.FactionNpcEntity;
import com.frostlogic.warproject.server.faction.npc.FactionNpcEntityType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Legacy NPC handler — keeps the {@code /wp npc create|remove} commands and
 * the {@code wp_npc_side:*} tag working, but now spawns the modern
 * {@link FactionNpcEntity} instead of vanilla villagers, and aggressively
 * discards any leftover tagged villagers from previous installs.
 *
 * <p>Right-click handling for FactionNpcEntity lives in
 * {@link com.frostlogic.warproject.server.faction.FactionNpcInteractHandler}
 * — the legacy click flow that used to live here is gone with the villagers.
 */
@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class NpcHandler {
    public static final String TAG_PREFIX = "wp_npc_side:";

    private NpcHandler() {
    }

    public static boolean spawnSideNpc(ServerPlayer player, Faction faction) {
        return spawnSideNpcAt(player, faction, player.getX(), player.getY(), player.getZ());
    }

    /**
     * Spawns a {@link FactionNpcEntity} for the given faction at the supplied
     * coordinates. Replaces the old villager-based implementation so
     * {@code /wp npc create} produces the right entity type going forward.
     */
    public static boolean spawnSideNpcAt(ServerPlayer player, Faction faction, double x, double y, double z) {
        if (faction == null || !faction.isPlayable()) {
            return false;
        }
        FactionId factionId = mapFactionId(faction);
        if (factionId == null) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        FactionNpcEntity npc = FactionNpcEntityType.FACTION_NPC.get().create(level);
        if (npc == null) {
            return false;
        }
        npc.moveTo(x, y, z, player.getYRot(), 0f);
        npc.setFactionId(factionId);
        return level.addFreshEntity(npc);
    }

    /**
     * Removes the closest WarProject recruiter NPCs to the player within 5
     * blocks. Matches both the modern {@link FactionNpcEntity} and any legacy
     * villagers still carrying the {@code wp_npc_side:*} tag, so the command
     * reliably erases recruiters regardless of which install spawned them.
     *
     * @return the total number of removed entities (modern + legacy)
     */
    public static int removeNearestSideNpc(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        AABB box = player.getBoundingBox().inflate(5.0D);

        List<FactionNpcEntity> modern = level.getEntitiesOfClass(FactionNpcEntity.class, box, e -> true);
        for (FactionNpcEntity npc : modern) {
            npc.discard();
        }

        List<Villager> legacy = level.getEntitiesOfClass(Villager.class, box, NpcHandler::hasSideTag);
        for (Villager villager : legacy) {
            villager.discard();
        }

        return modern.size() + legacy.size();
    }

    /**
     * Discards any villager carrying the {@code wp_npc_side:*} tag the moment
     * its chunk loads. This catches recruiter villagers that were saved into
     * world regions before the FactionNpcEntity migration and would otherwise
     * pop back into existence whenever a player walked into their chunk.
     *
     * <p>Modern {@link FactionNpcEntity} instances are left alone — they own
     * the {@code wp_npc_side:*} role now.
     */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.isNewChunk()) {
            return;
        }
        if (!(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        if (!(chunk.getLevel() instanceof ServerLevel)) {
            return;
        }

        // Iterate via getBlockEntities? No — we need entities, and chunk
        // entities are stored in the persistent entity manager. Querying the
        // level by AABB of this chunk's bounds is the supported way.
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        AABB chunkBox = new AABB(minX, chunk.getMinBuildHeight(), minZ,
                minX + 16, chunk.getMaxBuildHeight(), minZ + 16);

        ServerLevel level = (ServerLevel) chunk.getLevel();
        List<Villager> tagged = level.getEntitiesOfClass(Villager.class, chunkBox, NpcHandler::hasSideTag);
        if (tagged.isEmpty()) {
            return;
        }
        List<String> killed = new ArrayList<>(tagged.size());
        for (Villager v : tagged) {
            killed.add(v.getStringUUID());
            v.discard();
        }
        WarProject.LOGGER.info("[WP] Removed {} legacy recruiter villager(s) on chunk load at ({}, {}): {}",
                killed.size(), chunk.getPos().x, chunk.getPos().z, killed);
    }

    private static boolean hasSideTag(Villager villager) {
        for (String tag : villager.getTags()) {
            if (tag.startsWith(TAG_PREFIX)) {
                return true;
            }
        }
        return false;
    }

    private static FactionId mapFactionId(Faction faction) {
        return switch (faction) {
            case ZARNAVIA -> FactionId.ZARNAVIA;
            case CHERNOGRYAD -> FactionId.CHERNOGRYAD;
            default -> null;
        };
    }
}
