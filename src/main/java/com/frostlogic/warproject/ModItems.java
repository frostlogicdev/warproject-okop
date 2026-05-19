package com.frostlogic.warproject;

import com.frostlogic.warproject.item.FirstAidKitItem;
import com.frostlogic.warproject.item.PassportItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(WarProject.MOD_ID);

    // === OKOP ===
    public static final DeferredItem<Item> WOODEN_SUPPORT_BEAM = ITEMS.register("wooden_support_beam",
            () -> new BlockItem(ModBlocks.WOODEN_SUPPORT_BEAM.get(), new Item.Properties()));
    public static final DeferredItem<Item> IRON_SUPPORT_BEAM = ITEMS.register("iron_support_beam",
            () -> new BlockItem(ModBlocks.IRON_SUPPORT_BEAM.get(), new Item.Properties()));
    public static final DeferredItem<Item> REINFORCED_SUPPORT_BEAM = ITEMS.register("reinforced_support_beam",
            () -> new BlockItem(ModBlocks.REINFORCED_SUPPORT_BEAM.get(), new Item.Properties()));
    public static final DeferredItem<Item> FOREST_CAMO_NET = ITEMS.register("forest_camo_net",
            () -> new BlockItem(ModBlocks.FOREST_CAMO_NET.get(), new Item.Properties()));
    public static final DeferredItem<Item> DESERT_CAMO_NET = ITEMS.register("desert_camo_net",
            () -> new BlockItem(ModBlocks.DESERT_CAMO_NET.get(), new Item.Properties()));
    public static final DeferredItem<Item> WINTER_CAMO_NET = ITEMS.register("winter_camo_net",
            () -> new BlockItem(ModBlocks.WINTER_CAMO_NET.get(), new Item.Properties()));
    public static final DeferredItem<Item> SANDBAG = ITEMS.register("sandbag",
            () -> new BlockItem(ModBlocks.SANDBAG.get(), new Item.Properties()));
    public static final DeferredItem<Item> WOODEN_HORIZONTAL_COVER = ITEMS.register("wooden_horizontal_cover",
            () -> new BlockItem(ModBlocks.WOODEN_HORIZONTAL_COVER.get(), new Item.Properties()));
    public static final DeferredItem<Item> LOG_HORIZONTAL_COVER = ITEMS.register("log_horizontal_cover",
            () -> new BlockItem(ModBlocks.LOG_HORIZONTAL_COVER.get(), new Item.Properties()));
    public static final DeferredItem<Item> BARBED_WIRE = ITEMS.register("barbed_wire",
            () -> new BlockItem(ModBlocks.BARBED_WIRE.get(), new Item.Properties()));
    public static final DeferredItem<Item> DRAINAGE_GRATE = ITEMS.register("drainage_grate",
            () -> new BlockItem(ModBlocks.DRAINAGE_GRATE.get(), new Item.Properties()));
    public static final DeferredItem<Item> FIRING_SLOT = ITEMS.register("firing_slot",
            () -> new BlockItem(ModBlocks.FIRING_SLOT.get(), new Item.Properties()));
    public static final DeferredItem<Item> TRENCH_STAIRS = ITEMS.register("trench_stairs",
            () -> new BlockItem(ModBlocks.TRENCH_STAIRS.get(), new Item.Properties()));
    public static final DeferredItem<Item> TRENCH_LANTERN = ITEMS.register("trench_lantern",
            () -> new BlockItem(ModBlocks.TRENCH_LANTERN.get(), new Item.Properties()));
    public static final DeferredItem<Item> SUPPLY_CRATE = ITEMS.register("supply_crate",
            () -> new BlockItem(ModBlocks.SUPPLY_CRATE.get(), new Item.Properties()));
    public static final DeferredItem<Item> GARAGE_CHANDELIER = ITEMS.register("garage_chandelier",
            () -> new BlockItem(ModBlocks.GARAGE_CHANDELIER.get(), new Item.Properties()));
    public static final DeferredItem<Item> MESS_CHANDELIER = ITEMS.register("mess_chandelier",
            () -> new BlockItem(ModBlocks.MESS_CHANDELIER.get(), new Item.Properties()));

    // === POLEVOY ===
    public static final DeferredItem<Item> SMALL_TENT = ITEMS.register("small_tent",
            () -> new BlockItem(ModBlocks.SMALL_TENT.get(), new Item.Properties()));
    public static final DeferredItem<Item> MEDICAL_TENT = ITEMS.register("medical_tent",
            () -> new BlockItem(ModBlocks.MEDICAL_TENT.get(), new Item.Properties()));
    public static final DeferredItem<Item> FIELD_KITCHEN = ITEMS.register("field_kitchen",
            () -> new BlockItem(ModBlocks.FIELD_KITCHEN.get(), new Item.Properties()));
    public static final DeferredItem<Item> FIELD_RADIO = ITEMS.register("field_radio",
            () -> new BlockItem(ModBlocks.FIELD_RADIO.get(), new Item.Properties()));
    public static final DeferredItem<Item> FIELD_SPOTLIGHT = ITEMS.register("field_spotlight",
            () -> new BlockItem(ModBlocks.FIELD_SPOTLIGHT.get(), new Item.Properties()));
    public static final DeferredItem<Item> FIRST_AID_KIT = ITEMS.register("first_aid_kit",
            () -> new FirstAidKitItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<Item> GENERATOR = ITEMS.register("generator",
            () -> new BlockItem(ModBlocks.GENERATOR.get(), new Item.Properties()));

    // === BAZA ===
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

    // === WarProject server items ===
    public static final DeferredItem<Item> PASSPORT = ITEMS.register("passport",
            () -> new PassportItem(new Item.Properties()));

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
