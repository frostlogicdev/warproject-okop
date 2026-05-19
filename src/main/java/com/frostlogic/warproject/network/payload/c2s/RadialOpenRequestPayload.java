package com.frostlogic.warproject.network.payload.c2s;

import com.frostlogic.warproject.WarProject;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * C2S: Player requests to open radial menu targeting another player.
 */
public record RadialOpenRequestPayload(UUID lookingAtUuid, float yaw, float pitch) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RadialOpenRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WarProject.MOD_ID, "radial_open_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RadialOpenRequestPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public RadialOpenRequestPayload decode(RegistryFriendlyByteBuf buf) {
            return new RadialOpenRequestPayload(buf.readUUID(), buf.readFloat(), buf.readFloat());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, RadialOpenRequestPayload payload) {
            buf.writeUUID(payload.lookingAtUuid);
            buf.writeFloat(payload.yaw);
            buf.writeFloat(payload.pitch);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
