package com.frostlogic.warproject.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FiringSlotBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<FiringSlotBlock> CODEC = simpleCodec(FiringSlotBlock::new);

    private static final VoxelShape SHAPE_NS = Shapes.join(
            Shapes.block(),
            Block.box(4, 6, 0, 12, 10, 16),
            BooleanOp.ONLY_FIRST);
    private static final VoxelShape SHAPE_EW = Shapes.join(
            Shapes.block(),
            Block.box(0, 6, 4, 16, 10, 12),
            BooleanOp.ONLY_FIRST);

    public FiringSlotBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<FiringSlotBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        Direction facing = state.getValue(FACING);
        return (facing == Direction.EAST || facing == Direction.WEST) ? SHAPE_EW : SHAPE_NS;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) { return true; }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) { return 1.0F; }
}
