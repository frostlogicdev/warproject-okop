package com.frostlogic.warproject.block;

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

/**
 * Firing slot / embrasure — a block with a narrow slit for shooting from cover.
 * Directional: the slit faces the direction the player places it.
 * The 3D model has a real through-hole so you can see through the opening.
 */
public class FiringSlotBlock extends HorizontalDirectionalBlock {
    // Hole: x=4..12, y=6..10, through z=0..16 (North-South axis)
    private static final VoxelShape SHAPE_NS = Shapes.join(
            Shapes.block(),
            Block.box(4, 6, 0, 12, 10, 16),
            BooleanOp.ONLY_FIRST);
    // Hole: z=4..12, y=6..10, through x=0..16 (East-West axis)
    private static final VoxelShape SHAPE_EW = Shapes.join(
            Shapes.block(),
            Block.box(0, 6, 4, 16, 10, 12),
            BooleanOp.ONLY_FIRST);

    public FiringSlotBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        Direction facing = state.getValue(FACING);
        return (facing == Direction.EAST || facing == Direction.WEST) ? SHAPE_EW : SHAPE_NS;
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
