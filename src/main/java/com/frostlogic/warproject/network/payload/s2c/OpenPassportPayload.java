package com.frostlogic.warproject.network.payload.s2c;

import com.frostlogic.warproject.WarProject;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C: Tells the client to open the passport screen.
 * Sent after server validates the PassportOpenPayload request.
 */
public record OpenPassportPayload(boolean success) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenPassportPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WarProject.MOD_ID, "open_passport"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenPassportPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public OpenPassportPayload decode(RegistryFriendlyByteBuf buf) {
            return new OpenPassportPayload(buf.readBoolean());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, OpenPassportPayload payload) {
            buf.writeBoolean(payload.success);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
