package com.frostlogic.warproject.server.radial;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Server-side raytrace utility for the radial menu system.
 * Performs a raytrace from the initiator's eye position along their view vector,
 * returning the first ServerPlayer hit within {@link #MAX_DISTANCE} blocks.
 * <p>
 * Design: §8.4
 * Requirements: 8.3, 9.2, 14.2
 */
public final class RaytraceUtil {

    public static final double MAX_DISTANCE = 5.0;

    private RaytraceUtil() {
    }

    /**
     * Performs a server-side raytrace from the initiator's eye position along their
     * view vector for up to {@link #MAX_DISTANCE} blocks, looking for player entities.
     *
     * @param initiator the player performing the raytrace
     * @return the first {@link ServerPlayer} hit within range, or {@code null} if none found
     */
    @Nullable
    public static ServerPlayer raytracePlayer(ServerPlayer initiator) {
        Vec3 eye = initiator.getEyePosition(1.0F);
        Vec3 look = initiator.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(MAX_DISTANCE));
        AABB box = initiator.getBoundingBox().expandTowards(look.scale(MAX_DISTANCE)).inflate(0.5);

        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                initiator.level(), initiator, eye, end, box,
                e -> e instanceof ServerPlayer && e != initiator && !e.isSpectator());

        return hit != null && hit.getEntity() instanceof ServerPlayer p ? p : null;
    }
}
