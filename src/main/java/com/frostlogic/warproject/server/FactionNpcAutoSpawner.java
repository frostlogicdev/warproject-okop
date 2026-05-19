package com.frostlogic.warproject.server;

import com.frostlogic.warproject.WarProject;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
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

    private static final double ZARNAVIA_X = 34.5D;
    private static final double ZARNAVIA_Y = 66.0D;
    private static final double ZARNAVIA_Z = 31.5D;

    private static final double CHERNOGRYAD_X = 37.5D;
    private static final double CHERNOGRYAD_Y = 66.0D;
    private static final double CHERNOGRYAD_Z = 31.5D;

    /** Search radius (in blocks) for an existing tagged NPC around the target xyz. */
    private static final double SEARCH_RADIUS = 5.0D;

    private FactionNpcAutoSpawner() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ServerLevel netherLevel = event.getServer().getLevel(Level.NETHER);
        if (netherLevel == null) {
            WarProject.LOGGER.warn("[WP] FactionNpcAutoSpawner: nether dimension not available; skipping faction NPC spawn.");
            return;
        }

        ensureNpc(netherLevel, Faction.ZARNAVIA, ZARNAVIA_X, ZARNAVIA_Y, ZARNAVIA_Z);
        ensureNpc(netherLevel, Faction.CHERNOGRYAD, CHERNOGRYAD_X, CHERNOGRYAD_Y, CHERNOGRYAD_Z);
    }

    private static void ensureNpc(ServerLevel level, Faction faction, double x, double y, double z) {
        if (faction == null || !faction.isPlayable()) {
            return;
        }

        String tag = NpcHandler.TAG_PREFIX + faction.id();
        AABB searchBox = new AABB(
                x - SEARCH_RADIUS, y - SEARCH_RADIUS, z - SEARCH_RADIUS,
                x + SEARCH_RADIUS, y + SEARCH_RADIUS, z + SEARCH_RADIUS
        );
        List<Villager> existing = level.getEntitiesOfClass(Villager.class, searchBox,
                v -> v.getTags().contains(tag));
        if (!existing.isEmpty()) {
            WarProject.LOGGER.debug("[WP] Faction NPC for {} already present near {} {} {} (count={}).",
                    faction.displayName(), x, y, z, existing.size());
            return;
        }

        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) {
            WarProject.LOGGER.warn("[WP] FactionNpcAutoSpawner: failed to create Villager entity for {}.", faction.displayName());
            return;
        }

        villager.moveTo(x, y, z, 0.0F, 0.0F);
        villager.setNoAi(true);
        villager.setInvulnerable(true);
        villager.setPersistenceRequired();
        villager.setSilent(true);
        villager.setCustomName(Component.literal("Вербовщик " + faction.displayName()).withStyle(faction.color()));
        villager.setCustomNameVisible(true);
        villager.addTag(tag);

        if (level.addFreshEntity(villager)) {
            WarProject.LOGGER.info("[WP] Spawned faction NPC at {} {} {} for {}", x, y, z, faction.displayName());
        } else {
            WarProject.LOGGER.warn("[WP] FactionNpcAutoSpawner: addFreshEntity rejected NPC for {}.", faction.displayName());
        }
    }
}
