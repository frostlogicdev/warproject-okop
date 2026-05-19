package com.frostlogic.warproject.server.passport;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import com.frostlogic.warproject.network.payload.s2c.PassportSnapshotPayload;

/**
 * Design-compliant PassportItem (§4.4, §9.3).
 * <p>
 * Extends {@link Item} with stacksTo(1) and rarity(EPIC).
 * On server-side use, reads {@link PassportComponentTypes#PASSPORT_DATA} from the held stack
 * and sends a {@link PassportSnapshotPayload} to the player for PassportScreen display.
 * <p>
 * Requirements: 12.3
 * Design: §4.4, §9.3
 */
public class PassportItem extends Item {

    public PassportItem(Properties properties) {
        super(properties.stacksTo(1).rarity(Rarity.EPIC));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            PassportData passportData = stack.get(PassportComponentTypes.PASSPORT_DATA.get());
            if (passportData != null) {
                PacketDistributor.sendToPlayer(serverPlayer, new PassportSnapshotPayload(passportData));
            }
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
