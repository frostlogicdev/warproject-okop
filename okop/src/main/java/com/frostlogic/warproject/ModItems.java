package com.frostlogic.warproject;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, WarProjectOkop.MOD_ID);

    public static final RegistryObject<Item> WOODEN_SUPPORT_BEAM = ITEMS.register("wooden_support_beam",
            () -> new BlockItem(ModBlocks.WOODEN_SUPPORT_BEAM.get(), new Item.Properties()));
    public static final RegistryObject<Item> IRON_SUPPORT_BEAM = ITEMS.register("iron_support_beam",
            () -> new BlockItem(ModBlocks.IRON_SUPPORT_BEAM.get(), new Item.Properties()));
    public static final RegistryObject<Item> REINFORCED_SUPPORT_BEAM = ITEMS.register("reinforced_support_beam",
            () -> new BlockItem(ModBlocks.REINFORCED_SUPPORT_BEAM.get(), new Item.Properties()));
    public static final RegistryObject<Item> FOREST_CAMO_NET = ITEMS.register("forest_camo_net",
            () -> new BlockItem(ModBlocks.FOREST_CAMO_NET.get(), new Item.Properties()));
    public static final RegistryObject<Item> DESERT_CAMO_NET = ITEMS.register("desert_camo_net",
            () -> new BlockItem(ModBlocks.DESERT_CAMO_NET.get(), new Item.Properties()));
    public static final RegistryObject<Item> WINTER_CAMO_NET = ITEMS.register("winter_camo_net",
            () -> new BlockItem(ModBlocks.WINTER_CAMO_NET.get(), new Item.Properties()));
    public static final RegistryObject<Item> SANDBAG = ITEMS.register("sandbag",
            () -> new BlockItem(ModBlocks.SANDBAG.get(), new Item.Properties()));
    public static final RegistryObject<Item> WOODEN_HORIZONTAL_COVER = ITEMS.register("wooden_horizontal_cover",
            () -> new BlockItem(ModBlocks.WOODEN_HORIZONTAL_COVER.get(), new Item.Properties()));
    public static final RegistryObject<Item> LOG_HORIZONTAL_COVER = ITEMS.register("log_horizontal_cover",
            () -> new BlockItem(ModBlocks.LOG_HORIZONTAL_COVER.get(), new Item.Properties()));
    public static final RegistryObject<Item> BARBED_WIRE = ITEMS.register("barbed_wire",
            () -> new BlockItem(ModBlocks.BARBED_WIRE.get(), new Item.Properties()));
    public static final RegistryObject<Item> DRAINAGE_GRATE = ITEMS.register("drainage_grate",
            () -> new BlockItem(ModBlocks.DRAINAGE_GRATE.get(), new Item.Properties()));
    public static final RegistryObject<Item> FIRING_SLOT = ITEMS.register("firing_slot",
            () -> new BlockItem(ModBlocks.FIRING_SLOT.get(), new Item.Properties()));
    public static final RegistryObject<Item> TRENCH_STAIRS = ITEMS.register("trench_stairs",
            () -> new BlockItem(ModBlocks.TRENCH_STAIRS.get(), new Item.Properties()));
    public static final RegistryObject<Item> TRENCH_LANTERN = ITEMS.register("trench_lantern",
            () -> new BlockItem(ModBlocks.TRENCH_LANTERN.get(), new Item.Properties()));
    public static final RegistryObject<Item> SUPPLY_CRATE = ITEMS.register("supply_crate",
            () -> new BlockItem(ModBlocks.SUPPLY_CRATE.get(), new Item.Properties()));

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
