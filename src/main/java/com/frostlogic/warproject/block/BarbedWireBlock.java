package com.frostlogic.warproject.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BarbedWireBlock extends Block {
    /**
     * Visual outline + collision shape — height matches the cutout cross
     * model (8 px). Wider than tall so entities walking up to the wire from
     * any side hit the edge before clipping into the centre.
     */
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 8, 15);

    public BarbedWireBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        // Slightly shorter collision than visual outline so jumping mobs aren't
        // perched on top of the visual top of the wire — they actually clip
        // into the barbs and trigger entityInside damage.
        return SHAPE;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (entity instanceof LivingEntity) {
            entity.makeStuckInBlock(state, new Vec3(0.25, 0.05, 0.25));
            if (!level.isClientSide && level.getRandom().nextInt(20) == 0) {
                entity.hurt(level.damageSources().cactus(), 1.0f);
            }
        }
    }
}
