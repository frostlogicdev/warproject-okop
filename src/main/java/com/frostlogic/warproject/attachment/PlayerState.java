package com.frostlogic.warproject.attachment;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * Player lifecycle state within the WarProject mod.
 * Represents the finite state machine described in design §5.1.
 */
public enum PlayerState implements StringRepresentable {
    NEW("new"),
    REGISTERED_PENDING("registered_pending"),
    LOGIN_PENDING("login_pending"),
    CAPTCHA("captcha"),
    RPNAME_REQUIRED("rpname_required"),
    FACTIONLESS("factionless"),
    CANDIDATE("candidate"),
    ACCEPTED("accepted"),
    CAPTURED("captured");

    public static final Codec<PlayerState> CODEC = StringRepresentable.fromEnum(PlayerState::values);
    public static final StreamCodec<ByteBuf, PlayerState> STREAM_CODEC =
            ByteBufCodecs.idMapper(i -> values()[i], PlayerState::ordinal);

    private final String serializedName;

    PlayerState(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
