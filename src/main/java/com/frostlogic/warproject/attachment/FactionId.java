package com.frostlogic.warproject.attachment;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * Faction identifier for the two opposing sides.
 * <p>
 * Each faction has a color (used for NPC names, TAB prefixes, etc.) and a
 * translation key for the display name (resolved via {@code Component.translatable}).
 * <p>
 * Requirements: 6.1, 6.2
 * Design: §3
 */
public enum FactionId implements StringRepresentable {
    ZARNAVIA("zarnavia", ChatFormatting.GREEN, "wp.faction.zarnavia"),
    CHERNOGRYAD("chernogryad", ChatFormatting.DARK_GRAY, "wp.faction.chernogryad");

    public static final Codec<FactionId> CODEC = StringRepresentable.fromEnum(FactionId::values);
    public static final StreamCodec<ByteBuf, FactionId> STREAM_CODEC =
            ByteBufCodecs.idMapper(i -> values()[i], FactionId::ordinal);

    private final String serializedName;
    private final ChatFormatting color;
    private final String displayNameKey;

    FactionId(String serializedName, ChatFormatting color, String displayNameKey) {
        this.serializedName = serializedName;
        this.color = color;
        this.displayNameKey = displayNameKey;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    /**
     * Returns the chat color associated with this faction.
     */
    public ChatFormatting color() {
        return color;
    }

    /**
     * Returns the translation key for the faction's display name.
     * Use with {@code Component.translatable(factionId.displayNameKey())}.
     */
    public String displayNameKey() {
        return displayNameKey;
    }
}
