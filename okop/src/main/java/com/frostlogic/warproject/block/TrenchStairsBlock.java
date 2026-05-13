package com.frostlogic.warproject.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Trench stairs — quick ascent/descent without jumping.
 * Stair-shaped collision with smooth step-up.
 */
public class TrenchStairsBlock extends HorizontalDirectionalBlock {
    private static final VoxelShape SHAPE_NORTH = Shapes.or(
            Block.box(0, 0, 8, 16, 8, 16),
            Block.box(0, 8, 0, 16, 16, 8));
    private static final VoxelShape SHAPE_SOUTH = Shapes.or(
            Block.box(0, 0, 0, 16, 8, 8),
            Block.box(0, 8, 8, 16, 16, 16));
    private static final VoxelShape SHAPE_EAST = Shapes.or(
            Block.box(0, 0, 0, 8, 8, 16),
            Block.box(8, 8, 0, 16, 16, 16));
    private static final VoxelShape SHAPE_WEST = Shapes.or(
            Block.box(8, 0, 0, 16, 8, 16),
            Block.box(0, 8, 0, 8, 16, 16));

    public TrenchStairsBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SHAPE_SOUTH;
            case EAST -> SHAPE_EAST;
            case WEST -> SHAPE_WEST;
            default -> SHAPE_NORTH;
        };
    }
}
