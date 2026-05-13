package com.frostlogic.warproject;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(WarProjectOkop.MOD_ID)
public class WarProjectOkop {
    public static final String MOD_ID = "warproject";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WarProjectOkop() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.register(bus);
        ModItems.register(bus);
        ModBlockEntities.register(bus);
        ModCreativeTab.register(bus);
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("War Project - Okop loaded!");
    }
}
