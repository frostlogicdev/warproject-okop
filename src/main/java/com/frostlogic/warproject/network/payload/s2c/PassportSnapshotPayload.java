package com.frostlogic.warproject.network.payload.s2c;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.server.passport.PassportData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C: Sends passport data snapshot to the client for display in PassportScreen.
 * <p>
 * Requirements: 4.1, 4.2, 4.3
 * Design: §4.2, §6
 */
public record PassportSnapshotPayload(PassportData data) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PassportSnapshotPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WarProject.MOD_ID, "passport_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PassportSnapshotPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public PassportSnapshotPayload decode(RegistryFriendlyByteBuf buf) {
            return new PassportSnapshotPayload(PassportData.STREAM_CODEC.decode(buf));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, PassportSnapshotPayload payload) {
            PassportData.STREAM_CODEC.encode(buf, payload.data);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
