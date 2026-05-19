package com.frostlogic.warproject.network;

import com.frostlogic.warproject.WarProject;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent client &rarr; server when the player submits the WarLoginScreen form.
 *
 * @param register true if this is a registration submission (set new password),
 *                 false if it is a login attempt (verify existing password).
 * @param password raw password text. Server is responsible for hashing/verifying;
 *                 we never log this payload's content.
 */
public record SubmitPasswordPayload(boolean register, String password) implements CustomPacketPayload {
    /** Hard limit on the password length the server is willing to accept (defence in depth). */
    public static final int MAX_PASSWORD_LENGTH = 64;

    public static final CustomPacketPayload.Type<SubmitPasswordPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WarProject.MOD_ID, "submit_password"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SubmitPasswordPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SubmitPasswordPayload decode(RegistryFriendlyByteBuf buf) {
            boolean register = buf.readBoolean();
            String password = buf.readUtf(MAX_PASSWORD_LENGTH);
            return new SubmitPasswordPayload(register, password);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, SubmitPasswordPayload payload) {
            buf.writeBoolean(payload.register);
            buf.writeUtf(payload.password, MAX_PASSWORD_LENGTH);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
