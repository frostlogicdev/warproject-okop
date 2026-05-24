package com.frostlogic.warproject.server.lifecycle;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.attachment.PlayerState;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Makes {@link PlayerState#FACTIONLESS} players invisible to each other
 * in the choice hall so that each player sees only themselves and the NPCs.
 * <p>
 * Uses {@code ServerPlayer.setInvisible(true)} which is the vanilla mechanism
 * for entity invisibility (no model, no name tag for other players). The
 * invisible player can still move, interact with NPCs, and see the world
 * normally — other players simply cannot see them.
 * <p>
 * Visibility is restored as soon as the player transitions out of FACTIONLESS
 * (e.g. when they choose a faction and become CANDIDATE).
 * <p>
 * Design: §5.1 — choice hall isolation
 */
@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class ChoiceHallVisibilityHandler {

    /** Cooldown (in ticks) between visibility-refresh checks per player. */
    private static final int REFRESH_INTERVAL_TICKS = 10; // ~0.5 seconds — fast convergence after faction choice

    private ChoiceHallVisibilityHandler() {
        // static utility — no instantiation
    }

    // ─── Event Subscribers ────────────────────────────────────────────────────────

    /**
     * On login, apply invisibility if the player is already FACTIONLESS
     * (e.g. they disconnected before choosing a faction and are reconnecting).
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        applyVisibility(player);
    }

    /**
     * On respawn (after death or returning from the End), re-apply invisibility
     * if the player is still FACTIONLESS.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        applyVisibility(player);
    }

    /**
     * Periodic refresh — catches state changes that don't fire their own event
     * (e.g. admin override, legacy pipeline) and corrects any desync.
     * Runs at LOW priority so it doesn't interfere with gameplay tick handlers.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // Throttle to avoid per-tick overhead
        if (player.tickCount % REFRESH_INTERVAL_TICKS != 0) {
            return;
        }
        applyVisibility(player);
    }

    // ─── Internal Helpers ─────────────────────────────────────────────────────────

    /**
     * Sets the player invisible if they are FACTIONLESS, or visible otherwise.
     * <p>
     * Only updates when the flag actually changes to avoid unnecessary
     * entity metadata packets.
     */
    private static void applyVisibility(ServerPlayer player) {
        PlayerState state = player.getData(WpAttachmentTypes.PLAYER_STATE.get());
        boolean shouldBeInvisible = (state == PlayerState.FACTIONLESS);

        if (shouldBeInvisible != player.isInvisible()) {
            player.setInvisible(shouldBeInvisible);
            WarProject.LOGGER.debug("[WarProject] ChoiceHall visibility: {} {} → invisible={}",
                    player.getGameProfile().getName(), state.getSerializedName(), shouldBeInvisible);
        }
    }
}
