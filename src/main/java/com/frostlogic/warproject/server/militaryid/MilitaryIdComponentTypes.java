package com.frostlogic.warproject.server.militaryid;

import com.frostlogic.warproject.WarProject;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers the DataComponentType for the MilitaryIdData record.
 * <p>
 * This component type uses both a persistent Codec (for NBT/item stack serialization)
 * and a network-synchronized StreamCodec (for client sync).
 * <p>
 * Requirements: 4.2, 4.3, 4.4
 * Design: §2 Military ID System — MilitaryIdComponentTypes
 */
public final class MilitaryIdComponentTypes {

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, WarProject.MOD_ID);

    /**
     * The military ID data component type, stored on Military ID card item stacks.
     * Persistent via {@link MilitaryIdData#CODEC}, network-synced via {@link MilitaryIdData#STREAM_CODEC}.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MilitaryIdData>> MILITARY_ID_DATA =
            COMPONENTS.register("military_id_data", () -> DataComponentType.<MilitaryIdData>builder()
                    .persistent(MilitaryIdData.CODEC)
                    .networkSynchronized(MilitaryIdData.STREAM_CODEC)
                    .build());

    private MilitaryIdComponentTypes() {
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
