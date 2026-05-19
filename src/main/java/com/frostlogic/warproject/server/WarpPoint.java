package com.frostlogic.warproject.server;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.Set;

public record WarpPoint(ResourceKey<Level> dimension, double x, double y, double z, float yaw, float pitch) {
    public static WarpPoint of(ServerPlayer player) {
        return new WarpPoint(player.serverLevel().dimension(), player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
    }

    public Optional<ServerLevel> resolveLevel(MinecraftServer server) {
        ServerLevel level = server.getLevel(dimension);
        return Optional.ofNullable(level);
    }

    public boolean teleport(ServerPlayer player) {
        ServerLevel level = player.getServer() != null ? player.getServer().getLevel(dimension) : null;
        if (level == null) {
            return false;
        }
        player.teleportTo(level, x, y, z, Set.of(), yaw, pitch);
        return true;
    }

    public double distanceSq(ServerPlayer player) {
        if (!player.serverLevel().dimension().equals(dimension)) {
            return Double.POSITIVE_INFINITY;
        }
        double dx = player.getX() - x;
        double dy = player.getY() - y;
        double dz = player.getZ() - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public String serialize() {
        return dimension.location() + " " + x + " " + y + " " + z + " " + yaw + " " + pitch;
    }

    public static Optional<WarpPoint> deserialize(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }

        String[] parts = raw.trim().split("\\s+");
        if (parts.length < 4) {
            return Optional.empty();
        }

        try {
            ResourceLocation location = ResourceLocation.parse(parts[0]);
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, location);
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = parts.length > 4 ? Float.parseFloat(parts[4]) : 0f;
            float pitch = parts.length > 5 ? Float.parseFloat(parts[5]) : 0f;
            return Optional.of(new WarpPoint(dimension, x, y, z, yaw, pitch));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }
}
