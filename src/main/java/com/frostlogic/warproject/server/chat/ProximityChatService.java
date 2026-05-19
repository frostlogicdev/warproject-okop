package com.frostlogic.warproject.server.chat;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.WpConfig;
import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.attachment.PlayerState;
import com.frostlogic.warproject.attachment.Role;
import com.frostlogic.warproject.attachment.RpName;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Proximity-based chat routing for WarProject.
 * <p>
 * Intercepts every {@link ServerChatEvent} and replaces the vanilla
 * broadcast (all players) with scope-limited delivery:
 * <ul>
 *   <li><b>LOCAL</b> (default) — only players within a configurable radius
 *       ({@link WpConfig#CHAT_LOCAL_RADIUS}) in the same dimension.</li>
 *   <li><b>FACTION</b> — all online ACCEPTED members of the sender's faction.</li>
 *   <li><b>COMMANDER</b> — commanders+ of the sender's faction only.</li>
 * </ul>
 * <p>
 * GC ({@link ChatScope#GC}) is handled separately by {@link GeneralChatService}
 * via the {@code /wp generalchat} command and is not routed here.
 * <p>
 * Players who are not yet ACCEPTED (frozen states) have their chat blocked
 * by {@link com.frostlogic.warproject.server.lifecycle.FreezeService}; this
 * service only handles routing for players who are allowed to chat.
 * <p>
 * Requirements: 15.1–15.3
 * Design: §7
 */
@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class ProximityChatService {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Default radius in blocks for LOCAL chat. */
    private static final int DEFAULT_LOCAL_RADIUS = 50;

    private ProximityChatService() {
    }

    // ─── Event Subscriber ──────────────────────────────────────────────────────

    /**
     * Intercepts vanilla chat and routes it through the proximity system.
     * <p>
     * Priority {@code LOW} runs after FreezeService (HIGHEST) and mute checks (HIGH)
     * so we only process messages that are already allowed through.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onServerChat(ServerChatEvent event) {
        ServerPlayer sender = event.getPlayer();

        // Only route for ACCEPTED players (others are blocked by FreezeService)
        PlayerState state = sender.getData(WpAttachmentTypes.PLAYER_STATE.get());
        if (state != PlayerState.ACCEPTED && !sender.hasPermissions(2)) {
            return; // Let vanilla/FreezeService handle non-accepted players
        }

        // Cancel vanilla broadcast — we handle delivery ourselves
        event.setCanceled(true);

        // Route as LOCAL chat
        deliverLocal(sender, event.getRawText());
    }

    // ─── Public API ────────────────────────────────────────────────────────────

    /**
     * Delivers a LOCAL-scope message: only players within radius in the same dimension.
     */
    public static void deliverLocal(ServerPlayer sender, String message) {
        int radius = getLocalRadius();
        List<ServerPlayer> recipients = new ArrayList<>();
        for (ServerPlayer candidate : sender.server.getPlayerList().getPlayers()) {
            if (candidate == sender) {
                recipients.add(candidate);
                continue;
            }
            if (!candidate.level().dimension().equals(sender.level().dimension())) {
                continue;
            }
            if (candidate.distanceTo(sender) <= radius) {
                recipients.add(candidate);
            }
        }

        MutableComponent formatted = formatLocal(sender, message);
        for (ServerPlayer recipient : recipients) {
            recipient.sendSystemMessage(formatted);
        }

        LOGGER.debug("[WP Chat] LOCAL: {} → {} recipients (r={})",
                sender.getGameProfile().getName(), recipients.size(), radius);
    }

    /**
     * Delivers a FACTION-scope message: all online ACCEPTED members of the sender's faction.
     */
    public static void deliverFaction(ServerPlayer sender, String message) {
        Optional<FactionId> senderFaction = sender.getData(WpAttachmentTypes.FACTION.get());
        if (senderFaction.isEmpty()) {
            sender.sendSystemMessage(Component.translatable("wp.chat.no_faction")
                    .withStyle(net.minecraft.ChatFormatting.RED));
            return;
        }

        FactionId faction = senderFaction.get();
        List<ServerPlayer> recipients = new ArrayList<>();
        for (ServerPlayer candidate : sender.server.getPlayerList().getPlayers()) {
            Optional<FactionId> candidateFaction = candidate.getData(WpAttachmentTypes.FACTION.get());
            if (candidateFaction.isPresent() && candidateFaction.get() == faction) {
                recipients.add(candidate);
            }
        }

        MutableComponent formatted = formatFaction(sender, message, faction);
        for (ServerPlayer recipient : recipients) {
            recipient.sendSystemMessage(formatted);
        }

        LOGGER.debug("[WP Chat] FACTION: {} ({}) → {} recipients",
                sender.getGameProfile().getName(), faction, recipients.size());
    }

    /**
     * Delivers a COMMANDER-scope message: commanders+ of the sender's faction only.
     */
    public static void deliverCommander(ServerPlayer sender, String message) {
        Optional<FactionId> senderFaction = sender.getData(WpAttachmentTypes.FACTION.get());
        if (senderFaction.isEmpty()) {
            sender.sendSystemMessage(Component.translatable("wp.chat.no_faction")
                    .withStyle(net.minecraft.ChatFormatting.RED));
            return;
        }

        FactionId faction = senderFaction.get();
        List<ServerPlayer> recipients = new ArrayList<>();
        for (ServerPlayer candidate : sender.server.getPlayerList().getPlayers()) {
            Optional<FactionId> candidateFaction = candidate.getData(WpAttachmentTypes.FACTION.get());
            if (candidateFaction.isPresent() && candidateFaction.get() == faction) {
                Role role = candidate.getData(WpAttachmentTypes.ROLE.get());
                if (role.atLeast(Role.COMMANDER)) {
                    recipients.add(candidate);
                }
            }
        }

        MutableComponent formatted = formatCommander(sender, message, faction);
        for (ServerPlayer recipient : recipients) {
            recipient.sendSystemMessage(formatted);
        }

        LOGGER.debug("[WP Chat] COMMANDER: {} ({}) → {} recipients",
                sender.getGameProfile().getName(), faction, recipients.size());
    }

    // ─── Formatting ────────────────────────────────────────────────────────────

    private static MutableComponent formatLocal(ServerPlayer sender, String message) {
        String displayName = displayName(sender);
        return Component.translatable("wp.chat.local", displayName, message);
    }

    private static MutableComponent formatFaction(ServerPlayer sender, String message, FactionId faction) {
        String displayName = displayName(sender);
        return Component.translatable("wp.chat.faction",
                Component.translatable(faction.displayNameKey()), displayName, message);
    }

    private static MutableComponent formatCommander(ServerPlayer sender, String message, FactionId faction) {
        String displayName = displayName(sender);
        return Component.translatable("wp.chat.commander",
                Component.translatable(faction.displayNameKey()), displayName, message);
    }

    /**
     * Computes the player's display name for the chat line. Prefers the RP name;
     * falls back to the Mojang profile name.
     */
    private static String displayName(ServerPlayer player) {
        Optional<RpName> rp = player.getData(WpAttachmentTypes.RP_NAME.get());
        return rp.map(RpName::fullName).orElseGet(() -> player.getGameProfile().getName());
    }

    // ─── Config ────────────────────────────────────────────────────────────────

    private static int getLocalRadius() {
        try {
            return WpConfig.CHAT_LOCAL_RADIUS.get();
        } catch (Throwable t) {
            return DEFAULT_LOCAL_RADIUS;
        }
    }
}
