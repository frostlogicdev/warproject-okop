package com.frostlogic.warproject.server.passport;

import com.frostlogic.warproject.WarProject;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers the DataComponentType for the new PassportData record (design §4.4).
 * <p>
 * This component type uses both a persistent Codec (for NBT/item stack serialization)
 * and a network-synchronized StreamCodec (for client sync).
 * <p>
 * Requirements: 12.x
 * Design: §4.4
 */
public final class PassportComponentTypes {

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, WarProject.MOD_ID);

    /**
     * The passport data component type, stored on PassportItem stacks.
     * Persistent via {@link PassportData#CODEC}, network-synced via {@link PassportData#STREAM_CODEC}.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PassportData>> PASSPORT_DATA =
            COMPONENTS.register("passport_data", () -> DataComponentType.<PassportData>builder()
                    .persistent(PassportData.CODEC)
                    .networkSynchronized(PassportData.STREAM_CODEC)
                    .build());

    private PassportComponentTypes() {
        // utility class
    }

    /**
     * Register the deferred register on the mod event bus.
     * Called from {@link WarProject} constructor.
     */
    public static void register(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }
}
