package com.frostlogic.warproject.client;

import com.frostlogic.warproject.WarProject;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = WarProject.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class WarKeyBindings {
    public static final String CATEGORY = "key.categories.warproject";

    public static final KeyMapping RADIAL_MENU = new KeyMapping(
            "key.warproject.radial",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            CATEGORY
    );

    public static final KeyMapping SET_MARKER = new KeyMapping(
            "key.warproject.set_marker",
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
            CATEGORY
    );

    private WarKeyBindings() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterKeyMappingsEvent event) {
        event.register(RADIAL_MENU);
        event.register(SET_MARKER);
    }
}
