package com.frostlogic.warproject.network.payload.c2s;

import com.frostlogic.warproject.WarProject;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S: Player submits captcha code.
 */
public record CaptchaSubmitPayload(String code) implements CustomPacketPayload {
    public static final int MAX_CODE_LENGTH = 16;

    public static final CustomPacketPayload.Type<CaptchaSubmitPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WarProject.MOD_ID, "captcha_submit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CaptchaSubmitPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public CaptchaSubmitPayload decode(RegistryFriendlyByteBuf buf) {
            return new CaptchaSubmitPayload(buf.readUtf(MAX_CODE_LENGTH));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, CaptchaSubmitPayload payload) {
            buf.writeUtf(payload.code, MAX_CODE_LENGTH);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
