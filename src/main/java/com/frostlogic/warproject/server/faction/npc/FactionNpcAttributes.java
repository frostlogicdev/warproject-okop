package com.frostlogic.warproject.server.faction.npc;

import com.frostlogic.warproject.WarProject;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * Registers default attributes for {@link FactionNpcEntity} via the
 * {@link EntityAttributeCreationEvent} on the MOD event bus.
 * <p>
 * Without this registration, spawning the entity would crash with
 * "Entity does not have attribute supplier".
 * <p>
 * Requirements: 6.1, 6.2
 * Design: §3
 */
@EventBusSubscriber(modid = WarProject.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class FactionNpcAttributes {

    private FactionNpcAttributes() {
        // static event subscriber — no instantiation
    }

    @SubscribeEvent
    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(
                FactionNpcEntityType.FACTION_NPC.get(),
                Mob.createMobAttributes().build()
        );
    }
}
