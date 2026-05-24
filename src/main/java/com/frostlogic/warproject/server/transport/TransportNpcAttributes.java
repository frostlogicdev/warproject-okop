package com.frostlogic.warproject.server.transport;

import com.frostlogic.warproject.WarProject;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * Registers default attributes for {@link TransportNpcEntity} on the MOD event
 * bus. Without this registration the entity would crash on spawn with
 * "Entity does not have attribute supplier".
 */
@EventBusSubscriber(modid = WarProject.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class TransportNpcAttributes {

    private TransportNpcAttributes() {
    }

    @SubscribeEvent
    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(
                TransportNpcEntityType.TRANSPORT_NPC.get(),
                Mob.createMobAttributes().build()
        );
    }
}
