package com.frostlogic.warproject.item;

import com.frostlogic.warproject.ModDataComponents;
import com.frostlogic.warproject.server.Country;
import com.frostlogic.warproject.server.Faction;
import com.frostlogic.warproject.server.Rank;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class PassportItem extends Item {
    public PassportItem(Properties properties) {
        super(properties.stacksTo(1).rarity(Rarity.UNCOMMON).fireResistant());
    }

    @Override
    public Component getName(ItemStack stack) {
        PassportData data = stack.get(ModDataComponents.PASSPORT.get());
        if (data == null || data.rpName().isBlank()) {
            return Component.literal("Паспорт").withStyle(ChatFormatting.GOLD);
        }
        Faction faction = data.factionEnum();
        ChatFormatting color = faction.isPlayable() ? faction.color() : ChatFormatting.GOLD;
        return Component.literal("Паспорт — " + data.rpName()).withStyle(color);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        PassportData data = stack.get(ModDataComponents.PASSPORT.get());
        if (data == null) {
            tooltip.add(Component.literal("Пустой паспорт").withStyle(ChatFormatting.GRAY));
            return;
        }

        tooltip.add(Component.literal("═══ Документ ═══").withStyle(ChatFormatting.GOLD));

        tooltip.add(Component.literal("Имя: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(data.rpName().isBlank() ? "—" : data.rpName()).withStyle(ChatFormatting.WHITE)));

        tooltip.add(Component.literal("Возраст: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(data.age() > 0 ? String.valueOf(data.age()) : "—").withStyle(ChatFormatting.WHITE)));

        Country country = data.countryEnum();
        tooltip.add(Component.literal("Страна рождения: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(country.displayName()).withStyle(ChatFormatting.WHITE)));

        String subdivisionLabel = data.subdivision().isBlank() ? "—" : data.subdivision();
        tooltip.add(Component.literal("Подразделение: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(subdivisionLabel).withStyle(ChatFormatting.WHITE)));

        Faction faction = data.factionEnum();
        tooltip.add(Component.literal("Сторона: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(faction.displayName()).withStyle(faction.isPlayable() ? faction.color() : ChatFormatting.WHITE)));

        Rank rank = data.rankEnum();
        String rankLabel = "citizen".equals(data.rankId())
                ? "Гражданин"
                : rank.displayName();
        tooltip.add(Component.literal("Звание: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(rankLabel).withStyle(ChatFormatting.WHITE)));

        tooltip.add(Component.literal("").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Этот документ нельзя выбросить.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }

    public static boolean isPassport(ItemStack stack) {
        return stack.getItem() instanceof PassportItem;
    }
}
