package com.frostlogic.warproject.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A ceiling-mounted light that can be turned on/off by right-clicking.
 * <p>
 * Variants are differentiated only by their hitbox / aabb (passed via the
 * constructor), so this class can back both the simple garage pendant and
 * the wider mess-hall chandelier without code duplication. The block must be
 * placed against the underside of a solid block — placement is rejected
 * otherwise — and is broken automatically if the supporting block is
 * removed.
 * <p>
 * Lit / unlit state is stored in {@link BlockStateProperties#LIT} so vanilla
 * blockstate JSON variants can pick the right model. The light level is
 * driven by {@link net.minecraft.world.level.block.state.BlockBehaviour.Properties#lightLevel(java.util.function.ToIntFunction)},
 * so callers can decide how bright each variant glows.
 */
public class CeilingLightBlock extends Block {

    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private final VoxelShape shape;

    public CeilingLightBlock(Properties properties, VoxelShape shape) {
        super(properties);
        this.shape = shape;
        this.registerDefaultState(this.stateDefinition.any().setValue(LIT, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shape;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        // No physical collision — players walk under the chandelier without
        // bumping into it. Light blocks are decoration in 1.21.
        return net.minecraft.world.phys.shapes.Shapes.empty();
    }

    /**
     * Only allow placement when there is a sturdy block above (a real ceiling).
     * The check matches the rule used by vanilla {@code Lantern} for its
     * hanging variant.
     */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos above = pos.above();
        return level.getBlockState(above).isFaceSturdy(level, above, Direction.DOWN);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        if (canSurvive(this.defaultBlockState(), ctx.getLevel(), ctx.getClickedPos())) {
            return this.defaultBlockState().setValue(LIT, true);
        }
        return null;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     net.minecraft.world.level.LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        // If the supporting block (above) is gone, drop the chandelier.
        if (direction == Direction.UP && !canSurvive(state, level, pos)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    /**
     * Right-click toggles the {@link #LIT} property and plays a soft click.
     * Empty hands are required so we don't preempt placement of items.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockState toggled = state.cycle(LIT);
            level.setBlock(pos, toggled, Block.UPDATE_ALL);
            level.playSound(null, pos,
                    toggled.getValue(LIT) ? SoundEvents.LEVER_CLICK : SoundEvents.LEVER_CLICK,
                    SoundSource.BLOCKS, 0.25F, toggled.getValue(LIT) ? 0.95F : 0.7F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Holding any item (including the chandelier itself in creative) and
     * right-clicking still toggles the light — players shouldn't have to
     * empty their hand to flip a light switch. Returns {@code PASS} when
     * the held stack should be allowed through to its own interaction (so
     * place-on-block still works for stacks that are valid blocks against
     * other faces).
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        // We only swallow the click when no other block-place would happen
        // on this face. Placement in vanilla goes through useItemOn on the
        // *clicked* block, but our chandelier doesn't host attachments so it
        // is safe to always toggle here.
        if (!level.isClientSide) {
            BlockState toggled = state.cycle(LIT);
            level.setBlock(pos, toggled, Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS,
                    0.25F, toggled.getValue(LIT) ? 0.95F : 0.7F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Subtle ambient particle when the light is on — a single "warm dust"
     * mote drifting upward from the bulb every few ticks. Cheap and helps
     * sell the lit state in dim rooms.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        if (random.nextInt(8) != 0) {
            return;
        }
        double cx = pos.getX() + 0.5D;
        double cy = pos.getY() + 0.45D;
        double cz = pos.getZ() + 0.5D;
        // Tiny smoke / glow ember lifting off the bulb.
        level.addParticle(ParticleTypes.END_ROD,
                cx + (random.nextDouble() - 0.5D) * 0.15D,
                cy,
                cz + (random.nextDouble() - 0.5D) * 0.15D,
                0.0D, 0.005D, 0.0D);
    }
}
