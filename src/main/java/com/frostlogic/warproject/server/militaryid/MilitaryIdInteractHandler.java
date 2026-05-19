package com.frostlogic.warproject.server.militaryid;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.network.payload.s2c.MilitaryIdSnapshotPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Handles right-click interaction on items with a {@link MilitaryIdData} component.
 * <p>
 * When a player right-clicks while holding an item that has the Military ID data component,
 * this handler sends a {@link MilitaryIdSnapshotPayload} to the client, which opens the
 * {@code MilitaryIdScreen} for display.
 * <p>
 * If the item has no {@link MilitaryIdData} component, the interaction is ignored.
 * <p>
 * Requirements: 6.1, 6.6
 * Design: §2 Military ID System
 */
@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class MilitaryIdInteractHandler {

    private MilitaryIdInteractHandler() {
        // static handlers only
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide()) {
            return;
        }

        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) {
            return;
        }

        ItemStack stack = serverPlayer.getItemInHand(event.getHand());
        if (stack.isEmpty()) {
            return;
        }

        MilitaryIdData data = stack.get(MilitaryIdComponentTypes.MILITARY_ID_DATA.get());
        if (data == null) {
            return;
        }

        // Send the Military ID snapshot to the client for display
        PacketDistributor.sendToPlayer(serverPlayer, new MilitaryIdSnapshotPayload(data));
    }
}
