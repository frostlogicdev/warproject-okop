package com.frostlogic.warproject.server.command.nodes;

import com.frostlogic.warproject.WarProject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime state shared by the admin moderation commands {@code /wp freeze} and
 * {@code /wp vanish}.
 * <p>
 * Unlike {@link com.frostlogic.warproject.server.lifecycle.FreezeService} (which
 * freezes players based on lifecycle state — auth, captcha, rpname), this service
 * tracks <em>admin-imposed</em> freeze and vanish flags by UUID.
 * <p>
 * Both flags are in-memory only (cleared on server restart) — admin moderation
 * actions are short-lived by intent and do not need to persist across restarts.
 * Audit entries for FREEZE / UNFREEZE / VANISH / UNVANISH are written by the
 * commands themselves to the {@code audit_log} table.
 * <p>
 * Requirements: 10.2 (/wp freeze, /wp vanish), 10.5
 * Design: §7
 */
@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class AdminModerationState {

    private static final Set<UUID> ADMIN_FROZEN = ConcurrentHashMap.newKeySet();
    /** Maps UUIDs of currently vanished players to the GameType they had before vanish. */
    private static final Map<UUID, GameType> VANISHED = new ConcurrentHashMap<>();

    private AdminModerationState() {
        // utility class — no instantiation
    }

    // ─── Freeze API ───────────────────────────────────────────────────────────────

    /** Returns whether the player is currently admin-frozen. */
    public static boolean isFrozen(UUID uuid) {
        return ADMIN_FROZEN.contains(uuid);
    }

    /**
     * Toggles the admin-freeze flag for the given player.
     *
     * @return {@code true} if the player is now frozen, {@code false} if unfrozen
     */
    public static boolean toggleFreeze(UUID uuid) {
        if (ADMIN_FROZEN.contains(uuid)) {
            ADMIN_FROZEN.remove(uuid);
            return false;
        }
        ADMIN_FROZEN.add(uuid);
        return true;
    }

    // ─── Vanish API ───────────────────────────────────────────────────────────────

    /** Returns whether the player is currently vanished. */
    public static boolean isVanished(UUID uuid) {
        return VANISHED.containsKey(uuid);
    }

    /**
     * Toggles vanish for the given player. When enabling, switches the player to
     * {@link GameType#SPECTATOR} and remembers the previous gamemode so it can be
     * restored on disable. When disabling, restores the previous gamemode.
     *
     * @return {@code true} if the player is now vanished, {@code false} if unvanished
     */
    public static boolean toggleVanish(ServerPlayer player) {
        UUID uuid = player.getUUID();
        GameType prev = VANISHED.remove(uuid);
        if (prev != null) {
            // Was vanished — restore previous gamemode.
            player.setGameMode(prev);
            return false;
        }
        VANISHED.put(uuid, player.gameMode.getGameModeForPlayer());
        player.setGameMode(GameType.SPECTATOR);
        return true;
    }

    // ─── Event hooks ──────────────────────────────────────────────────────────────

    /**
     * Zeros velocity of admin-frozen players each tick so they cannot move.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (isFrozen(player.getUUID())) {
            player.setDeltaMovement(0.0, 0.0, 0.0);
            player.hurtMarked = true;
            player.fallDistance = 0.0F;
        }
    }

    /**
     * Cancels incoming damage for admin-frozen players. Vanished players already
     * receive no damage by virtue of being in spectator mode.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isFrozen(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    /**
     * Clears state for a player when they log out. Freeze flag is cleared so
     * that the player is not silently frozen on rejoin; vanish, similarly, does
     * not survive a reconnect (the prev-gamemode mapping no longer applies once
     * the {@link ServerPlayer} instance is gone).
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID uuid = player.getUUID();
            ADMIN_FROZEN.remove(uuid);
            VANISHED.remove(uuid);
        }
    }
}
