package com.frostlogic.warproject.server.region;

import com.frostlogic.warproject.attachment.FactionId;
import net.jqwik.api.*;
import net.jqwik.api.constraints.DoubleRange;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for {@link BaseRegion}.
 * <p>
 * <b>Validates: Requirements 17.1, 17.2, 17.3</b>
 * <p>
 * Design: §12 Property 14
 */
@PropertyDefaults(tries = 100)
class RegionProperties {

    private static final ResourceKey<Level> OVERWORLD = Level.OVERWORLD;
    private static final ResourceKey<Level> NETHER = Level.NETHER;
    private static final ResourceKey<Level> END = Level.END;

    // --- Property 14a: contains(dim, x, y, z) ⇔ dim == region.dimension ∧ component-wise inequalities ---

    @Property
    void contains_iff_sameDimAndInsideBounds(
            @ForAll("regions") BaseRegion region,
            @ForAll("dimensions") ResourceKey<Level> dim,
            @ForAll @DoubleRange(min = -30_000_000, max = 30_000_000) double x,
            @ForAll @DoubleRange(min = -64, max = 320) double y,
            @ForAll @DoubleRange(min = -30_000_000, max = 30_000_000) double z
    ) {
        boolean actual = region.contains(dim, x, y, z);

        boolean sameDim = region.dimension().equals(dim);
        boolean inX = x >= region.aabb().minX && x <= region.aabb().maxX;
        boolean inY = y >= region.aabb().minY && y <= region.aabb().maxY;
        boolean inZ = z >= region.aabb().minZ && z <= region.aabb().maxZ;
        boolean expected = sameDim && inX && inY && inZ;

        assertThat(actual)
                .as("contains(%s, %.2f, %.2f, %.2f) should be %s for region dim=%s aabb=%s",
                        dim, x, y, z, expected, region.dimension(), region.aabb())
                .isEqualTo(expected);
    }

    // --- Property 14b: points outside the AABB → contains returns false ---

    @Property
    void outsideBounds_containsReturnsFalse(@ForAll("regions") BaseRegion region) {
        AABB aabb = region.aabb();
        ResourceKey<Level> dim = region.dimension();

        // Test point below minX
        assertThat(region.contains(dim, aabb.minX - 1, (aabb.minY + aabb.maxY) / 2, (aabb.minZ + aabb.maxZ) / 2))
                .as("Point below minX should not be contained").isFalse();

        // Test point above maxX
        assertThat(region.contains(dim, aabb.maxX + 1, (aabb.minY + aabb.maxY) / 2, (aabb.minZ + aabb.maxZ) / 2))
                .as("Point above maxX should not be contained").isFalse();

        // Test point below minY
        assertThat(region.contains(dim, (aabb.minX + aabb.maxX) / 2, aabb.minY - 1, (aabb.minZ + aabb.maxZ) / 2))
                .as("Point below minY should not be contained").isFalse();

        // Test point above maxY
        assertThat(region.contains(dim, (aabb.minX + aabb.maxX) / 2, aabb.maxY + 1, (aabb.minZ + aabb.maxZ) / 2))
                .as("Point above maxY should not be contained").isFalse();

        // Test point below minZ
        assertThat(region.contains(dim, (aabb.minX + aabb.maxX) / 2, (aabb.minY + aabb.maxY) / 2, aabb.minZ - 1))
                .as("Point below minZ should not be contained").isFalse();

        // Test point above maxZ
        assertThat(region.contains(dim, (aabb.minX + aabb.maxX) / 2, (aabb.minY + aabb.maxY) / 2, aabb.maxZ + 1))
                .as("Point above maxZ should not be contained").isFalse();
    }

    // --- Property 14c: wrong dimension → contains returns false regardless of coordinates ---

    @Property
    void wrongDimension_containsReturnsFalse(@ForAll("regions") BaseRegion region) {
        AABB aabb = region.aabb();
        // Pick a dimension different from the region's dimension
        ResourceKey<Level> wrongDim = region.dimension().equals(OVERWORLD) ? NETHER : OVERWORLD;

        // Use center of the AABB — guaranteed inside bounds
        double cx = (aabb.minX + aabb.maxX) / 2;
        double cy = (aabb.minY + aabb.maxY) / 2;
        double cz = (aabb.minZ + aabb.maxZ) / 2;

        assertThat(region.contains(wrongDim, cx, cy, cz))
                .as("Wrong dimension should always return false even for center point")
                .isFalse();
    }

    // --- Property 14d: parse round-trip produces correct faction, dimension, and AABB ---

    @Property
    void parseRoundTrip_producesCorrectRegion(
            @ForAll("factions") FactionId faction,
            @ForAll("dimensions") ResourceKey<Level> dim,
            @ForAll @DoubleRange(min = -10000, max = 10000) double minX,
            @ForAll @DoubleRange(min = -64, max = 320) double minY,
            @ForAll @DoubleRange(min = -10000, max = 10000) double minZ,
            @ForAll @DoubleRange(min = 1, max = 500) double sizeX,
            @ForAll @DoubleRange(min = 1, max = 200) double sizeY,
            @ForAll @DoubleRange(min = 1, max = 500) double sizeZ
    ) {
        double maxX = minX + sizeX;
        double maxY = minY + sizeY;
        double maxZ = minZ + sizeZ;

        String dimStr = dim.location().toString();
        String configLine = String.format("%s;%s;%s,%s,%s;%s,%s,%s",
                faction.name(), dimStr,
                formatCoord(minX), formatCoord(minY), formatCoord(minZ),
                formatCoord(maxX), formatCoord(maxY), formatCoord(maxZ));

        BaseRegion parsed = BaseRegion.parse(configLine);

        assertThat(parsed.faction())
                .as("Parsed faction should match")
                .isEqualTo(faction);
        assertThat(parsed.dimension())
                .as("Parsed dimension should match")
                .isEqualTo(dim);
        assertThat(parsed.aabb().minX).as("minX").isCloseTo(minX, org.assertj.core.data.Offset.offset(0.001));
        assertThat(parsed.aabb().minY).as("minY").isCloseTo(minY, org.assertj.core.data.Offset.offset(0.001));
        assertThat(parsed.aabb().minZ).as("minZ").isCloseTo(minZ, org.assertj.core.data.Offset.offset(0.001));
        assertThat(parsed.aabb().maxX).as("maxX").isCloseTo(maxX, org.assertj.core.data.Offset.offset(0.001));
        assertThat(parsed.aabb().maxY).as("maxY").isCloseTo(maxY, org.assertj.core.data.Offset.offset(0.001));
        assertThat(parsed.aabb().maxZ).as("maxZ").isCloseTo(maxZ, org.assertj.core.data.Offset.offset(0.001));
    }

    // --- Generators ---

    @Provide
    Arbitrary<BaseRegion> regions() {
        return Combinators.combine(
                factions(),
                dimensions(),
                Arbitraries.doubles().between(-10000, 10000),
                Arbitraries.doubles().between(-64, 320),
                Arbitraries.doubles().between(-10000, 10000),
                Arbitraries.doubles().between(1, 500),
                Arbitraries.doubles().between(1, 200),
                Arbitraries.doubles().between(1, 500)
        ).as((faction, dim, minX, minY, minZ, sizeX, sizeY, sizeZ) -> {
            AABB aabb = new AABB(minX, minY, minZ, minX + sizeX, minY + sizeY, minZ + sizeZ);
            return new BaseRegion(faction, dim, aabb);
        });
    }

    @Provide
    Arbitrary<FactionId> factions() {
        return Arbitraries.of(FactionId.ZARNAVIA, FactionId.CHERNOGRYAD);
    }

    @Provide
    Arbitrary<ResourceKey<Level>> dimensions() {
        return Arbitraries.of(OVERWORLD, NETHER, END);
    }

    private static String formatCoord(double value) {
        // Use enough precision to avoid floating-point round-trip issues
        return String.valueOf(value);
    }
}
