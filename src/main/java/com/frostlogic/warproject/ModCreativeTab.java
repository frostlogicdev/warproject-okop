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
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, WarProject.MOD_ID);

    // Tab 1: Trench Fortification (Okop)
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
                        output.accept(ModItems.GARAGE_CHANDELIER.get());
                    }).build());

    // Tab 2: Field Camp (Polevoy)
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FIELD_CAMP_TAB = TABS.register("field_camp",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.warproject.field_camp"))
                    .icon(() -> new ItemStack(ModItems.SMALL_TENT.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.SMALL_TENT.get());
                        output.accept(ModItems.MEDICAL_TENT.get());
                        output.accept(ModItems.FIELD_KITCHEN.get());
                        output.accept(ModItems.FIELD_RADIO.get());
                        output.accept(ModItems.FIELD_SPOTLIGHT.get());
                        output.accept(ModItems.FIRST_AID_KIT.get());
                        output.accept(ModItems.GENERATOR.get());
                    }).build());

    // Tab 3: Military Base (Baza)
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MILITARY_BASE_TAB = TABS.register("military_base",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.warproject.military_base"))
                    .icon(() -> new ItemStack(ModItems.REINFORCED_CONCRETE.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.MILITARY_CONCRETE.get());
                        output.accept(ModItems.REINFORCED_CONCRETE.get());
                        output.accept(ModItems.CONCRETE_SLAB.get());
                        output.accept(ModItems.HESCO_BARRIER.get());
                        output.accept(ModItems.METAL_GATE.get());
                        output.accept(ModItems.CHECKPOINT_BARRIER.get());
                        output.accept(ModItems.TANK_HEDGEHOG.get());
                        output.accept(ModItems.RAZOR_WIRE_FENCE.get());
                        output.accept(ModItems.MESS_CHANDELIER.get());
                    }).build());

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
}
