package com.frostlogic.warproject.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Authentication screen mode sent from server to client.
 * Determines which screen (Register or Login) the client should display.
 */
public enum AuthMode {
    REGISTER,
    LOGIN;

    public static final StreamCodec<ByteBuf, AuthMode> STREAM_CODEC =
            ByteBufCodecs.idMapper(i -> values()[i], AuthMode::ordinal);
}
