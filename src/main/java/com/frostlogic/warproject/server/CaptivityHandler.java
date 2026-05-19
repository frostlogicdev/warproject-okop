package com.frostlogic.warproject.server;

import com.frostlogic.warproject.WarProject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class CaptivityHandler {
    private CaptivityHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % 40 != 0) {
            return;
        }
        WarPlayerProfile profile = WarPlayerDataStore.get().getOrCreate(player);
        if (!profile.isCaptive()) {
            return;
        }
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1, false, false));
    }
}
