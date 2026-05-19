package com.frostlogic.warproject.network;

import com.frostlogic.warproject.WarProject;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent server &rarr; client to report the outcome of a {@code SubmitPasswordPayload}.
 * On {@code success=true} the client closes the login screen and resumes normal
 * play; on failure the screen displays {@code message} and clears its input.
 */
public record LoginResultPayload(boolean success, String message) implements CustomPacketPayload {
    public static final int MAX_MESSAGE_LENGTH = 200;

    public static final CustomPacketPayload.Type<LoginResultPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WarProject.MOD_ID, "login_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LoginResultPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public LoginResultPayload decode(RegistryFriendlyByteBuf buf) {
            boolean success = buf.readBoolean();
            String message = buf.readUtf(MAX_MESSAGE_LENGTH);
            return new LoginResultPayload(success, message);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, LoginResultPayload payload) {
            buf.writeBoolean(payload.success);
            buf.writeUtf(payload.message == null ? "" : payload.message, MAX_MESSAGE_LENGTH);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
