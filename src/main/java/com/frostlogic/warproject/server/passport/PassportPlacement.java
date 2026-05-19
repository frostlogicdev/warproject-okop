package com.frostlogic.warproject.server.passport;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/**
 * Utility class implementing the passport placement algorithm (Design §8.2).
 * <p>
 * Algorithm:
 * <ol>
 *   <li>Search for a free slot in the main inventory (slots 9..35)</li>
 *   <li>If no free slot in main inventory, find the least valuable item in the hotbar (slots 0..8),
 *       move it to a free slot elsewhere, and place the passport in the freed hotbar slot
 *       (or in the main inventory if a slot was freed there)</li>
 *   <li>If the entire inventory is full (no free slots anywhere), return {@code Err("inventory_full")}</li>
 * </ol>
 * <p>
 * "Least valuable" is determined by {@link Rarity} ordinal (COMMON &lt; UNCOMMON &lt; RARE &lt; EPIC);
 * ties are broken by largest stack count (larger stacks are considered less valuable per-unit).
 * <p>
 * Validates: Requirements 12.2
 * Design: §8.2
 */
public final class PassportPlacement {

    private PassportPlacement() {
        // utility class — no instantiation
    }

    /**
     * Places the passport stack into the player's inventory following the §8.2 algorithm.
     *
     * @param player        the server player receiving the passport
     * @param passportStack the passport ItemStack to place
     * @return {@link PlaceResult.Ok} with the slot index on success,
     *         or {@link PlaceResult.Err} with a reason string on failure
     */
    public static PlaceResult place(ServerPlayer player, ItemStack passportStack) {
        Inventory inv = player.getInventory();

        // Step 1: Try main inventory slots (9..35)
        for (int slot = 9; slot < 36; slot++) {
            if (inv.getItem(slot).isEmpty()) {
                inv.setItem(slot, passportStack);
                return new PlaceResult.Ok(slot);
            }
        }

        // Step 2: No free slot in main inventory — fallback with hotbar swap
        int leastValuableHotbarSlot = pickLeastValuableHotbarSlot(inv);

        // Look for any free slot in the entire inventory (main 9..35, hotbar 0..8, offhand 40)
        Integer freeSlot = findFirstFreeSlotAnywhere(inv);
        if (freeSlot == null) {
            // Entire inventory is full — no free slots anywhere
            return new PlaceResult.Err("inventory_full");
        }

        // Move the least valuable hotbar item to the free slot
        ItemStack hotbarItem = inv.getItem(leastValuableHotbarSlot);
        inv.setItem(freeSlot, hotbarItem);
        inv.setItem(leastValuableHotbarSlot, ItemStack.EMPTY);

        // Now try to find a free main inventory slot (one should have been freed if freeSlot was in hotbar,
        // or the hotbar slot itself is now free)
        Integer mainSlot = findFirstFreeMainSlot(inv);
        if (mainSlot != null) {
            inv.setItem(mainSlot, passportStack);
            return new PlaceResult.Ok(mainSlot);
        }

        // Edge case: the free slot was in the hotbar itself (not main), so main is still full.
        // Place passport in the freed hotbar slot.
        inv.setItem(leastValuableHotbarSlot, passportStack);
        return new PlaceResult.Ok(leastValuableHotbarSlot);
    }

    /**
     * Finds the hotbar slot (0..8) containing the least valuable item.
     * <p>
     * Least valuable = lowest {@link Rarity} ordinal; ties broken by largest stack count.
     */
    static int pickLeastValuableHotbarSlot(Inventory inv) {
        int bestSlot = 0;
        int bestRarityOrd = rarityOrdinal(inv.getItem(0));
        int bestCount = inv.getItem(0).getCount();

        for (int slot = 1; slot < 9; slot++) {
            ItemStack stack = inv.getItem(slot);
            int rarityOrd = rarityOrdinal(stack);

            if (rarityOrd < bestRarityOrd) {
                // Lower rarity = less valuable
                bestSlot = slot;
                bestRarityOrd = rarityOrd;
                bestCount = stack.getCount();
            } else if (rarityOrd == bestRarityOrd && stack.getCount() > bestCount) {
                // Same rarity, larger stack = less valuable per-unit
                bestSlot = slot;
                bestCount = stack.getCount();
            }
        }

        return bestSlot;
    }

    /**
     * Returns the ordinal value of an item's rarity for comparison.
     * Empty stacks are treated as the lowest possible value (most expendable).
     */
    private static int rarityOrdinal(ItemStack stack) {
        if (stack.isEmpty()) {
            return -1; // empty slots are "least valuable"
        }
        return stack.getRarity().ordinal();
    }

    /**
     * Finds the first free slot in the main inventory (slots 9..35).
     *
     * @return the slot index, or null if no free slot exists
     */
    static Integer findFirstFreeMainSlot(Inventory inv) {
        for (int slot = 9; slot < 36; slot++) {
            if (inv.getItem(slot).isEmpty()) {
                return slot;
            }
        }
        return null;
    }

    /**
     * Finds the first free slot anywhere in the player's inventory.
     * Search order: main inventory (9..35), hotbar (0..8), offhand (40).
     *
     * @return the slot index, or null if no free slot exists
     */
    private static Integer findFirstFreeSlotAnywhere(Inventory inv) {
        // Main inventory first
        for (int slot = 9; slot < 36; slot++) {
            if (inv.getItem(slot).isEmpty()) {
                return slot;
            }
        }
        // Hotbar
        for (int slot = 0; slot < 9; slot++) {
            if (inv.getItem(slot).isEmpty()) {
                return slot;
            }
        }
        // Offhand (slot 40 in Inventory)
        if (inv.getItem(40).isEmpty()) {
            return 40;
        }
        return null;
    }

    // --- Result type ---

    /**
     * Sealed interface representing the result of a passport placement attempt.
     */
    public sealed interface PlaceResult permits PlaceResult.Ok, PlaceResult.Err {

        /**
         * Successful placement.
         *
         * @param slot the inventory slot index where the passport was placed
         */
        record Ok(int slot) implements PlaceResult {}

        /**
         * Failed placement.
         *
         * @param reason the reason for failure (e.g. "inventory_full")
         */
        record Err(String reason) implements PlaceResult {}
    }
}
