package com.frostlogic.warproject.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Drainage grate — wooden grille placed on the trench floor.
 * Prevents waterlogging slowdown by removing the Slowness effect on contact.
 */
public class DrainageGrateBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 2, 16);

    public DrainageGrateBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (entity instanceof LivingEntity living && !level.isClientSide) {
            living.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        }
        super.stepOn(level, pos, state, entity);
    }
}
