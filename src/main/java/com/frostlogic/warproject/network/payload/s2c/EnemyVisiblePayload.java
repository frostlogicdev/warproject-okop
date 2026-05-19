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
 * S2C: Snapshot of currently visible enemy players (those the receiving player
 * is permitted to see on the map — typically only enemies inside the receiver's
 * faction base region).
 * <p>
 * Sent periodically from {@code MapBroadcastService}. Used by
 * {@code WpJourneymapPlugin} to materialise red {@code MarkerOverlay}s on JM.
 * <p>
 * Note: this payload is intentionally separate from {@link AllyPositionsPayload}
 * so the visibility predicate can differ (allies are always shared inside the
 * faction; enemies are gated by region presence + line-of-sight rules).
 * <p>
 * Requirements: 4.1, 4.2 — Design: §6
 */
public record EnemyVisiblePayload(List<Enemy> enemies) implements CustomPacketPayload {

    public static final int MAX_ENEMIES = 256;
    public static final int MAX_NAME_LENGTH = 64;
    public static final int MAX_DIMENSION_PATH_LENGTH = 256;

    public static final CustomPacketPayload.Type<EnemyVisiblePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WarProject.MOD_ID, "enemy_visible"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EnemyVisiblePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public EnemyVisiblePayload decode(RegistryFriendlyByteBuf buf) {
            int n = buf.readVarInt();
            if (n < 0 || n > MAX_ENEMIES) {
                throw new IllegalArgumentException("EnemyVisiblePayload: out-of-range size " + n);
            }
            List<Enemy> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                UUID uuid = buf.readUUID();
                String name = buf.readUtf(MAX_NAME_LENGTH);
                String dim = buf.readUtf(MAX_DIMENSION_PATH_LENGTH);
                int x = buf.readVarInt();
                int y = buf.readVarInt();
                int z = buf.readVarInt();
                out.add(new Enemy(uuid, name, dim, x, y, z));
            }
            return new EnemyVisiblePayload(out);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, EnemyVisiblePayload payload) {
            List<Enemy> enemies = payload.enemies;
            int n = Math.min(enemies.size(), MAX_ENEMIES);
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) {
                Enemy e = enemies.get(i);
                buf.writeUUID(e.uuid());
                buf.writeUtf(e.displayName(), MAX_NAME_LENGTH);
                buf.writeUtf(e.dimensionId(), MAX_DIMENSION_PATH_LENGTH);
                buf.writeVarInt(e.x());
                buf.writeVarInt(e.y());
                buf.writeVarInt(e.z());
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Enemy(UUID uuid, String displayName, String dimensionId, int x, int y, int z) {}
}
