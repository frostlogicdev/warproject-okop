package com.frostlogic.warproject.server.region;

import com.frostlogic.warproject.attachment.FactionId;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Represents a faction base region defined as an axis-aligned bounding box (AABB)
 * within a specific dimension.
 * <p>
 * Loaded from {@code WpConfig.REGIONS_BASES} entries with format:
 * {@code "faction;dimension;minX,minY,minZ;maxX,maxY,maxZ"}
 *
 * @param faction   the faction that owns this base region
 * @param dimension the dimension this region exists in
 * @param aabb      the axis-aligned bounding box defining the region boundaries
 */
public record BaseRegion(FactionId faction, ResourceKey<Level> dimension, AABB aabb) {

    /**
     * Checks whether the given position in the given dimension is contained within this region.
     *
     * @param dim the dimension to check
     * @param x   the x coordinate
     * @param y   the y coordinate
     * @param z   the z coordinate
     * @return true if the position is inside this region
     */
    public boolean contains(ResourceKey<Level> dim, double x, double y, double z) {
        return dimension.equals(dim)
                && x >= aabb.minX && x <= aabb.maxX
                && y >= aabb.minY && y <= aabb.maxY
                && z >= aabb.minZ && z <= aabb.maxZ;
    }

    /**
     * Parses a config string into a {@link BaseRegion}.
     * <p>
     * Expected format: {@code "FACTION;dimension_id;minX,minY,minZ;maxX,maxY,maxZ"}
     * <p>
     * Example: {@code "ZARNAVIA;minecraft:overworld;100,60,100;200,120,200"}
     *
     * @param configLine the raw config line
     * @return the parsed BaseRegion
     * @throws IllegalArgumentException if the format is invalid
     */
    public static BaseRegion parse(String configLine) {
        String[] parts = configLine.split(";");
        if (parts.length != 4) {
            throw new IllegalArgumentException(
                    "Invalid region format, expected 'faction;dimension;minX,minY,minZ;maxX,maxY,maxZ' but got: " + configLine);
        }

        FactionId faction = parseFaction(parts[0].trim());
        ResourceKey<Level> dimension = parseDimension(parts[1].trim());
        double[] min = parseCoords(parts[2].trim(), configLine);
        double[] max = parseCoords(parts[3].trim(), configLine);

        AABB aabb = new AABB(min[0], min[1], min[2], max[0], max[1], max[2]);
        return new BaseRegion(faction, dimension, aabb);
    }

    private static FactionId parseFaction(String raw) {
        try {
            return FactionId.valueOf(raw.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown faction in region config: '" + raw + "'. Expected ZARNAVIA or CHERNOGRYAD.");
        }
    }

    private static ResourceKey<Level> parseDimension(String raw) {
        ResourceLocation loc = ResourceLocation.parse(raw);
        return ResourceKey.create(Registries.DIMENSION, loc);
    }

    private static double[] parseCoords(String raw, String fullLine) {
        String[] tokens = raw.split(",");
        if (tokens.length != 3) {
            throw new IllegalArgumentException(
                    "Invalid coordinates '" + raw + "' in region config: " + fullLine);
        }
        try {
            return new double[]{
                    Double.parseDouble(tokens[0].trim()),
                    Double.parseDouble(tokens[1].trim()),
                    Double.parseDouble(tokens[2].trim())
            };
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Non-numeric coordinate in '" + raw + "' in region config: " + fullLine, e);
        }
    }
}
