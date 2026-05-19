package com.frostlogic.warproject;

import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import com.frostlogic.warproject.server.captivity.WpMenuTypes;
import com.frostlogic.warproject.server.command.argument.WpCommandArgumentTypes;
import com.frostlogic.warproject.server.faction.npc.FactionNpcEntityType;
import com.frostlogic.warproject.server.militaryid.MilitaryIdComponentTypes;
import com.frostlogic.warproject.server.passport.PassportComponentTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * Main entrypoint for the WarProject mod.
 * <p>
 * Registers all deferred registries on the MOD event bus, loads server config,
 * and wires up server/client event subscribers.
 * <p>
 * Requirements: 4.1, 19.1
 * Design: §3
 */
@Mod(WarProject.MOD_ID)
public class WarProject {
    public static final String MOD_ID = "warproject";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WarProject(IEventBus modEventBus, ModContainer modContainer) {
        // --- Server config registration ---
        modContainer.registerConfig(ModConfig.Type.SERVER, WpConfig.SPEC);

        // --- Existing deferred registries (blocks, items, block entities, data components, creative tab) ---
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModDataComponents.register(modEventBus);
        ModCreativeTab.register(modEventBus);

        // --- New WarProject registries ---
        WpAttachmentTypes.REG.register(modEventBus);
        FactionNpcEntityType.register(modEventBus);
        PassportComponentTypes.register(modEventBus);
        MilitaryIdComponentTypes.register(modEventBus);
        WpMenuTypes.register(modEventBus);
        WpCommandArgumentTypes.register(modEventBus);

        LOGGER.info("War Project v3.0 loaded! (31 blocks: Okop + Polevoy + Baza)");
    }
}
