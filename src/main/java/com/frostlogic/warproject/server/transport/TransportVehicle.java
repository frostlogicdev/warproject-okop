package com.frostlogic.warproject.server.transport;

import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.server.Rank;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * One row of the transport access matrix.
 * <p>
 * A vehicle is identified by an {@link ResourceLocation} pointing to a registered
 * {@link net.minecraft.world.item.Item}. The mod that registers that item is
 * usually a third-party vehicle mod (Immersive Vehicles / MTS / TaCZ-vehicles,
 * etc.). Storing only the ID keeps WarProject decoupled from any specific
 * vehicle mod — the server admin lists IDs in the TOML config and we look them
 * up at runtime.
 *
 * @param itemId       Registry id of the vehicle item (e.g. {@code iv:tiger_2}).
 *                     If the item cannot be resolved at runtime (mod missing),
 *                     {@link #resolveItem()} returns {@link Items#BARRIER}.
 * @param displayKey   Translation key for the vehicle's display name in the
 *                     menu (e.g. {@code wp.transport.vehicle.tiger_2}).
 * @param minRank      Minimum rank that can claim this vehicle. Players whose
 *                     current rank weight is &lt; this rank's weight are denied
 *                     with the head-shake animation.
 * @param faction      Which faction this entry belongs to. Used to filter
 *                     which NPC offers which vehicles.
 */
public record TransportVehicle(
        ResourceLocation itemId,
        String displayKey,
        Rank minRank,
        FactionId faction
) {

    /** Resolves the underlying {@link Item}, falling back to barrier when the mod isn't installed. */
    public Item resolveItem() {
        Item item = BuiltInRegistries.ITEM.get(itemId);
        // BuiltInRegistries.ITEM.get returns AIR (default) for unknown ids;
        // use barrier instead so the GUI shows something obvious and admins
        // notice a typo / missing mod immediately.
        if (item == Items.AIR) {
            return Items.BARRIER;
        }
        return item;
    }

    /** True if the resolved item is a real, mod-provided item (not the missing-mod placeholder). */
    public boolean isAvailable() {
        return BuiltInRegistries.ITEM.get(itemId) != Items.AIR;
    }
}
