package com.frostlogic.warproject.polevoy;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(WarProjectPolevoy.MOD_ID)
public class WarProjectPolevoy {
    public static final String MOD_ID = "polevoy";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WarProjectPolevoy(IEventBus modEventBus) {
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModCreativeTab.register(modEventBus);
        LOGGER.info("War Project - Polevoy (Field Camp) loaded!");
    }
}
