package com.frostlogic.warproject.network;

import com.frostlogic.warproject.WarProject;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent server &rarr; client when a player connects and needs to either register
 * a password (first login on this profile) or log in (returning player). The
 * client opens {@code WarLoginScreen} configured for the corresponding mode.
 *
 * @param register true if the player must set a new password; false if they
 *                 must enter an existing one.
 */
public record OpenLoginScreenPayload(boolean register) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenLoginScreenPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WarProject.MOD_ID, "open_login_screen"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenLoginScreenPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public OpenLoginScreenPayload decode(RegistryFriendlyByteBuf buf) {
            return new OpenLoginScreenPayload(buf.readBoolean());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, OpenLoginScreenPayload payload) {
            buf.writeBoolean(payload.register);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
