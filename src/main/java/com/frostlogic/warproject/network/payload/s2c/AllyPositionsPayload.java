package com.frostlogic.warproject.network.payload.s2c;

import com.frostlogic.warproject.WarProject;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * S2C: Snapshot of all ally players that the receiving client should render
 * on the JourneyMap minimap and fullscreen map.
 * <p>
 * Sent on a periodic tick from {@code MapBroadcastService}. Each entry is a
 * tuple of {UUID, displayName, dimension, x, y, z}.
 * <p>
 * Used by {@code WpJourneymapPlugin} to materialise/refresh
 * {@code MarkerOverlay}s in the green faction colour.
 * <p>
 * Requirements: 4.1, 4.2 — Design: §6
 */
public record AllyPositionsPayload(List<Ally> allies) implements CustomPacketPayload {

    public static final int MAX_ALLIES = 256;
    public static final int MAX_NAME_LENGTH = 64;
    public static final int MAX_DIMENSION_PATH_LENGTH = 256;

    public static final CustomPacketPayload.Type<AllyPositionsPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WarProject.MOD_ID, "ally_positions"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AllyPositionsPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AllyPositionsPayload decode(RegistryFriendlyByteBuf buf) {
            int n = buf.readVarInt();
            if (n < 0 || n > MAX_ALLIES) {
                throw new IllegalArgumentException("AllyPositionsPayload: out-of-range size " + n);
            }
            List<Ally> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                UUID uuid = buf.readUUID();
                String name = buf.readUtf(MAX_NAME_LENGTH);
                String dim = buf.readUtf(MAX_DIMENSION_PATH_LENGTH);
                int x = buf.readVarInt();
                int y = buf.readVarInt();
                int z = buf.readVarInt();
                out.add(new Ally(uuid, name, dim, x, y, z));
            }
            return new AllyPositionsPayload(out);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, AllyPositionsPayload payload) {
            List<Ally> allies = payload.allies;
            int n = Math.min(allies.size(), MAX_ALLIES);
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) {
                Ally a = allies.get(i);
                buf.writeUUID(a.uuid());
                buf.writeUtf(a.displayName(), MAX_NAME_LENGTH);
                buf.writeUtf(a.dimensionId(), MAX_DIMENSION_PATH_LENGTH);
                buf.writeVarInt(a.x());
                buf.writeVarInt(a.y());
                buf.writeVarInt(a.z());
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * One ally entry — the receiving client renders this as a marker on JM.
     *
     * @param uuid          stable player UUID; used as the JM overlay id so updates replace prior markers
     * @param displayName   name to show as the marker label (typically the in-game name)
     * @param dimensionId   the dimension's resource location, e.g. {@code "minecraft:overworld"}
     * @param x             block X
     * @param y             block Y
     * @param z             block Z
     */
    public record Ally(UUID uuid, String displayName, String dimensionId, int x, int y, int z) {}
}
