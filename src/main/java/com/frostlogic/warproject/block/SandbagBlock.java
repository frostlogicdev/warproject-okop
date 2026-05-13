package com.frostlogic.warproject.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Stackable sandbags (1-4 layers) for building parapets.
 * Each layer adds 4px of height. Provides partial blast protection.
 */
public class SandbagBlock extends Block {
    public static final IntegerProperty LAYERS = IntegerProperty.create("layers", 1, 4);

    private static final VoxelShape[] SHAPES = {
            Block.box(0, 0, 0, 16, 4, 16),
            Block.box(0, 0, 0, 16, 8, 16),
            Block.box(0, 0, 0, 16, 12, 16),
            Block.box(0, 0, 0, 16, 16, 16)
    };

    public SandbagBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LAYERS, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAYERS);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES[state.getValue(LAYERS) - 1];
    }

    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext ctx) {
        return ctx.getItemInHand().is(this.asItem()) && state.getValue(LAYERS) < 4;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState existing = ctx.getLevel().getBlockState(ctx.getClickedPos());
        if (existing.is(this)) {
            return existing.setValue(LAYERS, Math.min(4, existing.getValue(LAYERS) + 1));
        }
        return this.defaultBlockState();
    }
}
