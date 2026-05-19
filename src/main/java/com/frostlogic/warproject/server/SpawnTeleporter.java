package com.frostlogic.warproject.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.RelativeMovement;

import java.util.Optional;
import java.util.Set;

public final class SpawnTeleporter {
    private SpawnTeleporter() {
    }

    public static boolean toSpawn(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        Optional<WarpPoint> wp = WarServerSettings.get().getSpawnPoint();
        if (wp.isPresent() && wp.get().teleport(player)) {
            return true;
        }
        return toWorldSpawn(player);
    }

    public static boolean toWorldSpawn(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return false;
        }
        ServerLevel overworld = player.getServer().overworld();
        if (overworld == null) {
            return false;
        }
        BlockPos shared = overworld.getSharedSpawnPos();
        float yaw = overworld.getSharedSpawnAngle();
        player.teleportTo(overworld,
                shared.getX() + 0.5D,
                shared.getY(),
                shared.getZ() + 0.5D,
                Set.<RelativeMovement>of(),
                yaw,
                0f);
        return true;
    }
}
