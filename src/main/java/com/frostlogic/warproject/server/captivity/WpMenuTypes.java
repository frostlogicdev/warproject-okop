package com.frostlogic.warproject.server.captivity;

import com.frostlogic.warproject.WarProject;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registry for custom menu types used by the WarProject mod.
 * <p>
 * Currently registers the {@link RansomTradeMenu} for the captivity/ransom system.
 * <p>
 * Requirements: 14.5, 15 (open question #21 — manual mode)
 * Design: §16 (mode=MANUAL)
 */
public final class WpMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, WarProject.MOD_ID);

    /**
     * Menu type for the ransom trade GUI.
     * Uses NeoForge's extended menu type to pass additional data (target player UUID) on open.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<RansomTradeMenu>> RANSOM_TRADE =
            MENUS.register("ransom_trade", () -> IMenuTypeExtension.create(RansomTradeMenu::fromNetwork));

    private WpMenuTypes() {}

    /**
     * Registers the menu types deferred register on the mod event bus.
     *
     * @param modEventBus the mod event bus
     */
    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
