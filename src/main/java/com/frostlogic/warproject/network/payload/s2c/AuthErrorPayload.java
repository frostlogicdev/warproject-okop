package com.frostlogic.warproject.network.payload.s2c;

import com.frostlogic.warproject.WarProject;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C: Sends a localizable error to the client (i18n key + arguments).
 */
public record AuthErrorPayload(String i18nKey, List<String> args) implements CustomPacketPayload {
    public static final int MAX_KEY_LENGTH = 128;
    public static final int MAX_ARG_LENGTH = 128;

    public static final CustomPacketPayload.Type<AuthErrorPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WarProject.MOD_ID, "auth_error"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AuthErrorPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AuthErrorPayload decode(RegistryFriendlyByteBuf buf) {
            String key = buf.readUtf(MAX_KEY_LENGTH);
            int count = buf.readVarInt();
            List<String> args = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                args.add(buf.readUtf(MAX_ARG_LENGTH));
            }
            return new AuthErrorPayload(key, args);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, AuthErrorPayload payload) {
            buf.writeUtf(payload.i18nKey, MAX_KEY_LENGTH);
            buf.writeVarInt(payload.args.size());
            for (String arg : payload.args) {
                buf.writeUtf(arg, MAX_ARG_LENGTH);
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
