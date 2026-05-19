package com.frostlogic.warproject.server.lifecycle;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.attachment.PlayerState;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Set;

/**
 * Blocks all player actions (movement, damage, interactions, chat, commands)
 * for players in frozen lifecycle states.
 * <p>
 * Frozen states: {@link PlayerState#NEW}, {@link PlayerState#REGISTERED_PENDING},
 * {@link PlayerState#LOGIN_PENDING}, {@link PlayerState#CAPTCHA},
 * {@link PlayerState#RPNAME_REQUIRED}.
 * <p>
 * Only whitelisted commands are allowed per state (see {@link #allowsCommand}).
 * <p>
 * Requirements: 2.2, 3.2, 5.1, 5.2, 21.2
 * Design: §11 SP-8
 */
// FreezeService is now active — the new auth pipeline (WGuardService → CaptchaService →
// PlayerLifecycleService) is wired via ServerPayloadHandler and ServiceRegistry.
@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class FreezeService {

    /**
     * Set of states that require full player freeze.
     */
    private static final Set<PlayerState> FROZEN_STATES = Set.of(
            PlayerState.NEW,
            PlayerState.REGISTERED_PENDING,
            PlayerState.LOGIN_PENDING,
            PlayerState.CAPTCHA,
            PlayerState.RPNAME_REQUIRED
    );

    private FreezeService() {
        // static utility — no instantiation
    }

    // ─── Public API ───────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the given state requires the player to be frozen
     * (all actions blocked except whitelisted commands).
     *
     * @param state the current player lifecycle state
     * @return true if the player should be frozen
     */
    public static boolean shouldCancel(PlayerState state) {
        return FROZEN_STATES.contains(state);
    }

    /**
     * Returns {@code true} if the given command is allowed for a player in the
     * specified state.
     * <p>
     * Whitelist rules:
     * <ul>
     *   <li>{@code NEW}, {@code REGISTERED_PENDING}, {@code LOGIN_PENDING} — no commands allowed
     *       (authentication is handled via network packets, not commands)</li>
     *   <li>{@code CAPTCHA} — only {@code "wp captcha"}</li>
     *   <li>{@code RPNAME_REQUIRED} — only {@code "wp rpname"}</li>
     * </ul>
     * For non-frozen states, all commands are allowed.
     *
     * @param cmdName the full command string (without leading slash)
     * @param state   the current player lifecycle state
     * @return true if the command is permitted
     */
    public static boolean allowsCommand(String cmdName, PlayerState state) {
        if (!shouldCancel(state)) {
            return true;
        }
        return switch (state) {
            case NEW, REGISTERED_PENDING, LOGIN_PENDING -> false;
            case CAPTCHA -> cmdName.equals("wp captcha") || cmdName.startsWith("wp captcha ");
            case RPNAME_REQUIRED -> cmdName.equals("wp rpname") || cmdName.startsWith("wp rpname ");
            default -> false;
        };
    }

    // ─── Event Subscribers ────────────────────────────────────────────────────────

    /**
     * Blocks movement for frozen players by zeroing delta movement every tick.
     * <p>
     * Exception: CAPTCHA-state players managed by the new CaptchaService are
     * suspended in a sky-cage with NoGravity + flight. Zeroing their delta
     * movement would conflict with the NoGravity suspension, so we skip
     * movement zeroing for them — all other freeze effects (chat, damage,
     * interaction blocks) still apply.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (isFrozen(player)) {
            // Skip movement zeroing for CAPTCHA players in the sky-cage
            // (CaptchaService manages their position via NoGravity + flight).
            PlayerState state = player.getData(WpAttachmentTypes.PLAYER_STATE.get());
            if (state != PlayerState.CAPTCHA || !player.isNoGravity()) {
                player.setDeltaMovement(0.0, 0.0, 0.0);
                player.hurtMarked = true;
            }
            player.fallDistance = 0.0F;
        }
    }

    /**
     * Blocks all player-entity interactions (right-click on entities).
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player && isFrozen(player)) {
            event.setCanceled(true);
        }
    }

    /**
     * Blocks specific entity interactions (right-click on specific part of entity).
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getEntity() instanceof ServerPlayer player && isFrozen(player)) {
            event.setCanceled(true);
        }
    }

    /**
     * Blocks right-click on blocks for frozen players.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && isFrozen(player)) {
            event.setCanceled(true);
        }
    }

    /**
     * Blocks right-click item usage for frozen players.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player && isFrozen(player)) {
            event.setCanceled(true);
        }
    }

    /**
     * Blocks left-click on blocks for frozen players.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && isFrozen(player)) {
            event.setCanceled(true);
        }
    }

    /**
     * Blocks incoming damage to frozen players (they cannot be hurt).
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isFrozen(player)) {
            event.setCanceled(true);
        }
    }

    /**
     * Blocks block breaking for frozen players.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && isFrozen(player)) {
            event.setCanceled(true);
        }
    }

    /**
     * Blocks block placement for frozen players.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isFrozen(player)) {
            event.setCanceled(true);
        }
    }

    /**
     * Blocks container opening for frozen players.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer player && isFrozen(player)) {
            // PlayerContainerEvent.Open is not cancelable in NeoForge,
            // so we close the container immediately after it opens.
            player.closeContainer();
        }
    }

    /**
     * Blocks chat messages from frozen players.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerChat(ServerChatEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    /**
     * Blocks commands that are not whitelisted for the player's current state.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCommand(CommandEvent event) {
        var source = event.getParseResults().getContext().getSource();
        if (source.getEntity() instanceof ServerPlayer player && isFrozen(player)) {
            PlayerState state = player.getData(WpAttachmentTypes.PLAYER_STATE.get());
            String input = event.getParseResults().getReader().getString();
            // Strip leading slash if present
            String cmdName = input.startsWith("/") ? input.substring(1) : input;
            if (!allowsCommand(cmdName, state)) {
                event.setCanceled(true);
            }
        }
    }

    /**
     * Blocks game mode changes for frozen players.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onGameModeChange(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isFrozen(player)) {
            event.setCanceled(true);
        }
    }

    // ─── Internal Helpers ─────────────────────────────────────────────────────────

    /**
     * Checks whether the given player is in a frozen state by reading the
     * {@link WpAttachmentTypes#PLAYER_STATE} attachment.
     */
    private static boolean isFrozen(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        PlayerState state = serverPlayer.getData(WpAttachmentTypes.PLAYER_STATE.get());
        return shouldCancel(state);
    }
}
