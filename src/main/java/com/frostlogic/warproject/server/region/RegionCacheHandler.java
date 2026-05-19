package com.frostlogic.warproject.server.region;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.WpConfig;
import com.frostlogic.warproject.attachment.RegionRef;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * Maintains the cached {@link WpAttachmentTypes#REGION} attachment for each player.
 * <p>
 * Updates occur:
 * <ul>
 *   <li>Every {@code cfg.regions.tickIntervalTicks} ticks (default 10) via {@link PlayerTickEvent.Post}</li>
 *   <li>Immediately on teleport command ({@link EntityTeleportEvent.TeleportCommand})</li>
 *   <li>Immediately on respawn ({@link PlayerEvent.PlayerRespawnEvent})</li>
 *   <li>Immediately on dimension change ({@link PlayerEvent.PlayerChangedDimensionEvent})</li>
 * </ul>
 * <p>
 * Requirements: 17.2, 17.3, 17.4
 */
@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class RegionCacheHandler {

    private static final RegionService REGION_SERVICE = new RegionService();

    private RegionCacheHandler() {
        // static event subscriber — no instantiation
    }

    /**
     * Returns the shared {@link RegionService} instance used by this handler.
     * Other subsystems (candidate rules, collaborator service) should use this
     * to query regions.
     */
    public static RegionService regionService() {
        return REGION_SERVICE;
    }

    /**
     * Initializes (loads) regions from config. Should be called on server start
     * and on {@code /wp reload}.
     */
    public static void reload() {
        REGION_SERVICE.reload();
    }

    // =========================================================================
    // Event handlers
    // =========================================================================

    /**
     * Periodic region cache update on player tick.
     * Only recalculates every {@code cfg.regions.tickIntervalTicks} ticks to reduce overhead.
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        int interval = WpConfig.REGIONS_TICK_INTERVAL_TICKS.get();
        if (interval <= 0) {
            interval = 10;
        }

        if (player.tickCount % interval != 0) {
            return;
        }

        recalculateRegion(player);
    }

    /**
     * Immediate recalculation when a player is teleported via command
     * (e.g. /tp, /wp tp, /wp tphere).
     */
    @SubscribeEvent
    public static void onTeleportCommand(EntityTeleportEvent.TeleportCommand event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // The event fires before the teleport completes, so we schedule
            // recalculation using the target position from the event.
            Vec3 targetPos = new Vec3(event.getTargetX(), event.getTargetY(), event.getTargetZ());
            updateRegionForPosition(player, targetPos);
        }
    }

    /**
     * Immediate recalculation when a player respawns (after death or end portal).
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            recalculateRegion(player);
        }
    }

    /**
     * Immediate recalculation when a player changes dimension
     * (nether portal, end portal, /execute in, etc.).
     */
    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            recalculateRegion(player);
        }
    }

    // =========================================================================
    // Core logic
    // =========================================================================

    /**
     * Recalculates the region for the player at their current position and updates
     * the {@link WpAttachmentTypes#REGION} attachment.
     */
    private static void recalculateRegion(ServerPlayer player) {
        updateRegionForPosition(player, player.position());
    }

    /**
     * Updates the REGION attachment based on the given position (which may differ
     * from the player's current position in the case of pre-teleport events).
     */
    private static void updateRegionForPosition(ServerPlayer player, Vec3 pos) {
        @Nullable BaseRegion region = REGION_SERVICE.regionAt(player.level().dimension(), pos);

        Optional<RegionRef> newRef = region != null
                ? Optional.of(new RegionRef(regionUuid(region)))
                : Optional.empty();

        Optional<RegionRef> currentRef = player.getData(WpAttachmentTypes.REGION.get());

        // Only update if the value actually changed to avoid unnecessary NBT writes
        if (!newRef.equals(currentRef)) {
            player.setData(WpAttachmentTypes.REGION.get(), newRef);
        }
    }

    /**
     * Generates a deterministic UUID for a {@link BaseRegion} based on its
     * faction, dimension, and AABB coordinates.
     * <p>
     * This allows the {@link RegionRef} attachment to reference a specific region
     * without requiring regions to have explicit UUIDs in config.
     */
    private static UUID regionUuid(BaseRegion region) {
        String identity = region.faction().name()
                + ";" + region.dimension().location().toString()
                + ";" + region.aabb().minX + "," + region.aabb().minY + "," + region.aabb().minZ
                + ";" + region.aabb().maxX + "," + region.aabb().maxY + "," + region.aabb().maxZ;
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
    }
}
