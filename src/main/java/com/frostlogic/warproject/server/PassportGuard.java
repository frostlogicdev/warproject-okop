package com.frostlogic.warproject.server;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.item.PassportItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Iterator;

@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class PassportGuard {
    private PassportGuard() {
    }

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = event.getEntity().getItem();
        if (!PassportItem.isPassport(stack)) {
            return;
        }
        event.setCanceled(true);
        ItemStack copy = stack.copy();
        if (!player.getInventory().add(copy)) {
            for (int slot = 9; slot <= 35; slot++) {
                if (player.getInventory().getItem(slot).isEmpty()) {
                    player.getInventory().setItem(slot, copy);
                    break;
                }
            }
        }
        event.getEntity().discard();
        player.sendSystemMessage(Component.literal("[WP] Этот документ нельзя выбросить.").withStyle(ChatFormatting.RED));
    }

    @SubscribeEvent
    public static void onPickup(ItemEntityPickupEvent.Pre event) {
        ItemEntity itemEntity = event.getItemEntity();
        if (itemEntity == null) {
            return;
        }
        ItemStack stack = itemEntity.getItem();
        if (!PassportItem.isPassport(stack)) {
            return;
        }
        if (!(event.getPlayer() instanceof ServerPlayer picker)) {
            return;
        }
        if (stack.has(com.frostlogic.warproject.ModDataComponents.PASSPORT.get())) {
            String ownerUuid = stack.get(com.frostlogic.warproject.ModDataComponents.PASSPORT.get()).ownerUuid();
            if (!ownerUuid.isBlank() && !ownerUuid.equals(picker.getUUID().toString())) {
                event.setCanPickup(net.neoforged.neoforge.common.util.TriState.FALSE);
                return;
            }
        }
        event.setCanPickup(net.neoforged.neoforge.common.util.TriState.TRUE);
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer)) {
            return;
        }
        Iterator<ItemEntity> iterator = event.getDrops().iterator();
        while (iterator.hasNext()) {
            ItemEntity itemEntity = iterator.next();
            if (PassportItem.isPassport(itemEntity.getItem())) {
                iterator.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer newPlayer)) {
            return;
        }
        Inventory oldInv = event.getOriginal().getInventory();
        for (int i = 0; i < oldInv.getContainerSize(); i++) {
            ItemStack stack = oldInv.getItem(i);
            if (PassportItem.isPassport(stack)) {
                ItemStack copy = stack.copy();
                if (!newPlayer.getInventory().add(copy)) {
                    for (int slot = 9; slot <= 35; slot++) {
                        if (newPlayer.getInventory().getItem(slot).isEmpty()) {
                            newPlayer.getInventory().setItem(slot, copy);
                            break;
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % 40 != 0) {
            return;
        }
        WarPlayerProfile profile = WarPlayerDataStore.get().getOrCreate(player);
        if (!profile.isFactionMember()) {
            return;
        }
        if (!PassportManager.hasPassport(player)) {
            PassportManager.issue(player, profile);
        }
    }
}
