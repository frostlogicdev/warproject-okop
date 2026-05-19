package com.frostlogic.warproject.client;

import com.frostlogic.warproject.WarProject;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;

/**
 * Suppresses JourneyMap's automatic chat messages on the client.
 * <p>
 * JM sends messages like "JourneyMap: Press [J] to open the map" and
 * "JourneyMap: Type /jm to see available commands" on first login.
 * These break RP immersion on a military server, so we silently drop them.
 * <p>
 * Detection: any system message whose plain-text content starts with "JourneyMap:"
 * or contains "journeymap" (case-insensitive) is cancelled.
 */
@EventBusSubscriber(modid = WarProject.MOD_ID, value = Dist.CLIENT)
public final class JmChatFilter {

    private JmChatFilter() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSystemChat(ClientChatReceivedEvent.System event) {
        if (isJourneyMapMessage(event.getMessage())) {
            event.setCanceled(true);
        }
    }

    private static boolean isJourneyMapMessage(Component message) {
        if (message == null) return false;
        String text = message.getString();
        if (text == null || text.isEmpty()) return false;

        // JM messages always start with "JourneyMap:" or contain the mod name
        String lower = text.toLowerCase();
        if (lower.startsWith("journeymap:") || lower.startsWith("journeymap :")) {
            return true;
        }
        // Also catch localized variants
        if (lower.contains("journeymap")) {
            return true;
        }
        // Catch messages with key binding hints in brackets: [M], [J], [Ь], [О] etc.
        // JM pattern: "Something: Нажмите [X]" or "Something: Press [X]"
        if (lower.contains("нажмите") && text.contains("[")) {
            return true;
        }
        if (lower.contains("press") && text.contains("[") && lower.contains("map")) {
            return true;
        }
        // Catch /jm command hints
        if (text.contains("/jm")) {
            return true;
        }
        return false;
    }
}
