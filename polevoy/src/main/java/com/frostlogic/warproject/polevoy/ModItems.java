package com.frostlogic.warproject.polevoy;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(WarProjectPolevoy.MOD_ID);

    public static final DeferredItem<Item> SMALL_TENT = ITEMS.register("small_tent",
            () -> new BlockItem(ModBlocks.SMALL_TENT.get(), new Item.Properties()));
    public static final DeferredItem<Item> COMMAND_TENT = ITEMS.register("command_tent",
            () -> new BlockItem(ModBlocks.COMMAND_TENT.get(), new Item.Properties()));
    public static final DeferredItem<Item> MEDICAL_TENT = ITEMS.register("medical_tent",
            () -> new BlockItem(ModBlocks.MEDICAL_TENT.get(), new Item.Properties()));
    public static final DeferredItem<Item> FIELD_KITCHEN = ITEMS.register("field_kitchen",
            () -> new BlockItem(ModBlocks.FIELD_KITCHEN.get(), new Item.Properties()));
    public static final DeferredItem<Item> FIRST_AID_KIT = ITEMS.register("first_aid_kit",
            () -> new BlockItem(ModBlocks.FIRST_AID_KIT.get(), new Item.Properties()));
    public static final DeferredItem<Item> FIELD_RADIO = ITEMS.register("field_radio",
            () -> new BlockItem(ModBlocks.FIELD_RADIO.get(), new Item.Properties()));
    public static final DeferredItem<Item> FIELD_SPOTLIGHT = ITEMS.register("field_spotlight",
            () -> new BlockItem(ModBlocks.FIELD_SPOTLIGHT.get(), new Item.Properties()));
    public static final DeferredItem<Item> GENERATOR = ITEMS.register("generator",
            () -> new BlockItem(ModBlocks.GENERATOR.get(), new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
