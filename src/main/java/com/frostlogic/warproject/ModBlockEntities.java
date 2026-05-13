package com.frostlogic.warproject;

import com.frostlogic.warproject.block.SupplyCrateBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, WarProjectOkop.MOD_ID);

    public static final RegistryObject<BlockEntityType<SupplyCrateBlockEntity>> SUPPLY_CRATE =
            BLOCK_ENTITIES.register("supply_crate",
                    () -> BlockEntityType.Builder.of(SupplyCrateBlockEntity::new,
                            ModBlocks.SUPPLY_CRATE.get()).build(null));

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
