package com.frostlogic.warproject.server.region;

import com.frostlogic.warproject.WpConfig;
import com.frostlogic.warproject.WarProject;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for loading and querying faction base regions.
 * <p>
 * Regions are loaded from {@link WpConfig#REGIONS_BASES} on startup and
 * reloaded when {@code /wp reload} is executed.
 * <p>
 * Lookup is O(B) where B is the number of configured regions (typically ≤ 4).
 */
public final class RegionService {

    private volatile Map<ResourceKey<Level>, List<BaseRegion>> regionsByDim = Collections.emptyMap();
    private volatile List<BaseRegion> allRegions = Collections.emptyList();

    /**
     * Loads (or reloads) regions from the current config values.
     * Called on server startup and on {@code /wp reload}.
     */
    public void reload() {
        List<? extends String> rawEntries = WpConfig.REGIONS_BASES.get();
        Map<ResourceKey<Level>, List<BaseRegion>> newMap = new HashMap<>();
        List<BaseRegion> newAll = new ArrayList<>();

        for (String entry : rawEntries) {
            try {
                BaseRegion region = BaseRegion.parse(entry);
                newMap.computeIfAbsent(region.dimension(), k -> new ArrayList<>()).add(region);
                newAll.add(region);
            } catch (IllegalArgumentException e) {
                WarProject.LOGGER.error("[WP Region] Failed to parse region config entry '{}': {}", entry, e.getMessage());
            }
        }

        // Make lists unmodifiable for thread safety
        newMap.replaceAll((k, v) -> Collections.unmodifiableList(v));
        this.regionsByDim = Collections.unmodifiableMap(newMap);
        this.allRegions = Collections.unmodifiableList(newAll);

        WarProject.LOGGER.info("[WP Region] Loaded {} base region(s) across {} dimension(s)",
                newAll.size(), newMap.size());
    }

    /**
     * Finds the first region that contains the given position in the given dimension.
     *
     * @param dim the dimension to check
     * @param pos the position to check
     * @return the containing {@link BaseRegion}, or {@code null} if the position is not in any region
     */
    @Nullable
    public BaseRegion regionAt(ResourceKey<Level> dim, Vec3 pos) {
        List<BaseRegion> regions = regionsByDim.get(dim);
        if (regions == null || regions.isEmpty()) {
            return null;
        }
        for (BaseRegion r : regions) {
            if (r.contains(dim, pos.x, pos.y, pos.z)) {
                return r;
            }
        }
        return null;
    }

    /**
     * Returns all loaded regions (unmodifiable).
     */
    public List<BaseRegion> allRegions() {
        return allRegions;
    }

    /**
     * Returns the regions grouped by dimension (unmodifiable).
     */
    public Map<ResourceKey<Level>, List<BaseRegion>> regionsByDimension() {
        return regionsByDim;
    }
}
