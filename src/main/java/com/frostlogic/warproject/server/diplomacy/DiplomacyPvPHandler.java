package com.frostlogic.warproject.server.diplomacy;

import com.frostlogic.warproject.WarProject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Event listener that cancels PvP damage between faction members while a
 * diplomatic truce is active.
 * <p>
 * Subscribes to {@link LivingIncomingDamageEvent} at {@link EventPriority#HIGH}
 * so that the cancellation happens early in the event chain, before combat-tag
 * or other lower-priority handlers process the hit.
 * <p>
 * The handler delegates the actual truce/faction check to
 * {@link DiplomacyService#shouldCancelPvP(ServerPlayer, ServerPlayer)}.
 * <p>
 * Requirements: 10.3
 */
@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class DiplomacyPvPHandler {

    private static @Nullable DiplomacyService diplomacyService;

    private DiplomacyPvPHandler() {
        // static event subscriber — no instantiation
    }

    /**
     * Initializes the handler with the {@link DiplomacyService} instance.
     * Must be called during server startup (from {@code WpCommandRoot.onServerStarted})
     * before any damage events are processed.
     */
    public static void init(DiplomacyService service) {
        diplomacyService = service;
    }

    /**
     * Cancels PvP damage when a truce is active between the attacker's and
     * victim's factions.
     * <p>
     * Only player-vs-player damage is considered. Non-PvP sources (mobs,
     * environment, self-damage) pass through unaffected. The attacker is
     * resolved via {@link DamageSource#getEntity()}, which handles indirect
     * sources like arrows and tridents.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (diplomacyService == null) {
            return;
        }

        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }

        DamageSource source = event.getSource();
        Entity attacker = source.getEntity();
        if (!(attacker instanceof ServerPlayer attackerPlayer)) {
            // Not a PvP source (mob/environment/projectile-without-owner); ignore.
            return;
        }

        if (attackerPlayer == victim) {
            // Self-damage isn't PvP; let it through.
            return;
        }

        if (diplomacyService.shouldCancelPvP(attackerPlayer, victim)) {
            event.setCanceled(true);
        }
    }
}
