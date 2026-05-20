package com.frostlogic.warproject.server.combat;

import com.frostlogic.warproject.WarProject;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Sends a localized death notice to the killer and the victim only.
 * <p>
 * Pairs with the {@code showDeathMessages = false} game-rule enforced by
 * {@link com.frostlogic.warproject.server.SoftRpGameRulesHandler}: vanilla
 * broadcasts every death to the entire server, which is noisy and
 * RP-breaking on a 10–30 player military server. Instead, only the two
 * players directly involved see who killed whom; bystanders learn through
 * voice / proximity chat, exactly like in real combat.
 * <p>
 * The handler reuses the vanilla {@link LivingEntity#getCombatTracker()}
 * death message so the translation, weapon name, and kill-streak metadata
 * stay identical to vanilla — only the audience changes.
 * <p>
 * Non-PvP deaths (mobs, environment, fall, drowning) still notify the
 * victim privately, so they understand why they died, but nobody else.
 * <p>
 * Runs at {@link EventPriority#NORMAL} on {@link LivingDeathEvent} which
 * fires once per death and is never cancelled by us — we read, we don't
 * mutate.
 */
@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class CustomDeathMessageHandler {

    private CustomDeathMessageHandler() {
        // static event subscriber — no instantiation
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }

        Component vanillaMessage;
        try {
            vanillaMessage = victim.getCombatTracker().getDeathMessage();
        } catch (Throwable t) {
            // CombatTracker can occasionally throw if the entity has no last-hurt info;
            // fall back to a generic key so the players still see *something*.
            WarProject.LOGGER.debug("[WarProject] CombatTracker.getDeathMessage failed for {}: {}",
                    victim.getGameProfile().getName(), t.toString());
            vanillaMessage = Component.translatable("death.attack.generic", victim.getDisplayName());
        }

        // Always notify the victim privately so they understand the cause.
        victim.sendSystemMessage(vanillaMessage);

        // If the killer is a different player, notify them too.
        DamageSource source = event.getSource();
        Entity killerEntity = source != null ? source.getEntity() : null;
        if (killerEntity instanceof ServerPlayer killer && killer != victim) {
            killer.sendSystemMessage(vanillaMessage);
        }
    }
}
