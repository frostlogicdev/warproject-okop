package com.frostlogic.warproject;

import com.frostlogic.warproject.item.PassportData;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, WarProject.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PassportData>> PASSPORT =
            COMPONENTS.register("passport", () -> DataComponentType.<PassportData>builder()
                    .persistent(PassportData.CODEC)
                    .networkSynchronized(PassportData.STREAM_CODEC)
                    .build());

    public static void register(IEventBus bus) {
        COMPONENTS.register(bus);
    }
}
