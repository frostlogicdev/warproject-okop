package com.frostlogic.warproject;

import com.frostlogic.warproject.block.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(WarProjectOkop.MOD_ID);

    // === Support Beams (3 tiers) ===
    public static final DeferredBlock<Block> WOODEN_SUPPORT_BEAM = BLOCKS.register("wooden_support_beam",
            () -> new SupportBeamBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .strength(2.0f).sound(SoundType.WOOD).noOcclusion()));
    public static final DeferredBlock<Block> IRON_SUPPORT_BEAM = BLOCKS.register("iron_support_beam",
            () -> new SupportBeamBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(4.0f, 6.0f).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> REINFORCED_SUPPORT_BEAM = BLOCKS.register("reinforced_support_beam",
            () -> new SupportBeamBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(6.0f, 10.0f).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops()));

    // === Camo Nets (3 biome variants) ===
    public static final DeferredBlock<Block> FOREST_CAMO_NET = BLOCKS.register("forest_camo_net",
            () -> new CamoNetBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT)
                    .strength(0.5f).sound(SoundType.WOOL).noOcclusion().isViewBlocking((s,l,p) -> false)));
    public static final DeferredBlock<Block> DESERT_CAMO_NET = BLOCKS.register("desert_camo_net",
            () -> new CamoNetBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SAND)
                    .strength(0.5f).sound(SoundType.WOOL).noOcclusion().isViewBlocking((s,l,p) -> false)));
    public static final DeferredBlock<Block> WINTER_CAMO_NET = BLOCKS.register("winter_camo_net",
            () -> new CamoNetBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW)
                    .strength(0.5f).sound(SoundType.WOOL).noOcclusion().isViewBlocking((s,l,p) -> false)));

    // === Sandbags ===
    public static final DeferredBlock<Block> SANDBAG = BLOCKS.register("sandbag",
            () -> new SandbagBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SAND)
                    .strength(2.5f, 8.0f).sound(SoundType.SAND)));

    // === Horizontal Covers ===
    public static final DeferredBlock<Block> WOODEN_HORIZONTAL_COVER = BLOCKS.register("wooden_horizontal_cover",
            () -> new HorizontalCoverBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .strength(2.0f).sound(SoundType.WOOD)));
    public static final DeferredBlock<Block> LOG_HORIZONTAL_COVER = BLOCKS.register("log_horizontal_cover",
            () -> new HorizontalCoverBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .strength(2.5f).sound(SoundType.WOOD)));

    // === Barbed Wire ===
    public static final DeferredBlock<Block> BARBED_WIRE = BLOCKS.register("barbed_wire",
            () -> new BarbedWireBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(1.0f).sound(SoundType.CHAIN).noOcclusion().noCollission()));

    // === Drainage Grate ===
    public static final DeferredBlock<Block> DRAINAGE_GRATE = BLOCKS.register("drainage_grate",
            () -> new DrainageGrateBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .strength(1.5f).sound(SoundType.WOOD).noOcclusion()));

    // === Firing Slot / Embrasure ===
    public static final DeferredBlock<Block> FIRING_SLOT = BLOCKS.register("firing_slot",
            () -> new FiringSlotBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                    .strength(4.0f, 8.0f).sound(SoundType.STONE).noOcclusion().requiresCorrectToolForDrops()));

    // === Trench Stairs ===
    public static final DeferredBlock<Block> TRENCH_STAIRS = BLOCKS.register("trench_stairs",
            () -> new TrenchStairsBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .strength(2.0f).sound(SoundType.WOOD)));

    // === Trench Lantern ===
    public static final DeferredBlock<Block> TRENCH_LANTERN = BLOCKS.register("trench_lantern",
            () -> new TrenchLanternBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(1.0f).sound(SoundType.LANTERN).noOcclusion().lightLevel(s -> 5)));

    // === Supply Crate ===
    public static final DeferredBlock<Block> SUPPLY_CRATE = BLOCKS.register("supply_crate",
            () -> new SupplyCrateBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .strength(2.5f).sound(SoundType.WOOD)));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
