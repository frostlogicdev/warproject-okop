package com.frostlogic.warproject.server.chat;

import net.minecraft.util.StringRepresentable;

/**
 * Scopes for WarProject chat messages.
 * <p>
 * Each scope determines the set of recipients who see the message:
 * <ul>
 *   <li>{@link #LOCAL} — players within a configurable radius (default 50 blocks)</li>
 *   <li>{@link #FACTION} — all online members of the sender's faction</li>
 *   <li>{@link #COMMANDER} — commanders+ of the sender's faction only</li>
 *   <li>{@link #GC} — all commanders+ across both factions (via {@link GeneralChatService})</li>
 * </ul>
 * <p>
 * The default scope for vanilla {@code ServerChatEvent} is {@link #LOCAL}.
 * Other scopes are accessed through commands: {@code /wp chat} (FACTION),
 * {@code /wp ao} (COMMANDER), {@code /wp generalchat} (GC).
 * <p>
 * Requirements: 15.1–15.3
 * Design: §7
 */
public enum ChatScope implements StringRepresentable {
    LOCAL("local"),
    FACTION("faction"),
    COMMANDER("commander"),
    GC("gc");

    public static final StringRepresentable.StringRepresentableCodec<ChatScope> CODEC =
            StringRepresentable.fromEnum(ChatScope::values);

    private final String serializedName;

    ChatScope(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
