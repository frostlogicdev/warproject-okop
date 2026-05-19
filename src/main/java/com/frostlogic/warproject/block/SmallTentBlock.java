package com.frostlogic.warproject.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
    // Visual outline - full block for targeting
    private static final VoxelShape SHAPE = Block.box(-16, 0, -16, 32, 24, 32);
    // Collision - just the floor so players can walk inside
    private static final VoxelShape COLLISION = Shapes.or(
            Block.box(-16, 0, -16, 32, 1, 32),
            Block.box(-16, 0, 28, 32, 22, 32),
            Block.box(-16, 0, -16, -12, 22, 32),
            Block.box(28, 0, -16, 32, 22, 32),
            Block.box(-16, 0, -16, -2, 18, -12),
            Block.box(18, 0, -16, 32, 18, -12));

    public SmallTentBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
                if (level.dimension().equals(player.getRespawnDimension()) && pos.equals(player.getRespawnPosition())) {
                    player.setRespawnPosition(level.dimension(), null, 0, false, false);
                    player.sendSystemMessage(Component.translatable("message.warproject.small_tent.respawn_reset"), true);
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
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
            serverPlayer.setRespawnPosition(level.dimension(), pos, serverPlayer.getYRot(), true, false);
            serverPlayer.sendSystemMessage(Component.translatable("message.warproject.small_tent.respawn_set"), true);
            level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.8f, 1.0f);
        }
        return InteractionResult.SUCCESS;
    }
}
