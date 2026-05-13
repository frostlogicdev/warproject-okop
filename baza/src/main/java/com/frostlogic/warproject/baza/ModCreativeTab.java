package com.frostlogic.warproject.baza;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, WarProjectBaza.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BAZA_TAB = TABS.register("baza_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.baza"))
                    .icon(() -> new ItemStack(ModBlocks.MILITARY_CONCRETE.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.MILITARY_CONCRETE.get());
                        output.accept(ModItems.REINFORCED_CONCRETE.get());
                        output.accept(ModItems.CONCRETE_SLAB.get());
                        output.accept(ModItems.HESCO_BARRIER.get());
                        output.accept(ModItems.METAL_GATE.get());
                        output.accept(ModItems.CHECKPOINT_BARRIER.get());
                        output.accept(ModItems.TANK_HEDGEHOG.get());
                        output.accept(ModItems.RAZOR_WIRE_FENCE.get());
                    }).build());

    public static void register(IEventBus eventBus) {
        TABS.register(eventBus);
    }
}
