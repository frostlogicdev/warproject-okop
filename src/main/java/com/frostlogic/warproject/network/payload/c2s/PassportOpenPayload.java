package com.frostlogic.warproject.network.payload.c2s;

import com.frostlogic.warproject.WarProject;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S: Player requests to open their passport from a specific inventory slot.
 */
public record PassportOpenPayload(int slotIndex) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PassportOpenPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WarProject.MOD_ID, "passport_open"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PassportOpenPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public PassportOpenPayload decode(RegistryFriendlyByteBuf buf) {
            return new PassportOpenPayload(buf.readVarInt());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, PassportOpenPayload payload) {
            buf.writeVarInt(payload.slotIndex);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
