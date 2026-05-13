package com.frostlogic.warproject.polevoy;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, WarProjectPolevoy.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> POLEVOY_TAB = TABS.register("polevoy_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.polevoy"))
                    .icon(() -> new ItemStack(ModBlocks.SMALL_TENT.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.SMALL_TENT.get());
                        output.accept(ModItems.COMMAND_TENT.get());
                        output.accept(ModItems.MEDICAL_TENT.get());
                        output.accept(ModItems.FIELD_KITCHEN.get());
                        output.accept(ModItems.FIRST_AID_KIT.get());
                        output.accept(ModItems.FIELD_RADIO.get());
                        output.accept(ModItems.FIELD_SPOTLIGHT.get());
                        output.accept(ModItems.GENERATOR.get());
                    }).build());

    public static void register(IEventBus eventBus) {
        TABS.register(eventBus);
    }
}
