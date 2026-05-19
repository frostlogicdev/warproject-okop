package com.frostlogic.warproject.network.payload.s2c;

import com.frostlogic.warproject.WarProject;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C: Sends captcha state to the client (code to display, remaining time, attempts left).
 */
public record CaptchaStatePayload(String code, int remainingSeconds, int attemptsLeft) implements CustomPacketPayload {
    public static final int MAX_CODE_LENGTH = 16;

    public static final CustomPacketPayload.Type<CaptchaStatePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WarProject.MOD_ID, "captcha_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CaptchaStatePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public CaptchaStatePayload decode(RegistryFriendlyByteBuf buf) {
            return new CaptchaStatePayload(buf.readUtf(MAX_CODE_LENGTH), buf.readVarInt(), buf.readVarInt());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, CaptchaStatePayload payload) {
            buf.writeUtf(payload.code, MAX_CODE_LENGTH);
            buf.writeVarInt(payload.remainingSeconds);
            buf.writeVarInt(payload.attemptsLeft);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
