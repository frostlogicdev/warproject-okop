package com.frostlogic.warproject.server.chat;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.WpConfig;
import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.attachment.Role;
import com.frostlogic.warproject.attachment.RpName;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Proximity-based chat routing for WarProject.
 * <p>
 * Intercepts every {@link ServerChatEvent} and replaces the vanilla
 * broadcast (all players) with scope-limited delivery:
 * <ul>
 *   <li><b>LOCAL</b> (default) — only players within a configurable radius
 *       ({@link WpConfig#CHAT_LOCAL_RADIUS}) in the same dimension. Format:
 *       {@code [Звание] Имя Фамилия: текст}.</li>
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

    // ─── Event Subscriber ────────────────────────────────────────────────

    /**
     * Intercepts vanilla chat and routes it through the proximity system.
     * <p>
     * Priority {@code LOW} runs after FreezeService (HIGHEST) and mute checks (HIGH)
     * so we only process messages that are already allowed through.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onServerChat(ServerChatEvent event) {
        ServerPlayer sender = event.getPlayer();

        // Route every message that reaches us through the formatter so the
        // visible chat line is always "[Role] Имя Фамилия: текст" — never the
        // vanilla "<launcherName> ..." form. Players who must not chat at all
        // are already cancelled upstream by FreezeService / mute checks at
        // HIGHEST / HIGH priority, so anything reaching this LOW handler is
        // allowed to be delivered. CANDIDATEs in particular need this so they
        // appear as "[Гражданин] Имя Фамилия: ..." instead of leaking their
        // Mojang launcher name.

        // Cancel vanilla broadcast — we handle delivery ourselves
        event.setCanceled(true);

        // Route as LOCAL chat
        deliverLocal(sender, event.getRawText());
    }

    // ─── Public API ─────────────────────────────────────────────────────────

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

    // ─── Formatting ────────────────────────────────────────────────────────

    /**
     * Builds a localized {@code [Звание] Имя Фамилия: текст} line.
     * The role label is itself a translation key ({@code wp.role.<lowercase>})
     * so client locales render it natively.
     */
    private static MutableComponent formatLocal(ServerPlayer sender, String message) {
        String displayName = displayName(sender);
        Component roleLabel = roleLabel(sender);
        return Component.translatable("wp.chat.local", roleLabel, displayName, message);
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
     * Translates the sender's {@link Role} into a localized label. OPs get a
     * distinct "admin" key so admin messages visually stand out in local chat.
     */
    private static Component roleLabel(ServerPlayer sender) {
        Role role = sender.getData(WpAttachmentTypes.ROLE.get());
        String key = "wp.role." + role.name().toLowerCase(Locale.ROOT);
        MutableComponent label = Component.translatable(key);
        if (role == Role.OP) {
            label = label.withStyle(net.minecraft.ChatFormatting.RED);
        }
        return label;
    }

    /**
     * Computes the player's display name for the chat line. Prefers the RP name;
     * falls back to the Mojang profile name.
     */
    private static String displayName(ServerPlayer player) {
        Optional<RpName> rp = player.getData(WpAttachmentTypes.RP_NAME.get());
        return rp.map(RpName::fullName).orElseGet(() -> player.getGameProfile().getName());
    }

    // ─── Config ───────────────────────────────────────────────────────────

    private static int getLocalRadius() {
        try {
            return WpConfig.CHAT_LOCAL_RADIUS.get();
        } catch (Throwable t) {
            return DEFAULT_LOCAL_RADIUS;
        }
    }
}
