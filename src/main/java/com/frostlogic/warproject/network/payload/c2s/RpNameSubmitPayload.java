package com.frostlogic.warproject.network.payload.c2s;

import com.frostlogic.warproject.WarProject;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S: Player submits their RP name (first name + surname).
 */
public record RpNameSubmitPayload(String rpName, String rpSurname) implements CustomPacketPayload {
    public static final int MAX_NAME_LENGTH = 32;

    public static final CustomPacketPayload.Type<RpNameSubmitPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WarProject.MOD_ID, "rp_name_submit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RpNameSubmitPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public RpNameSubmitPayload decode(RegistryFriendlyByteBuf buf) {
            return new RpNameSubmitPayload(buf.readUtf(MAX_NAME_LENGTH), buf.readUtf(MAX_NAME_LENGTH));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, RpNameSubmitPayload payload) {
            buf.writeUtf(payload.rpName, MAX_NAME_LENGTH);
            buf.writeUtf(payload.rpSurname, MAX_NAME_LENGTH);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
