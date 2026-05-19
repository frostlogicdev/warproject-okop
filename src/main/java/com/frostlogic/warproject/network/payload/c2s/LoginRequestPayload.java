package com.frostlogic.warproject.network.payload.c2s;

import com.frostlogic.warproject.WarProject;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S: Player submits login password.
 */
public record LoginRequestPayload(String password) implements CustomPacketPayload {
    public static final int MAX_PASSWORD_LENGTH = 64;

    public static final CustomPacketPayload.Type<LoginRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WarProject.MOD_ID, "login_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LoginRequestPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public LoginRequestPayload decode(RegistryFriendlyByteBuf buf) {
            return new LoginRequestPayload(buf.readUtf(MAX_PASSWORD_LENGTH));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, LoginRequestPayload payload) {
            buf.writeUtf(payload.password, MAX_PASSWORD_LENGTH);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
