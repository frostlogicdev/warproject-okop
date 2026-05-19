package com.frostlogic.warproject.network.payload.c2s;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.server.radial.RadialMenuItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * C2S: Player selects an action from the radial menu.
 * <p>
 * Requirements: 4.1, 4.2, 4.3
 * Design: §4.2, §6
 */
public record RadialActionPayload(UUID targetUuid, RadialMenuItem action) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RadialActionPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WarProject.MOD_ID, "radial_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RadialActionPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public RadialActionPayload decode(RegistryFriendlyByteBuf buf) {
            return new RadialActionPayload(buf.readUUID(), RadialMenuItem.STREAM_CODEC.decode(buf));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, RadialActionPayload payload) {
            buf.writeUUID(payload.targetUuid);
            RadialMenuItem.STREAM_CODEC.encode(buf, payload.action);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
