package com.frostlogic.warproject.network.payload.c2s;

import com.frostlogic.warproject.WarProject;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S: Player presses {@code Z} on the JM map and confirms a TTL → the chosen
 * world block (raycasted client-side) is sent here so the server can validate
 * permissions and broadcast the marker to teammates.
 * <p>
 * Permission rules enforced server-side:
 * <ul>
 *     <li>SOLDIER + assigned to a subdivision → marker visible to that subdivision only.</li>
 *     <li>COMMANDER / GENERAL → marker visible to the whole faction.</li>
 *     <li>Anything else (CANDIDATE, no faction) → request rejected.</li>
 * </ul>
 * TTL is capped server-side at 600 seconds (10 minutes).
 */
public record SetFactionMarkerPayload(int x, int y, int z, int ttlSeconds) implements CustomPacketPayload {

    /** Hard cap on TTL. Anything larger is clamped server-side. */
    public static final int MAX_TTL_SECONDS = 600;

    public static final CustomPacketPayload.Type<SetFactionMarkerPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WarProject.MOD_ID, "set_faction_marker"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetFactionMarkerPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SetFactionMarkerPayload decode(RegistryFriendlyByteBuf buf) {
            return new SetFactionMarkerPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, SetFactionMarkerPayload p) {
            buf.writeVarInt(p.x);
            buf.writeVarInt(p.y);
            buf.writeVarInt(p.z);
            buf.writeVarInt(p.ttlSeconds);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
