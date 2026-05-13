package com.frostlogic.warproject.baza;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(WarProjectBaza.MOD_ID);

    public static final DeferredItem<Item> MILITARY_CONCRETE = ITEMS.register("military_concrete",
            () -> new BlockItem(ModBlocks.MILITARY_CONCRETE.get(), new Item.Properties()));
    public static final DeferredItem<Item> REINFORCED_CONCRETE = ITEMS.register("reinforced_concrete",
            () -> new BlockItem(ModBlocks.REINFORCED_CONCRETE.get(), new Item.Properties()));
    public static final DeferredItem<Item> CONCRETE_SLAB = ITEMS.register("concrete_slab",
            () -> new BlockItem(ModBlocks.CONCRETE_SLAB.get(), new Item.Properties()));
    public static final DeferredItem<Item> HESCO_BARRIER = ITEMS.register("hesco_barrier",
            () -> new BlockItem(ModBlocks.HESCO_BARRIER.get(), new Item.Properties()));
    public static final DeferredItem<Item> METAL_GATE = ITEMS.register("metal_gate",
            () -> new BlockItem(ModBlocks.METAL_GATE.get(), new Item.Properties()));
    public static final DeferredItem<Item> CHECKPOINT_BARRIER = ITEMS.register("checkpoint_barrier",
            () -> new BlockItem(ModBlocks.CHECKPOINT_BARRIER.get(), new Item.Properties()));
    public static final DeferredItem<Item> TANK_HEDGEHOG = ITEMS.register("tank_hedgehog",
            () -> new BlockItem(ModBlocks.TANK_HEDGEHOG.get(), new Item.Properties()));
    public static final DeferredItem<Item> RAZOR_WIRE_FENCE = ITEMS.register("razor_wire_fence",
            () -> new BlockItem(ModBlocks.RAZOR_WIRE_FENCE.get(), new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
