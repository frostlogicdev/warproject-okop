package com.frostlogic.warproject.polevoy.block;

import com.frostlogic.warproject.polevoy.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class MedicalTentBlockEntity extends BlockEntity {
    private int tickCounter = 0;

    public MedicalTentBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MEDICAL_TENT.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, MedicalTentBlockEntity be) {
        if (++be.tickCounter >= 60) {
            be.tickCounter = 0;
            level.getEntitiesOfClass(Player.class, new AABB(pos).inflate(5.0))
                    .forEach(p -> p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 80, 0)));
        }
    }
}
