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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class TankHedgehogBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(6, 0, 0, 10, 3, 16),
            Block.box(0, 0, 6, 16, 3, 10),
            Block.box(4, 3, 2, 12, 8, 14),
            Block.box(2, 3, 4, 14, 8, 12),
            Block.box(5, 8, 5, 11, 14, 11));

    public TankHedgehogBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (entity instanceof LivingEntity) {
            entity.makeStuckInBlock(state, new Vec3(0.3, 0.3, 0.3));
            if (!level.isClientSide && level.getRandom().nextInt(15) == 0) {
                entity.hurt(level.damageSources().cactus(), 2.0f);
            }
        }
    }
}
