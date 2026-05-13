package com.frostlogic.warproject.polevoy.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SmallTentBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 1, 16),
            Block.box(0, 1, 0, 5, 8, 16),
            Block.box(11, 1, 0, 16, 8, 16),
            Block.box(2, 8, 0, 14, 11, 16),
            Block.box(5, 11, 0, 11, 14, 16));

    public SmallTentBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.setRespawnPosition(level.dimension(), pos, 0, false, true);
        }
        return InteractionResult.SUCCESS;
    }
}
