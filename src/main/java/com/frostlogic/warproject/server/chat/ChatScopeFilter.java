package com.frostlogic.warproject.server.chat;

import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.attachment.Role;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Server-side filter that decides which players see a given chat line scope
 * (local / faction / commander / GC).
 * <p>
 * Provides static predicates used by {@link ProximityChatService} and other
 * callers to test whether a specific player qualifies as a recipient for a
 * given scope without enumerating the entire audience.
 * <p>
 * Requirements: 15.3
 * Design: §7
 */
public final class ChatScopeFilter {

    private ChatScopeFilter() {
        // utility class
    }

    /**
     * Returns {@code true} iff the given online player is eligible to receive
     * GeneralChat messages — i.e. role &ge; {@link Role#COMMANDER}.
     * <p>
     * Mirrors the membership predicate of {@link GeneralChatService#recipients}
     * for callers that need to test a single player rather than enumerate the
     * whole audience.
     */
    public static boolean isGeneralChatRecipient(ServerPlayer player) {
        return GeneralChatService.hasRequiredRole(player);
    }

    /**
     * Returns {@code true} iff the given player is in the same faction as the
     * sender and would receive a FACTION-scope message.
     */
    public static boolean isFactionRecipient(ServerPlayer player, FactionId faction) {
        Optional<FactionId> playerFaction = player.getData(WpAttachmentTypes.FACTION.get());
        return playerFaction.isPresent() && playerFaction.get() == faction;
    }

    /**
     * Returns {@code true} iff the given player is a commander+ in the same
     * faction and would receive a COMMANDER-scope message.
     */
    public static boolean isCommanderRecipient(ServerPlayer player, FactionId faction) {
        if (!isFactionRecipient(player, faction)) return false;
        Role role = player.getData(WpAttachmentTypes.ROLE.get());
        return role.atLeast(Role.COMMANDER);
    }
}
