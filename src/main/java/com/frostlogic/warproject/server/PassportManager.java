package com.frostlogic.warproject.server;

import com.frostlogic.warproject.ModDataComponents;
import com.frostlogic.warproject.ModItems;
import com.frostlogic.warproject.item.PassportData;
import com.frostlogic.warproject.item.PassportItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public final class PassportManager {
    private PassportManager() {
    }

    public static ItemStack create(WarPlayerProfile profile) {
        ItemStack stack = new ItemStack(ModItems.PASSPORT.get());
        stack.set(ModDataComponents.PASSPORT.get(), PassportData.fromProfile(profile));
        return stack;
    }

    public static boolean hasPassport(ServerPlayer player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (PassportItem.isPassport(inv.getItem(i))) {
                return true;
            }
        }
        return false;
    }

    public static Optional<ItemStack> findPassport(ServerPlayer player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (PassportItem.isPassport(stack)) {
                return Optional.of(stack);
            }
        }
        return Optional.empty();
    }

    public static void issue(ServerPlayer player, WarPlayerProfile profile) {
        // Support candidate-only passports too: if the player has chosen a side
        // via the spawn NPC but has not yet been accepted, we still issue a
        // passport that reads "Гражданин" + their candidate faction.
        Faction effective = profile.getFaction();
        if (!effective.isPlayable()) {
            effective = profile.getCandidateFaction();
        }
        if (!effective.isPlayable()) {
            // Truly factionless — nothing to issue yet.
            return;
        }
        if (profile.getAge() == 0) {
            profile.setAge(Country.generateAge());
        }
        if (profile.getBirthCountry() == Country.UNKNOWN) {
            profile.setBirthCountry(Country.generateFor(effective));
        }
        if (hasPassport(player)) {
            updateAll(player, profile);
            return;
        }
        ItemStack passport = create(profile);
        Inventory inv = player.getInventory();
        for (int slot = 9; slot <= 35; slot++) {
            if (inv.getItem(slot).isEmpty()) {
                inv.setItem(slot, passport);
                if (player.containerMenu != null) {
                    player.containerMenu.broadcastChanges();
                }
                return;
            }
        }
        if (!inv.add(passport)) {
            player.drop(passport, false);
        }
    }

    public static int remove(ServerPlayer player) {
        Inventory inv = player.getInventory();
        int removed = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (PassportItem.isPassport(inv.getItem(i))) {
                inv.setItem(i, ItemStack.EMPTY);
                removed++;
            }
        }
        if (removed > 0 && player.containerMenu != null) {
            player.containerMenu.broadcastChanges();
        }
        return removed;
    }

    public static void updateAll(ServerPlayer player, WarPlayerProfile profile) {
        PassportData data = PassportData.fromProfile(profile);
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (PassportItem.isPassport(stack)) {
                stack.set(ModDataComponents.PASSPORT.get(), data);
            }
        }
        if (player.containerMenu != null) {
            player.containerMenu.broadcastChanges();
        }
    }

    public static Optional<PassportData> readData(ItemStack stack) {
        if (!PassportItem.isPassport(stack)) {
            return Optional.empty();
        }
        return Optional.ofNullable(stack.get(ModDataComponents.PASSPORT.get()));
    }
}
