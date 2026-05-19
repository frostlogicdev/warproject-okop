package com.frostlogic.warproject.server.chat;

import com.frostlogic.warproject.attachment.Role;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side filter that decides which players see a given chat line scope
 * (local / commander-only / general / GC).
 * <p>
 * <em>Placeholder.</em> The full implementation — radius-limited local chat,
 * faction-internal command chat, and the GC channel ties into
 * {@link GeneralChatService} — lands once the wider chat-scoping work is
 * scheduled. The single utility method below ({@link #isGeneralChatRecipient})
 * is provided up-front so other code paths (e.g. broadcast hooks) can reuse
 * the role gate without importing {@link GeneralChatService} directly.
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
}
