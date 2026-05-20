package com.frostlogic.warproject.server;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.server.faction.npc.FactionNpcEntity;
import com.frostlogic.warproject.server.faction.npc.FactionNpcEntityType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.List;

/**
 * Auto-spawns the playable faction recruiter NPCs on server start so the
 * onboarding choice hall always has them available without an admin running
 * {@code /wp npc create}. Idempotent: if a tagged NPC already exists at the
 * configured location it is left alone.
 */
@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class FactionNpcAutoSpawner {

    /** ResourceKey for the Multiworld "choicehall" dimension. */
    private static final ResourceKey<Level> CHOICE_HALL_DIM = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.parse("multiworld:choicehall")
    );

    // NPC coordinates in the choicehall world (from user-provided positions)
    private static final double ZARNAVIA_X = 49.5D;
    private static final double ZARNAVIA_Y = -1942.0D;
    private static final double ZARNAVIA_Z = -287.5D;

    private static final double CHERNOGRYAD_X = 53.5D;
    private static final double CHERNOGRYAD_Y = -1942.0D;
    private static final double CHERNOGRYAD_Z = -287.5D;

    /** Search radius (in blocks) for an existing tagged NPC around the target xyz. */
    private static final double SEARCH_RADIUS = 5.0D;

    private FactionNpcAutoSpawner() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ServerLevel choiceHallLevel = event.getServer().getLevel(CHOICE_HALL_DIM);
        if (choiceHallLevel == null) {
            WarProject.LOGGER.warn("[WP] FactionNpcAutoSpawner: multiworld:choicehall dimension not available; falling back to nether.");
            choiceHallLevel = event.getServer().getLevel(Level.NETHER);
        }
        if (choiceHallLevel == null) {
            WarProject.LOGGER.warn("[WP] FactionNpcAutoSpawner: no suitable dimension found; skipping faction NPC spawn.");
            return;
        }

        // Force-load the chunks we care about. ServerStartedEvent fires before
        // any chunk in the choicehall dimension is ticked, so getEntitiesOfClass
        // would otherwise skip loaded-but-not-yet-spawned entities and we'd
        // both miss legacy villagers (cleanup no-op) and re-spawn duplicate
        // FactionNpcEntities every restart. Reading the chunk at full status
        // pulls the saved entities back into the level.
        forceLoadChunk(choiceHallLevel, ZARNAVIA_X, ZARNAVIA_Z);
        forceLoadChunk(choiceHallLevel, CHERNOGRYAD_X, CHERNOGRYAD_Z);

        // Clean up legacy Villager NPCs with wp_npc_side tags
        cleanupLegacyVillagerNpcs(choiceHallLevel);

        ensureNpc(choiceHallLevel, FactionId.ZARNAVIA, ZARNAVIA_X, ZARNAVIA_Y, ZARNAVIA_Z);
        ensureNpc(choiceHallLevel, FactionId.CHERNOGRYAD, CHERNOGRYAD_X, CHERNOGRYAD_Y, CHERNOGRYAD_Z);
    }

    private static void forceLoadChunk(ServerLevel level, double x, double z) {
        int cx = ((int) Math.floor(x)) >> 4;
        int cz = ((int) Math.floor(z)) >> 4;
        level.getChunk(cx, cz);
    }

    /**
     * Removes old Villager-based faction NPCs (tagged with {@code wp_npc_side:*})
     * that were spawned by the legacy NpcHandler before the FactionNpcEntity
     * migration. These render as villagers instead of humanoids.
     * <p>
     * The search is restricted to small AABBs around the two recruiter slots
     * because that's where {@link NpcHandler#spawnSideNpcAt} historically
     * placed them; doing a full-world villager scan here would touch every
     * loaded chunk and pick up unrelated villager mobs in normal worlds.
     */
    private static void cleanupLegacyVillagerNpcs(ServerLevel level) {
        int removed = 0;
        removed += removeLegacyVillagersAt(level, ZARNAVIA_X, ZARNAVIA_Y, ZARNAVIA_Z);
        removed += removeLegacyVillagersAt(level, CHERNOGRYAD_X, CHERNOGRYAD_Y, CHERNOGRYAD_Z);
        if (removed > 0) {
            WarProject.LOGGER.info("[WP] Cleaned up {} legacy Villager faction NPCs.", removed);
        }
    }

    private static int removeLegacyVillagersAt(ServerLevel level, double x, double y, double z) {
        AABB box = new AABB(x - 8, y - 8, z - 8, x + 8, y + 8, z + 8);
        List<Villager> legacy = level.getEntitiesOfClass(Villager.class, box,
                v -> v.getTags().stream().anyMatch(t -> t.startsWith(NpcHandler.TAG_PREFIX)));
        for (Villager v : legacy) {
            v.discard();
        }
        return legacy.size();
    }

    private static void ensureNpc(ServerLevel level, FactionId factionId, double x, double y, double z) {
        if (factionId == null) {
            return;
        }

        AABB searchBox = new AABB(
                x - SEARCH_RADIUS, y - SEARCH_RADIUS, z - SEARCH_RADIUS,
                x + SEARCH_RADIUS, y + SEARCH_RADIUS, z + SEARCH_RADIUS
        );
        List<FactionNpcEntity> existing = level.getEntitiesOfClass(FactionNpcEntity.class, searchBox,
                npc -> npc.getFactionId() == factionId);
        if (!existing.isEmpty()) {
            WarProject.LOGGER.debug("[WP] Faction NPC for {} already present near {} {} {} (count={}).",
                    factionId.getSerializedName(), x, y, z, existing.size());
            return;
        }

        FactionNpcEntity npc = FactionNpcEntityType.FACTION_NPC.get().create(level);
        if (npc == null) {
            WarProject.LOGGER.warn("[WP] FactionNpcAutoSpawner: failed to create FactionNpcEntity for {}.", factionId.getSerializedName());
            return;
        }

        npc.moveTo(x, y, z, 0.0F, 0.0F);
        npc.setFactionId(factionId);

        if (level.addFreshEntity(npc)) {
            WarProject.LOGGER.info("[WP] Spawned faction NPC at {} {} {} for {}", x, y, z, factionId.getSerializedName());
        } else {
            WarProject.LOGGER.warn("[WP] FactionNpcAutoSpawner: addFreshEntity rejected NPC for {}.", factionId.getSerializedName());
        }
    }
}
