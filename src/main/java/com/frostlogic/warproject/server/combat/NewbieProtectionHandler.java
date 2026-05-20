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
 * accepted into a faction yet — BUT ONLY WHEN {@link #ENABLED} IS TRUE.
 * <p>
 * War Project's design choice is "RP from the very first minute": newly
 * registered players are not given a 30-minute immunity bubble; the entire
 * world is hot from the moment they spawn. Therefore the handler is shipped
 * with {@code ENABLED = false} and short-circuits before any state lookup.
 * The class stays in the codebase so flipping the constant (or migrating it
 * to {@code WpConfig}) restores the legacy behaviour instantly.
 * <p>
 * When ENABLED, protected states (no incoming and no outgoing PvP damage)
 * are: {@link PlayerState#NEW}, {@code REGISTERED_PENDING}, {@code LOGIN_PENDING},
 * {@code CAPTCHA}, {@code RPNAME_REQUIRED}, {@code FACTIONLESS},
 * {@code CANDIDATE}, {@code CAPTURED}. Only {@link PlayerState#ACCEPTED} on
 * both sides lets the event flow through to the truce check and combat-tag.
 * <p>
 * Non-PvP damage (mobs, environment, fall, drowning, projectile-without-owner)
 * is always left untouched. Self-damage is also always passed through.
 * <p>
 * Runs at {@link EventPriority#HIGHEST} so cancellation (when enabled) happens
 * before the truce check (HIGH) and combat-tag (NORMAL) get a chance to
 * process the hit.
 */
@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class NewbieProtectionHandler {

    /**
     * Master kill-switch for the entire newbie protection feature. Flip to
     * {@code true} to restore the legacy "only ACCEPTED players can PvP"
     * behaviour. Kept as a class-level constant rather than a config option to
     * make the design decision visible in code review — changing the rule
     * should be a deliberate code change, not a TOML tweak.
     */
    private static final boolean ENABLED = false;

    private NewbieProtectionHandler() {
        // static event subscriber — no instantiation
    }

    /**
     * Cancels PvP damage when at least one side is not in
     * {@link PlayerState#ACCEPTED} — only when {@link #ENABLED} is true.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!ENABLED) {
            return;
        }

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
