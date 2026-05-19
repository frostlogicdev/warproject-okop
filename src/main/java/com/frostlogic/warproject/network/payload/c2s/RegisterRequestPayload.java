package com.frostlogic.warproject.network.payload.c2s;

import com.frostlogic.warproject.WarProject;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S: Player submits registration form with password and confirmation.
 */
public record RegisterRequestPayload(String password, String passwordConfirm) implements CustomPacketPayload {
    public static final int MAX_PASSWORD_LENGTH = 64;

    public static final CustomPacketPayload.Type<RegisterRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WarProject.MOD_ID, "register_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RegisterRequestPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public RegisterRequestPayload decode(RegistryFriendlyByteBuf buf) {
            return new RegisterRequestPayload(buf.readUtf(MAX_PASSWORD_LENGTH), buf.readUtf(MAX_PASSWORD_LENGTH));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, RegisterRequestPayload payload) {
            buf.writeUtf(payload.password, MAX_PASSWORD_LENGTH);
            buf.writeUtf(payload.passwordConfirm, MAX_PASSWORD_LENGTH);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
