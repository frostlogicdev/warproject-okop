package com.frostlogic.warproject.network.payload.s2c;

import com.frostlogic.warproject.WarProject;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C: Broadcasts a single command/squad marker to the receiving client.
 * <p>
 * Sent when a marker is set, refreshed on login (so newcomers see active markers),
 * and re-broadcast every time a new marker replaces the old one. Pairs with
 * {@link FactionMarkerClearPayload} (sent on TTL expiry / explicit clear).
 * <p>
 * The {@code scope} field tells the client whether to label the marker with
 * "Командование" (FACTION) or "Отделение N" (SUBDIVISION). The {@code scopeKey}
 * is either the faction id name or the subdivision DB id (as String) — the
 * client uses it as a stable id for the JM overlay.
 */
public record FactionMarkerActivePayload(
        Scope scope,
        String scopeKey,
        String dimensionId,
        int x,
        int y,
        int z,
        long expiresAtEpochMs,
        String ownerName
) implements CustomPacketPayload {

    public static final int MAX_KEY_LENGTH = 64;
    public static final int MAX_DIM_LENGTH = 256;
    public static final int MAX_NAME_LENGTH = 64;

    public enum Scope {
        FACTION, SUBDIVISION;

        static Scope decode(int ord) { return values()[ord]; }
    }

    public static final CustomPacketPayload.Type<FactionMarkerActivePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WarProject.MOD_ID, "faction_marker_active"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FactionMarkerActivePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public FactionMarkerActivePayload decode(RegistryFriendlyByteBuf buf) {
            Scope scope = Scope.decode(buf.readVarInt());
            String scopeKey = buf.readUtf(MAX_KEY_LENGTH);
            String dim = buf.readUtf(MAX_DIM_LENGTH);
            int x = buf.readVarInt();
            int y = buf.readVarInt();
            int z = buf.readVarInt();
            long expires = buf.readVarLong();
            String owner = buf.readUtf(MAX_NAME_LENGTH);
            return new FactionMarkerActivePayload(scope, scopeKey, dim, x, y, z, expires, owner);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, FactionMarkerActivePayload p) {
            buf.writeVarInt(p.scope.ordinal());
            buf.writeUtf(p.scopeKey, MAX_KEY_LENGTH);
            buf.writeUtf(p.dimensionId, MAX_DIM_LENGTH);
            buf.writeVarInt(p.x);
            buf.writeVarInt(p.y);
            buf.writeVarInt(p.z);
            buf.writeVarLong(p.expiresAtEpochMs);
            buf.writeUtf(p.ownerName, MAX_NAME_LENGTH);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
