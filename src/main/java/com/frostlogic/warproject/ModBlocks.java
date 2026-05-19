package com.frostlogic.warproject;

import com.frostlogic.warproject.block.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(WarProject.MOD_ID);

    // ==================== OKOP (Trench Fortification) ====================
    public static final DeferredBlock<Block> WOODEN_SUPPORT_BEAM = BLOCKS.register("wooden_support_beam",
            () -> new SupportBeamBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .strength(2.0f).sound(SoundType.WOOD).noOcclusion()));
    public static final DeferredBlock<Block> IRON_SUPPORT_BEAM = BLOCKS.register("iron_support_beam",
            () -> new SupportBeamBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(4.0f, 6.0f).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> REINFORCED_SUPPORT_BEAM = BLOCKS.register("reinforced_support_beam",
            () -> new SupportBeamBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(6.0f, 10.0f).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> FOREST_CAMO_NET = BLOCKS.register("forest_camo_net",
            () -> new CamoNetBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT)
                    .strength(0.5f).sound(SoundType.WOOL).noOcclusion().isViewBlocking((s,l,p) -> false)));
    public static final DeferredBlock<Block> DESERT_CAMO_NET = BLOCKS.register("desert_camo_net",
            () -> new CamoNetBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SAND)
                    .strength(0.5f).sound(SoundType.WOOL).noOcclusion().isViewBlocking((s,l,p) -> false)));
    public static final DeferredBlock<Block> WINTER_CAMO_NET = BLOCKS.register("winter_camo_net",
            () -> new CamoNetBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW)
                    .strength(0.5f).sound(SoundType.WOOL).noOcclusion().isViewBlocking((s,l,p) -> false)));
    public static final DeferredBlock<Block> SANDBAG = BLOCKS.register("sandbag",
            () -> new SandbagBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SAND)
                    .strength(2.5f, 8.0f).sound(SoundType.SAND)));
    public static final DeferredBlock<Block> WOODEN_HORIZONTAL_COVER = BLOCKS.register("wooden_horizontal_cover",
            () -> new HorizontalCoverBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .strength(2.0f).sound(SoundType.WOOD).noOcclusion()));
    public static final DeferredBlock<Block> LOG_HORIZONTAL_COVER = BLOCKS.register("log_horizontal_cover",
            () -> new HorizontalCoverBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .strength(2.5f).sound(SoundType.WOOD).noOcclusion()));
    public static final DeferredBlock<Block> BARBED_WIRE = BLOCKS.register("barbed_wire",
            () -> new BarbedWireBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(1.0f).sound(SoundType.CHAIN).noOcclusion().noCollission()));
    public static final DeferredBlock<Block> DRAINAGE_GRATE = BLOCKS.register("drainage_grate",
            () -> new DrainageGrateBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .strength(1.5f).sound(SoundType.WOOD).noOcclusion()));
    public static final DeferredBlock<Block> FIRING_SLOT = BLOCKS.register("firing_slot",
            () -> new FiringSlotBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                    .strength(4.0f, 8.0f).sound(SoundType.STONE).noOcclusion().requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> TRENCH_STAIRS = BLOCKS.register("trench_stairs",
            () -> new TrenchStairsBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .strength(2.0f).sound(SoundType.WOOD)));
    public static final DeferredBlock<Block> TRENCH_LANTERN = BLOCKS.register("trench_lantern",
            () -> new TrenchLanternBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(1.0f).sound(SoundType.LANTERN).noOcclusion().lightLevel(s -> 14)));
    public static final DeferredBlock<Block> SUPPLY_CRATE = BLOCKS.register("supply_crate",
            () -> new SupplyCrateBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .strength(2.5f).sound(SoundType.WOOD)));

    /**
     * Garage chandelier — simple olive-drab metal pendant. Lit state pumps
     * out the same brightness as a campfire (light=14) so a 5×5 garage is
     * fully illuminated by a single ceiling light. Off → light=0.
     */
    public static final DeferredBlock<Block> GARAGE_CHANDELIER = BLOCKS.register("garage_chandelier",
            () -> new CeilingLightBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(1.5f)
                            .sound(SoundType.LANTERN)
                            .noOcclusion()
                            .lightLevel(s -> s.getValue(CeilingLightBlock.LIT) ? 14 : 0),
                    // Outline matches the wide reflector dome (1..15) in the model.
                    Block.box(1, 3, 1, 15, 16, 15)));

    /**
     * Mess-hall chandelier — brass cross-frame with four candle cups. Lit
     * state outputs full daylight (light=15) so a 7×7 dining hall is bright
     * even at night. Off → light=0.
     * <p>
     * The visual model extends BEYOND the placed block (cross arms reach to
     * x=-4..20 and z=-4..20) so the chandelier appears to span 3 blocks
     * wide. The hitbox itself stays inside the placed block — the geometry
     * sticking out into neighbours is purely cosmetic and won't block
     * placement of adjacent blocks.
     */
    public static final DeferredBlock<Block> MESS_CHANDELIER = BLOCKS.register("mess_chandelier",
            () -> new CeilingLightBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.GOLD)
                            .strength(2.0f)
                            .sound(SoundType.LANTERN)
                            .noOcclusion()
                            .lightLevel(s -> s.getValue(CeilingLightBlock.LIT) ? 15 : 0),
                    Block.box(4, 4, 4, 12, 16, 12)));

    // ==================== POLEVOY (Field Camp) ====================
    public static final DeferredBlock<Block> SMALL_TENT = BLOCKS.register("small_tent",
            () -> new SmallTentBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOL)
                    .strength(1.0f).sound(SoundType.WOOL).noOcclusion()));
    public static final DeferredBlock<Block> MEDICAL_TENT = BLOCKS.register("medical_tent",
            () -> new MedicalTentBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOL)
                    .strength(1.0f).sound(SoundType.WOOL).noOcclusion()));
    public static final DeferredBlock<Block> FIELD_KITCHEN = BLOCKS.register("field_kitchen",
            () -> new FieldEquipmentBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(2.5f).sound(SoundType.METAL).noOcclusion(),
                    Block.box(1, 0, 1, 15, 13, 15)));
    public static final DeferredBlock<Block> FIELD_RADIO = BLOCKS.register("field_radio",
            () -> new FieldEquipmentBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(1.5f).sound(SoundType.METAL).noOcclusion(),
                    Block.box(3, 0, 3, 13, 10, 13)));
    public static final DeferredBlock<Block> FIELD_SPOTLIGHT = BLOCKS.register("field_spotlight",
            () -> new FieldEquipmentBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(2.0f).sound(SoundType.METAL).noOcclusion().lightLevel(s -> 10),
                    Block.box(3, 0, 3, 13, 14, 13)));
    public static final DeferredBlock<Block> FIRST_AID_KIT = BLOCKS.register("first_aid_kit",
            () -> new FirstAidKitBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED)
                    .strength(0.5f).sound(SoundType.WOOL).noOcclusion()));
    public static final DeferredBlock<Block> GENERATOR = BLOCKS.register("generator",
            () -> new FieldEquipmentBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops(),
                    Block.box(1, 0, 1, 15, 12, 15)));

    // ==================== BAZA (Military Base) ====================
    public static final DeferredBlock<Block> MILITARY_CONCRETE = BLOCKS.register("military_concrete",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                    .strength(5.0f, 30.0f).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> REINFORCED_CONCRETE = BLOCKS.register("reinforced_concrete",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                    .strength(8.0f, 60.0f).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> CONCRETE_SLAB = BLOCKS.register("concrete_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                    .strength(5.0f, 30.0f).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> HESCO_BARRIER = BLOCKS.register("hesco_barrier",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.SAND)
                    .strength(4.0f, 20.0f).sound(SoundType.SAND)));
    public static final DeferredBlock<Block> METAL_GATE = BLOCKS.register("metal_gate",
            () -> new MetalGateBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(5.0f, 15.0f).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> CHECKPOINT_BARRIER = BLOCKS.register("checkpoint_barrier",
            () -> new CheckpointBarrierBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED)
                    .strength(3.0f, 10.0f).sound(SoundType.METAL).noOcclusion()));
    public static final DeferredBlock<Block> TANK_HEDGEHOG = BLOCKS.register("tank_hedgehog",
            () -> new TankHedgehogBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(6.0f, 20.0f).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> RAZOR_WIRE_FENCE = BLOCKS.register("razor_wire_fence",
            () -> new RazorWireFenceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f).sound(SoundType.CHAIN).noOcclusion()));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
