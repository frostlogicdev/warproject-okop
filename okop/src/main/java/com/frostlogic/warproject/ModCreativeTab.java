package com.frostlogic.warproject;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, WarProjectOkop.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FORTIFICATION_TAB = TABS.register("fortification",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.warproject.fortification"))
                    .icon(() -> new ItemStack(ModItems.SANDBAG.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.WOODEN_SUPPORT_BEAM.get());
                        output.accept(ModItems.IRON_SUPPORT_BEAM.get());
                        output.accept(ModItems.REINFORCED_SUPPORT_BEAM.get());
                        output.accept(ModItems.FOREST_CAMO_NET.get());
                        output.accept(ModItems.DESERT_CAMO_NET.get());
                        output.accept(ModItems.WINTER_CAMO_NET.get());
                        output.accept(ModItems.SANDBAG.get());
                        output.accept(ModItems.WOODEN_HORIZONTAL_COVER.get());
                        output.accept(ModItems.LOG_HORIZONTAL_COVER.get());
                        output.accept(ModItems.BARBED_WIRE.get());
                        output.accept(ModItems.DRAINAGE_GRATE.get());
                        output.accept(ModItems.FIRING_SLOT.get());
                        output.accept(ModItems.TRENCH_STAIRS.get());
                        output.accept(ModItems.TRENCH_LANTERN.get());
                        output.accept(ModItems.SUPPLY_CRATE.get());
                    }).build());

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
}
