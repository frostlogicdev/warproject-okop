package com.frostlogic.warproject.network.payload.s2c;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.server.militaryid.MilitaryIdData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C: Sends Military ID data snapshot to the client for display in MilitaryIdScreen.
 * Follows the same pattern as {@link PassportSnapshotPayload}.
 * <p>
 * Requirements: 6.1
 * Design: §Network Payloads
 */
public record MilitaryIdSnapshotPayload(MilitaryIdData data) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MilitaryIdSnapshotPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WarProject.MOD_ID, "military_id_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MilitaryIdSnapshotPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MilitaryIdSnapshotPayload decode(RegistryFriendlyByteBuf buf) {
            return new MilitaryIdSnapshotPayload(MilitaryIdData.STREAM_CODEC.decode(buf));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, MilitaryIdSnapshotPayload payload) {
            MilitaryIdData.STREAM_CODEC.encode(buf, payload.data);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
