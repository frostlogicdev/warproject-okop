package com.frostlogic.warproject.network.payload.s2c;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.network.payload.AuthMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

/**
 * S2C: Tells the client which auth screen to open (REGISTER or LOGIN) and optional error key.
 * <p>
 * Requirements: 4.1, 4.2
 * Design: §4.2, §6
 */
public record AuthScreenStatePayload(AuthMode mode, @Nullable String errorKey) implements CustomPacketPayload {
    public static final int MAX_ERROR_LENGTH = 128;

    public static final CustomPacketPayload.Type<AuthScreenStatePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WarProject.MOD_ID, "auth_screen_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AuthScreenStatePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AuthScreenStatePayload decode(RegistryFriendlyByteBuf buf) {
            AuthMode mode = AuthMode.STREAM_CODEC.decode(buf);
            boolean hasError = buf.readBoolean();
            String errorKey = hasError ? buf.readUtf(MAX_ERROR_LENGTH) : null;
            return new AuthScreenStatePayload(mode, errorKey);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, AuthScreenStatePayload payload) {
            AuthMode.STREAM_CODEC.encode(buf, payload.mode);
            buf.writeBoolean(payload.errorKey != null);
            if (payload.errorKey != null) {
                buf.writeUtf(payload.errorKey, MAX_ERROR_LENGTH);
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
