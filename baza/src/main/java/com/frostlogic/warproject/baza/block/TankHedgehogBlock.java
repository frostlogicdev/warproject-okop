package com.frostlogic.warproject.baza.block;

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
            Block.box(7, 0, 0, 9, 2, 16),
            Block.box(0, 0, 7, 16, 2, 9),
            Block.box(6, 2, 2, 10, 6, 14),
            Block.box(2, 2, 6, 14, 6, 10),
            Block.box(5, 6, 4, 11, 10, 12),
            Block.box(4, 6, 5, 12, 10, 11),
            Block.box(6, 10, 6, 10, 14, 10));

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
