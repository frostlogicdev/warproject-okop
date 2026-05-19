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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MedicalTentBlock extends Block {
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

    private static final Map<UUID, Long> HEAL_COOLDOWN = new HashMap<>();
    private static final long COOLDOWN_MS = 3 * 60 * 1000; // 3 minutes

    public MedicalTentBlock(Properties properties) {
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
            if (player.getHealth() >= player.getMaxHealth()) {
                serverPlayer.sendSystemMessage(Component.translatable("message.warproject.medical_tent.full_health"), true);
                return InteractionResult.SUCCESS;
            }

            long currentTime = System.currentTimeMillis();
            long lastHealTime = HEAL_COOLDOWN.getOrDefault(player.getUUID(), 0L);
            long elapsedTime = currentTime - lastHealTime;

            if (elapsedTime >= COOLDOWN_MS) {
                player.setHealth(player.getMaxHealth());
                HEAL_COOLDOWN.put(player.getUUID(), currentTime);
                level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 1.0f, 1.0f);
                serverPlayer.sendSystemMessage(Component.translatable("message.warproject.medical_tent.healed"), true);
            } else {
                long timeLeft = Math.max(1, (COOLDOWN_MS - elapsedTime + 999) / 1000);
                serverPlayer.sendSystemMessage(Component.translatable("message.warproject.medical_tent.cooldown", timeLeft), true);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
