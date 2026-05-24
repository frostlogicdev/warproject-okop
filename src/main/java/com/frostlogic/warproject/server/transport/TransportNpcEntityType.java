package com.frostlogic.warproject.server.transport;

import com.frostlogic.warproject.WarProject;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Deferred registration of the {@link TransportNpcEntity} entity type.
 * <p>
 * Registers a single generic type {@code warproject:transport_npc} that stores
 * the represented {@link com.frostlogic.warproject.attachment.FactionId} in NBT,
 * mirroring the pattern used by
 * {@link com.frostlogic.warproject.server.faction.npc.FactionNpcEntityType}.
 */
public final class TransportNpcEntityType {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, WarProject.MOD_ID);

    /**
     * The transport NPC entity type. A single type is used for both factions;
     * the specific faction is determined by the {@code FactionId} stored in NBT.
     */
    public static final Supplier<EntityType<TransportNpcEntity>> TRANSPORT_NPC = ENTITY_TYPES.register(
            "transport_npc",
            () -> EntityType.Builder.of(TransportNpcEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build("transport_npc")
    );

    private TransportNpcEntityType() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
