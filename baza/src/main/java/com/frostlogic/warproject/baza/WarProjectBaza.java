package com.frostlogic.warproject.baza;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(WarProjectBaza.MOD_ID)
public class WarProjectBaza {
    public static final String MOD_ID = "baza";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WarProjectBaza(IEventBus modEventBus) {
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModCreativeTab.register(modEventBus);
        LOGGER.info("War Project - Baza (Military Base) loaded!");
    }
}
