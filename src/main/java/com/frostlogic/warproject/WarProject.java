package com.frostlogic.warproject;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(WarProject.MOD_ID)
public class WarProject {
    public static final String MOD_ID = "warproject";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WarProject(IEventBus modEventBus) {
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModCreativeTab.register(modEventBus);
        LOGGER.info("War Project v3.0 loaded! (31 blocks: Okop + Polevoy + Baza)");
    }
}
