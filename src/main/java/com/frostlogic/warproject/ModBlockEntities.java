package com.frostlogic.warproject;

import com.frostlogic.warproject.block.SupplyCrateBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, WarProject.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SupplyCrateBlockEntity>> SUPPLY_CRATE =
            BLOCK_ENTITIES.register("supply_crate",
                    () -> BlockEntityType.Builder.of(SupplyCrateBlockEntity::new,
                            ModBlocks.SUPPLY_CRATE.get()).build(null));

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
