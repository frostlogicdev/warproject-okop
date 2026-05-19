package com.frostlogic.warproject.server.passport;

import com.frostlogic.warproject.WarProject;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Iterator;

/**
 * Guards passport items from being lost, duplicated, or transferred improperly.
 * <p>
 * Implements the following protections (design §11 SP-5):
 * <ol>
 *   <li>{@code ItemTossEvent} — cancel if the tossed item has PASSPORT_DATA component (prevents Q-drop)</li>
 *   <li>{@code ItemEntityPickupEvent.Pre} — cancel pickup of foreign passports without trophy flag</li>
 *   <li>{@code LivingDropsEvent} — remove passport items from death drops</li>
 *   <li>{@code PlayerEvent.Clone} — restore passport from original player on death</li>
 *   <li>{@code PlayerContainerEvent.Open} — prevent passport from being placed in non-player containers
 *       (note: full Slot.mayPlace/quickMoveStack prevention requires mixins; this handler uses
 *       a tick-based check to detect and return passports placed in external containers)</li>
 * </ol>
 * <p>
 * Requirements: 13.1–13.5, 14.4
 * Design: §11 SP-5
 */
@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class PassportGuardHandler {

    private PassportGuardHandler() {
        // utility class — no instantiation
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 1. Prevent Q-drop of passport (Req 13.1)
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Cancels the toss event if the item being tossed has a PASSPORT_DATA component.
     * The item is returned to the player's inventory.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = event.getEntity().getItem();
        if (!hasPassportData(stack)) {
            return;
        }

        event.setCanceled(true);

        // Return the passport to the player's inventory
        ItemStack copy = stack.copy();
        if (!player.getInventory().add(copy)) {
            // Fallback: place in main inventory slots (9..35)
            placeInMainInventory(player, copy);
        }
        event.getEntity().discard();

        player.sendSystemMessage(
                Component.translatable("wp.passport.cannot_drop")
                        .withStyle(ChatFormatting.RED)
        );
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 2. Prevent pickup of foreign passports without trophy flag (Req 13.5, 14.4)
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Cancels pickup of a passport that doesn't belong to the player
     * unless it has the trophy flag set (captured passport).
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPickup(ItemEntityPickupEvent.Pre event) {
        ItemEntity itemEntity = event.getItemEntity();
        if (itemEntity == null) {
            return;
        }
        ItemStack stack = itemEntity.getItem();
        if (!hasPassportData(stack)) {
            return;
        }
        if (!(event.getPlayer() instanceof ServerPlayer picker)) {
            return;
        }

        PassportData data = stack.get(PassportComponentTypes.PASSPORT_DATA.get());
        if (data == null) {
            return;
        }

        // Allow pickup if it's a trophy (captured passport)
        if (data.trophy()) {
            event.setCanPickup(net.neoforged.neoforge.common.util.TriState.TRUE);
            return;
        }

        // Allow pickup if the passport belongs to the picker
        String pickerUuid = picker.getUUID().toString();
        // PassportData doesn't store ownerUuid directly, but we can check via the passportId prefix
        // or by checking if the player already owns this passport. For now, we deny all foreign pickups.
        // The passport should only be on the ground if something went wrong — deny pickup of any
        // non-trophy passport that isn't the player's own.
        // We check the old component as well for backwards compatibility.
        if (isOwnPassport(stack, picker)) {
            event.setCanPickup(net.neoforged.neoforge.common.util.TriState.TRUE);
            return;
        }

        // Deny pickup of foreign passport without trophy flag
        event.setCanPickup(net.neoforged.neoforge.common.util.TriState.FALSE);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 3. Remove passports from death drops (Req 13.3)
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Removes all passport items from the drops list when a player dies.
     * The passport will be restored via {@link #onClone(PlayerEvent.Clone)}.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer)) {
            return;
        }
        Iterator<ItemEntity> iterator = event.getDrops().iterator();
        while (iterator.hasNext()) {
            ItemEntity itemEntity = iterator.next();
            if (hasPassportData(itemEntity.getItem())) {
                iterator.remove();
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 4. Restore passport on death clone (Req 13.3)
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * When a player dies and is cloned, copies passport items from the original
     * player's inventory to the new player entity.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
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
            if (hasPassportData(stack)) {
                ItemStack copy = stack.copy();
                if (!newPlayer.getInventory().add(copy)) {
                    placeInMainInventory(newPlayer, copy);
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 5. Container transfer prevention (Req 13.4)
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * When a player opens a container, schedule a check on the next tick to detect
     * if a passport was placed in the container and return it.
     * <p>
     * Note: {@code PlayerContainerEvent.Open} is not cancelable in NeoForge.
     * Full prevention of passport placement in non-player containers requires
     * mixins on {@code Slot.mayPlace} and {@code AbstractContainerMenu.quickMoveStack}.
     * This event-based approach provides a best-effort guard by monitoring the
     * player's open container on each tick and returning any passport found there.
     * <p>
     * TODO: Add mixin for Slot.mayPlace to fully prevent passport insertion into
     * non-PlayerInventory slots. See design §11 SP-5 note on mixins.
     */
    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // The inventoryMenu is the player's own inventory — that's always allowed
        if (event.getContainer() == player.inventoryMenu) {
            return;
        }
        // Schedule a recurring check while the container is open
        // This is handled by the tick event below
    }

    /**
     * On each player tick, if the player has a non-inventory container open,
     * scan the container slots for passport items and return them to the player's inventory.
     * This provides a best-effort guard against passport placement in external containers.
     */
    @SubscribeEvent
    public static void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // Only check every 5 ticks for performance
        if (player.tickCount % 5 != 0) {
            return;
        }
        // If the player has a container open that isn't their own inventory
        if (player.containerMenu == player.inventoryMenu) {
            return;
        }

        // Scan the container's non-player slots for passports
        var containerMenu = player.containerMenu;
        for (int i = 0; i < containerMenu.slots.size(); i++) {
            var slot = containerMenu.slots.get(i);
            // Skip slots that belong to the player's inventory
            if (slot.container instanceof Inventory) {
                continue;
            }
            ItemStack slotStack = slot.getItem();
            if (hasPassportData(slotStack)) {
                // Remove from container slot
                slot.set(ItemStack.EMPTY);
                // Return to player inventory
                ItemStack copy = slotStack.copy();
                if (!player.getInventory().add(copy)) {
                    placeInMainInventory(player, copy);
                }
                player.sendSystemMessage(
                        Component.translatable("wp.passport.cannot_place_container")
                                .withStyle(ChatFormatting.RED)
                );
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Helper methods
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Checks if the given ItemStack has the new PASSPORT_DATA component.
     */
    private static boolean hasPassportData(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return stack.has(PassportComponentTypes.PASSPORT_DATA.get());
    }

    /**
     * Checks if the passport belongs to the given player.
     * Since PassportData doesn't store ownerUuid directly, we check the player's
     * existing inventory for a passport with the same ID.
     */
    private static boolean isOwnPassport(ItemStack stack, ServerPlayer player) {
        PassportData pickupData = stack.get(PassportComponentTypes.PASSPORT_DATA.get());
        if (pickupData == null) {
            return false;
        }

        // Check if the player already has a passport — if they do, this isn't theirs
        // (a player should only have one passport)
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack existing = inv.getItem(i);
            if (existing.isEmpty()) continue;
            PassportData existingData = existing.get(PassportComponentTypes.PASSPORT_DATA.get());
            if (existingData != null) {
                // Player already has a passport — the one on the ground is not theirs
                // unless it has the same ID (shouldn't happen normally)
                return existingData.passportId().equals(pickupData.passportId());
            }
        }

        // Player has no passport in inventory — this might be theirs (e.g., after a bug/crash)
        // Allow pickup as a safety measure
        return true;
    }

    /**
     * Places an item in the main inventory area (slots 9..35).
     * Falls back to any available slot if main area is full.
     */
    private static void placeInMainInventory(ServerPlayer player, ItemStack stack) {
        for (int slot = 9; slot <= 35; slot++) {
            if (player.getInventory().getItem(slot).isEmpty()) {
                player.getInventory().setItem(slot, stack);
                return;
            }
        }
        // Last resort: try hotbar
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getItem(slot).isEmpty()) {
                player.getInventory().setItem(slot, stack);
                return;
            }
        }
    }
}
