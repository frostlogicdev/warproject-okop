package com.frostlogic.warproject.block;

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
import net.minecraft.world.phys.shapes.VoxelShape;

public class SmallTentBlock extends Block {
    // Visual outline - full block for targeting
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);
    // Collision - just the floor so players can walk inside
    private static final VoxelShape COLLISION = Block.box(0, 0, 0, 16, 1, 16);

    public SmallTentBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return COLLISION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            // forced=true: skip vanilla respawn-block validation (tent isn't a BedBlock/RespawnAnchor)
            serverPlayer.setRespawnPosition(level.dimension(), pos, 0, true, true);
        }
        return InteractionResult.SUCCESS;
    }
}
