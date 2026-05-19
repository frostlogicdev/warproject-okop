package com.frostlogic.warproject.network.payload.s2c;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.attachment.FactionId;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C: Static snapshot of all faction base regions, sent once on player login.
 * <p>
 * Used by {@code WpJourneymapPlugin} to draw a coloured {@code PolygonOverlay}
 * around each base on the JM minimap and fullscreen map (green = Zarnavia,
 * dark gray = Chernogryad). The polygons are persistent — JM keeps showing them
 * once added, regardless of player chunk distance.
 * <p>
 * Requirements: 4.1, 4.2 — Design: §6
 */
public record FactionBasesPayload(List<Base> bases) implements CustomPacketPayload {

    public static final int MAX_BASES = 32;
    public static final int MAX_DIMENSION_PATH_LENGTH = 256;

    public static final CustomPacketPayload.Type<FactionBasesPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WarProject.MOD_ID, "faction_bases"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FactionBasesPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public FactionBasesPayload decode(RegistryFriendlyByteBuf buf) {
            int n = buf.readVarInt();
            if (n < 0 || n > MAX_BASES) {
                throw new IllegalArgumentException("FactionBasesPayload: out-of-range size " + n);
            }
            List<Base> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                FactionId faction = FactionId.STREAM_CODEC.decode(buf);
                String dim = buf.readUtf(MAX_DIMENSION_PATH_LENGTH);
                int minX = buf.readVarInt();
                int minY = buf.readVarInt();
                int minZ = buf.readVarInt();
                int maxX = buf.readVarInt();
                int maxY = buf.readVarInt();
                int maxZ = buf.readVarInt();
                out.add(new Base(faction, dim, minX, minY, minZ, maxX, maxY, maxZ));
            }
            return new FactionBasesPayload(out);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, FactionBasesPayload payload) {
            List<Base> bases = payload.bases;
            int n = Math.min(bases.size(), MAX_BASES);
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) {
                Base b = bases.get(i);
                FactionId.STREAM_CODEC.encode(buf, b.faction());
                buf.writeUtf(b.dimensionId(), MAX_DIMENSION_PATH_LENGTH);
                buf.writeVarInt(b.minX());
                buf.writeVarInt(b.minY());
                buf.writeVarInt(b.minZ());
                buf.writeVarInt(b.maxX());
                buf.writeVarInt(b.maxY());
                buf.writeVarInt(b.maxZ());
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * One base region as an axis-aligned bounding box. The Y range is preserved
     * for completeness but JM only renders the XZ rectangle (it is a 2D map).
     */
    public record Base(FactionId faction, String dimensionId,
                       int minX, int minY, int minZ,
                       int maxX, int maxY, int maxZ) {}
}
