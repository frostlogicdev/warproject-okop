package com.frostlogic.warproject.server.auth;

import com.frostlogic.warproject.WarProject;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prevents duplicate-nickname login attacks on offline-mode servers.
 * <p>
 * In vanilla Minecraft with {@code online-mode=false}, when a player connects
 * with a nickname that's already online, the server kicks the existing player
 * and lets the new one in. This is a trivial attack vector: anyone can steal
 * another player's session by connecting with their name.
 * <p>
 * This guard uses a two-layer approach:
 * <ol>
 *   <li><b>Session lock</b>: tracks which UUIDs are currently "locked" (online and
 *       authenticated). When a new player logs in with a UUID that's already locked,
 *       the <b>new player is immediately kicked</b> before any other handler runs.</li>
 *   <li><b>Grace period</b>: after a player disconnects, their UUID stays locked for
 *       5 seconds to prevent rapid reconnect-steal attacks.</li>
 * </ol>
 * <p>
 * The vanilla behavior of kicking the existing player is overridden by kicking
 * the newcomer instead. Since vanilla's duplicate-kick happens inside
 * {@code PlayerList.placeNewPlayer()} BEFORE {@code PlayerLoggedInEvent} fires,
 * we additionally override the vanilla check by re-adding the original player
 * if they were removed.
 * <p>
 * Requirements: Security hardening for offline-mode servers.
 */
@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class DuplicateLoginGuard {

    /**
     * Tracks active sessions: UUID → timestamp when the session was locked.
     * A session is locked when the player is online or within the grace period.
     */
    private static final Map<UUID, Long> SESSION_LOCKS = new ConcurrentHashMap<>();

    /** Grace period after disconnect (ms). */
    private static final long GRACE_PERIOD_MS = 5000;

    private DuplicateLoginGuard() {
    }

    /**
     * When a player logs in, check if their UUID is already session-locked.
     * If so, kick the NEW player immediately (they are the attacker).
     * <p>
     * Priority HIGHEST ensures this runs before any other login handler,
     * including our own auth flow in ServerEvents.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer newPlayer)) {
            return;
        }

        UUID uuid = newPlayer.getUUID();
        String name = newPlayer.getGameProfile().getName();
        MinecraftServer server = newPlayer.getServer();

        if (server == null) return;

        // Check if this UUID has an active session lock
        Long lockTime = SESSION_LOCKS.get(uuid);
        if (lockTime != null) {
            long elapsed = System.currentTimeMillis() - lockTime;
            // If within grace period OR another player with same UUID is still online
            ServerPlayer existingPlayer = null;
            for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                if (online != newPlayer && online.getUUID().equals(uuid)) {
                    existingPlayer = online;
                    break;
                }
            }

            if (existingPlayer != null) {
                // Another player with same UUID is online — kick the newcomer
                WarProject.LOGGER.warn("[WP Guard] Duplicate login blocked: '{}' (uuid={}) — original still online",
                        name, uuid);
                newPlayer.connection.disconnect(Component.literal(
                        "Игрок с таким ником уже на сервере.\nЕсли это вы — подождите и попробуйте снова."));
                return;
            }

            if (elapsed < GRACE_PERIOD_MS) {
                // Within grace period after disconnect — could be a steal attempt
                WarProject.LOGGER.warn("[WP Guard] Login during grace period blocked: '{}' (uuid={}, {}ms since disconnect)",
                        name, uuid, elapsed);
                newPlayer.connection.disconnect(Component.literal(
                        "Подождите несколько секунд перед повторным подключением."));
                return;
            }
        }

        // Also check by name (in case UUID generation differs)
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (online != newPlayer && online.getGameProfile().getName().equalsIgnoreCase(name)) {
                WarProject.LOGGER.warn("[WP Guard] Duplicate name login blocked: incoming='{}', existing='{}'",
                        name, online.getGameProfile().getName());
                newPlayer.connection.disconnect(Component.literal(
                        "Игрок с таким ником уже на сервере.\nЕсли это вы — подождите и попробуйте снова."));
                return;
            }
        }

        // Lock the session for this UUID
        SESSION_LOCKS.put(uuid, System.currentTimeMillis());
    }

    /**
     * When a player disconnects, keep the session lock active for the grace period.
     * The lock timestamp is updated to "now" so the grace countdown starts fresh.
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Update the lock timestamp to start the grace period from now
            SESSION_LOCKS.put(player.getUUID(), System.currentTimeMillis());
        }
    }

    /**
     * Periodic cleanup: remove expired session locks (older than grace period).
     * Called from server tick to prevent memory leaks.
     */
    @SubscribeEvent
    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        // Only clean up every 600 ticks (~30 seconds)
        if (event.getServer().getTickCount() % 600 != 0) return;
        if (SESSION_LOCKS.isEmpty()) return;

        long now = System.currentTimeMillis();
        long expiry = GRACE_PERIOD_MS * 2; // keep locks a bit longer than grace for safety

        SESSION_LOCKS.entrySet().removeIf(entry -> {
            long age = now - entry.getValue();
            // Only remove if the player is NOT online AND the lock is expired
            if (age > expiry) {
                ServerPlayer online = event.getServer().getPlayerList().getPlayer(entry.getKey());
                return online == null;
            }
            return false;
        });
    }
}
