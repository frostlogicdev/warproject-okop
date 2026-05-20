package com.frostlogic.warproject.server.combat;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.attachment.PlayerState;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Cancels PvP damage when either the attacker or the victim has not been
 * accepted into a faction yet.
 * <p>
 * Protected states (no incoming and no outgoing PvP damage):
 * <ul>
 *   <li>{@link PlayerState#NEW}</li>
 *   <li>{@link PlayerState#REGISTERED_PENDING}</li>
 *   <li>{@link PlayerState#LOGIN_PENDING}</li>
 *   <li>{@link PlayerState#CAPTCHA}</li>
 *   <li>{@link PlayerState#RPNAME_REQUIRED}</li>
 *   <li>{@link PlayerState#FACTIONLESS}</li>
 *   <li>{@link PlayerState#CANDIDATE}</li>
 *   <li>{@link PlayerState#CAPTURED}</li>
 * </ul>
 * <p>
 * Only {@link PlayerState#ACCEPTED} on both sides lets the event flow through
 * to {@link com.frostlogic.warproject.server.diplomacy.DiplomacyPvPHandler}
 * (truce check) and combat-tag.
 * <p>
 * Non-PvP damage (mobs, environment, fall, drowning, projectile-without-owner)
 * is left untouched. Self-damage is also passed through.
 * <p>
 * Runs at {@link EventPriority#HIGHEST} so cancellation happens before the
 * truce check (HIGH) and combat-tag (NORMAL) get a chance to process the hit.
 */
@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class NewbieProtectionHandler {

    private NewbieProtectionHandler() {
        // static event subscriber — no instantiation
    }

    /**
     * Cancels PvP damage when at least one side is not in
     * {@link PlayerState#ACCEPTED}.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }

        DamageSource source = event.getSource();
        Entity attackerEntity = source.getEntity();
        if (!(attackerEntity instanceof ServerPlayer attacker)) {
            // Non-PvP source (mob, environment, projectile without owner). Pass through.
            return;
        }

        if (attacker == victim) {
            // Self-damage is not PvP. Pass through.
            return;
        }

        if (!isAccepted(victim) || !isAccepted(attacker)) {
            event.setCanceled(true);
        }
    }

    /**
     * Returns true if the given player has been accepted into a faction.
     */
    private static boolean isAccepted(ServerPlayer player) {
        PlayerState state = player.getData(WpAttachmentTypes.PLAYER_STATE.get());
        return state == PlayerState.ACCEPTED;
    }
}
