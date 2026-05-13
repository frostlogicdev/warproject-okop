package com.frostlogic.warproject.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Stackable sandbags (1-4 layers) for building parapets.
 * Each layer adds a row of individual bag shapes in a brick-like pattern.
 * Bags have a rounded top bulge for realism.
 */
public class SandbagBlock extends Block {
    public static final IntegerProperty LAYERS = IntegerProperty.create("layers", 1, 4);

    // Collision shapes matched to model heights
    // Layer 1: row at y 0-5 (body 4px + bulge 1px)
    // Layer 2: rows up to y 10
    // Layer 3: rows up to y 14
    // Layer 4: full block y 16
    private static final VoxelShape SHAPE_1 = Shapes.or(
            Block.box(0, 0, 0, 8, 5, 15),
            Block.box(8, 0, 1, 16, 5, 16));
    private static final VoxelShape SHAPE_2 = Shapes.or(
            SHAPE_1,
            Block.box(0, 5, 1, 8, 10, 16),
            Block.box(8, 5, 0, 16, 10, 15));
    private static final VoxelShape SHAPE_3 = Shapes.or(
            SHAPE_2,
            Block.box(0, 10, 0, 8, 14, 15),
            Block.box(8, 10, 1, 16, 14, 16));
    private static final VoxelShape SHAPE_4 = Shapes.or(
            SHAPE_3,
            Block.box(0, 14, 1, 8, 16, 16),
            Block.box(8, 14, 0, 16, 16, 15));

    private static final VoxelShape[] SHAPES = { SHAPE_1, SHAPE_2, SHAPE_3, SHAPE_4 };

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
