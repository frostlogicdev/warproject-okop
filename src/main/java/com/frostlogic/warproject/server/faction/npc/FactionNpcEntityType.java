package com.frostlogic.warproject.server.faction.npc;

import com.frostlogic.warproject.WarProject;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Deferred registration of the {@link FactionNpcEntity} entity type.
 * <p>
 * Registers a single generic entity type {@code warproject:faction_npc} that stores
 * its faction via NBT data (see {@link FactionNpcEntity#addAdditionalSaveData}).
 * <p>
 * Models and textures are provided by the resource pack bundled in the jar (see §15).
 * <p>
 * Requirements: 6.1, 6.2
 * Design: §3 — server/faction/npc/FactionNpcEntityType.java
 */
public final class FactionNpcEntityType {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, WarProject.MOD_ID);

    /**
     * The faction NPC entity type. A single type is used for both factions;
     * the specific faction is determined by the {@code FactionId} stored in NBT.
     */
    public static final Supplier<EntityType<FactionNpcEntity>> FACTION_NPC = ENTITY_TYPES.register(
            "faction_npc",
            () -> EntityType.Builder.of(FactionNpcEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F) // Same hitbox as a player/villager
                    .clientTrackingRange(10)
                    .build("faction_npc")
    );

    private FactionNpcEntityType() {
        // utility class — no instantiation
    }

    /**
     * Registers the entity type deferred register on the mod event bus.
     * Called from {@link WarProject} constructor.
     */
    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
