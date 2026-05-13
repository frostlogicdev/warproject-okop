package com.frostlogic.warproject.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class CommandTentBlock extends BaseEntityBlock {
    public static final MapCodec<CommandTentBlock> CODEC = simpleCodec(CommandTentBlock::new);
    // Visual outline
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);
    // Collision - just the floor so players can walk inside
    private static final VoxelShape COLLISION = Block.box(0, 0, 0, 16, 1, 16);

    public CommandTentBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<CommandTentBlock> codec() { return CODEC; }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CommandTentBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

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
        if (!level.isClientSide && player instanceof ServerPlayer) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof CommandTentBlockEntity tent) { player.openMenu(tent); }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof CommandTentBlockEntity tent) { Containers.dropContents(level, pos, tent); }
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
