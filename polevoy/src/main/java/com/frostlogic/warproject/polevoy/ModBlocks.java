package com.frostlogic.warproject.polevoy;

import com.frostlogic.warproject.polevoy.block.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(WarProjectPolevoy.MOD_ID);

    public static final DeferredBlock<Block> SMALL_TENT = BLOCKS.register("small_tent",
            () -> new SmallTentBlock(BlockBehaviour.Properties.of()
                    .strength(1.0f).sound(SoundType.WOOL).noOcclusion()));

    public static final DeferredBlock<Block> COMMAND_TENT = BLOCKS.register("command_tent",
            () -> new CommandTentBlock(BlockBehaviour.Properties.of()
                    .strength(1.5f).sound(SoundType.WOOL).noOcclusion()));

    public static final DeferredBlock<Block> MEDICAL_TENT = BLOCKS.register("medical_tent",
            () -> new MedicalTentBlock(BlockBehaviour.Properties.of()
                    .strength(1.0f).sound(SoundType.WOOL).noOcclusion()));

    public static final DeferredBlock<Block> FIELD_KITCHEN = BLOCKS.register("field_kitchen",
            () -> new FieldKitchenBlock(BlockBehaviour.Properties.of()
                    .strength(2.0f).sound(SoundType.METAL).noOcclusion()));

    public static final DeferredBlock<Block> FIRST_AID_KIT = BLOCKS.register("first_aid_kit",
            () -> new FirstAidKitBlock(BlockBehaviour.Properties.of()
                    .strength(0.5f).sound(SoundType.WOOL).noOcclusion()));

    public static final DeferredBlock<Block> FIELD_RADIO = BLOCKS.register("field_radio",
            () -> new FieldRadioBlock(BlockBehaviour.Properties.of()
                    .strength(1.5f).sound(SoundType.METAL).noOcclusion()));

    public static final DeferredBlock<Block> FIELD_SPOTLIGHT = BLOCKS.register("field_spotlight",
            () -> new FieldSpotlightBlock(BlockBehaviour.Properties.of()
                    .strength(2.0f).sound(SoundType.METAL).noOcclusion()
                    .lightLevel(state -> 15)));

    public static final DeferredBlock<Block> GENERATOR = BLOCKS.register("generator",
            () -> new GeneratorBlock(BlockBehaviour.Properties.of()
                    .strength(3.0f).sound(SoundType.METAL)));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
