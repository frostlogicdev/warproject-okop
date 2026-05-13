package com.frostlogic.warproject.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Trench lantern — dim light source (level 5) that does not attract mobs.
 * Emits subtle smoke particles for atmosphere.
 */
public class TrenchLanternBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 10, 12);

    public TrenchLanternBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) == 0) {
            double x = pos.getX() + 0.5;
            double y = pos.getY() + 0.7;
            double z = pos.getZ() + 0.5;
            level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0, 0.02, 0.0);
        }
    }
}
