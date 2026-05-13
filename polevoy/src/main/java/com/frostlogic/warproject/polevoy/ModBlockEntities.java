package com.frostlogic.warproject.polevoy;

import com.frostlogic.warproject.polevoy.block.CommandTentBlockEntity;
import com.frostlogic.warproject.polevoy.block.MedicalTentBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, WarProjectPolevoy.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CommandTentBlockEntity>> COMMAND_TENT =
            BLOCK_ENTITIES.register("command_tent",
                    () -> BlockEntityType.Builder.of(CommandTentBlockEntity::new,
                            ModBlocks.COMMAND_TENT.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MedicalTentBlockEntity>> MEDICAL_TENT =
            BLOCK_ENTITIES.register("medical_tent",
                    () -> BlockEntityType.Builder.of(MedicalTentBlockEntity::new,
                            ModBlocks.MEDICAL_TENT.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
