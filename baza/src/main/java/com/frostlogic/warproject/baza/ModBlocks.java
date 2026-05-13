package com.frostlogic.warproject.baza;

import com.frostlogic.warproject.baza.block.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(WarProjectBaza.MOD_ID);

    public static final DeferredBlock<Block> MILITARY_CONCRETE = BLOCKS.register("military_concrete",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f, 30.0f).sound(SoundType.STONE).requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> REINFORCED_CONCRETE = BLOCKS.register("reinforced_concrete",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(5.0f, 60.0f).sound(SoundType.STONE).requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> CONCRETE_SLAB = BLOCKS.register("concrete_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.of()
                    .strength(3.0f, 30.0f).sound(SoundType.STONE).requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> HESCO_BARRIER = BLOCKS.register("hesco_barrier",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(4.0f, 40.0f).sound(SoundType.GRAVEL)));

    public static final DeferredBlock<Block> METAL_GATE = BLOCKS.register("metal_gate",
            () -> new MetalGateBlock(BlockBehaviour.Properties.of()
                    .strength(4.0f, 30.0f).sound(SoundType.METAL).noOcclusion()));

    public static final DeferredBlock<Block> CHECKPOINT_BARRIER = BLOCKS.register("checkpoint_barrier",
            () -> new CheckpointBarrierBlock(BlockBehaviour.Properties.of()
                    .strength(3.0f, 20.0f).sound(SoundType.METAL).noOcclusion()));

    public static final DeferredBlock<Block> TANK_HEDGEHOG = BLOCKS.register("tank_hedgehog",
            () -> new TankHedgehogBlock(BlockBehaviour.Properties.of()
                    .strength(5.0f, 50.0f).sound(SoundType.METAL).noOcclusion()));

    public static final DeferredBlock<Block> RAZOR_WIRE_FENCE = BLOCKS.register("razor_wire_fence",
            () -> new RazorWireFenceBlock(BlockBehaviour.Properties.of()
                    .strength(2.0f, 10.0f).sound(SoundType.METAL).noOcclusion()));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
