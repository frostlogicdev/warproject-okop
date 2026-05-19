package com.frostlogic.warproject.network.payload.s2c;

import com.frostlogic.warproject.WarProject;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C: Tell the receiving client to drop the marker identified by
 * {@code (scope, scopeKey)}. Sent on TTL expiry, on owner-set replace, or on
 * scope removal (player kicked from subdivision, faction reset, etc).
 */
public record FactionMarkerClearPayload(
        FactionMarkerActivePayload.Scope scope,
        String scopeKey
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FactionMarkerClearPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WarProject.MOD_ID, "faction_marker_clear"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FactionMarkerClearPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public FactionMarkerClearPayload decode(RegistryFriendlyByteBuf buf) {
            return new FactionMarkerClearPayload(
                    FactionMarkerActivePayload.Scope.values()[buf.readVarInt()],
                    buf.readUtf(FactionMarkerActivePayload.MAX_KEY_LENGTH)
            );
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, FactionMarkerClearPayload p) {
            buf.writeVarInt(p.scope.ordinal());
            buf.writeUtf(p.scopeKey, FactionMarkerActivePayload.MAX_KEY_LENGTH);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
