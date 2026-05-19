package com.frostlogic.warproject.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class SupplyCrateBlock extends BaseEntityBlock {
    public static final MapCodec<SupplyCrateBlock> CODEC = simpleCodec(SupplyCrateBlock::new);
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING =
            HorizontalDirectionalBlock.FACING;

    /**
     * Visual + collision outline of the crate. Fills the full block in X/Z
     * so the bottom face flushes against the supporting block (and the
     * occlusion shape derived from this gives the floor proper shadow,
     * fixing the see-through-floor bug).
     */
    private static final net.minecraft.world.phys.shapes.VoxelShape SHAPE =
            Block.box(0, 0, 0, 16, 9, 16);

    public SupplyCrateBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<SupplyCrateBlock> codec() { return CODEC; }

    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,
                                                                  net.minecraft.world.level.BlockGetter level,
                                                                  BlockPos pos,
                                                                  net.minecraft.world.phys.shapes.CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getCollisionShape(BlockState state,
                                                                            net.minecraft.world.level.BlockGetter level,
                                                                            BlockPos pos,
                                                                            net.minecraft.world.phys.shapes.CollisionContext ctx) {
        return SHAPE;
    }

    /**
     * Block bottom completely covers the supporting block, so the light
     * engine should treat it as opaque from below. Without overriding this
     * method, {@link BaseEntityBlock} falls through to the default
     * implementation that effectively returns {@link Shapes#empty()},
     * which is what made the floor "see-through" before.
     */
    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getOcclusionShape(BlockState state,
                                                                            net.minecraft.world.level.BlockGetter level,
                                                                            BlockPos pos) {
        return SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SupplyCrateBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof SupplyCrateBlockEntity crate) { player.openMenu(crate); }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof SupplyCrateBlockEntity crate) {
                Containers.dropContents(level, pos, crate);
                level.updateNeighbourForOutputSignal(pos, this);
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
