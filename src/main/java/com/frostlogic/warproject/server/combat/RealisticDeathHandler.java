package com.frostlogic.warproject.server.combat;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.network.payload.s2c.DeathCinematicPayload;
import com.frostlogic.warproject.server.Faction;
import com.frostlogic.warproject.server.FactionRespawnHandler;
import com.frostlogic.warproject.server.WarPlayerDataStore;
import com.frostlogic.warproject.server.WarPlayerProfile;
import com.frostlogic.warproject.server.WarServerSettings;
import com.frostlogic.warproject.server.SpawnTeleporter;
import com.frostlogic.warproject.server.WarpPoint;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Realistic death system for WarProject.
 * <p>
 * When a player dies:
 * <ol>
 *   <li>Determines if the killing blow was a headshot (projectile hit above eye level)</li>
 *   <li>Sends {@link DeathCinematicPayload} to the dying player's client</li>
 *   <li>Enforces a 60-second respawn delay (player cannot click respawn early)</li>
 *   <li>After the delay, auto-respawns the player at their faction base</li>
 * </ol>
 * <p>
 * Headshot detection: if the damage source is a projectile and its Y position
 * at impact is above the player's eye height, it counts as a headshot. The client
 * renders a more abrupt cinematic (instant blackout) for headshots vs. a slower
 * fade for body shots.
 */
@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class RealisticDeathHandler {

    /** Respawn delay in ticks (60 seconds × 20 ticks/sec). */
    public static final int RESPAWN_DELAY_TICKS = 1200;

    /**
     * Tracks dead players awaiting respawn: UUID → tick when they should respawn.
     */
    private static final Map<UUID, RespawnEntry> PENDING_RESPAWNS = new ConcurrentHashMap<>();

    private RealisticDeathHandler() {
    }

    // ─── Death Event ──────────────────────────────────────────────────────────────

    /**
     * Intercepts player death to send the cinematic payload and schedule delayed respawn.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        DamageSource source = event.getSource();
        boolean headshot = isHeadshot(player, source);

        // Send cinematic payload to the dying player
        PacketDistributor.sendToPlayer(player, new DeathCinematicPayload(headshot, RESPAWN_DELAY_TICKS));

        // Schedule respawn after delay
        long respawnAtTick = player.server.getTickCount() + RESPAWN_DELAY_TICKS;
        PENDING_RESPAWNS.put(player.getUUID(), new RespawnEntry(respawnAtTick, headshot));

        WarProject.LOGGER.debug("[WP Death] Player {} died (headshot={}), respawn scheduled in {}t",
                player.getGameProfile().getName(), headshot, RESPAWN_DELAY_TICKS);
    }

    // ─── Tick — Auto-respawn ──────────────────────────────────────────────────────

    /**
     * On each server tick, checks if any pending respawns have reached their time
     * and forces the respawn.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING_RESPAWNS.isEmpty()) {
            return;
        }

        long currentTick = event.getServer().getTickCount();
        Iterator<Map.Entry<UUID, RespawnEntry>> it = PENDING_RESPAWNS.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<UUID, RespawnEntry> entry = it.next();
            UUID uuid = entry.getKey();
            RespawnEntry respawnEntry = entry.getValue();

            if (currentTick >= respawnEntry.respawnAtTick()) {
                it.remove();

                // Find the player and force respawn
                ServerPlayer player = event.getServer().getPlayerList().getPlayer(uuid);
                if (player != null && player.isDeadOrDying()) {
                    player.respawn();
                }
            }
        }
    }

    // ─── Respawn Event — Prevent Early Respawn ────────────────────────────────────

    /**
     * When a player respawns, remove them from the pending map.
     * The vanilla death screen respawn button is handled client-side by the overlay
     * which hides it until the timer expires.
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PENDING_RESPAWNS.remove(player.getUUID());
        }
    }

    /**
     * Cleanup on disconnect so we don't leak entries.
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PENDING_RESPAWNS.remove(player.getUUID());
        }
    }

    // ─── Public API ───────────────────────────────────────────────────────────────

    /**
     * Returns the remaining respawn delay in ticks for the given player, or 0 if not pending.
     */
    public static int getRemainingDelayTicks(UUID uuid, long currentTick) {
        RespawnEntry entry = PENDING_RESPAWNS.get(uuid);
        if (entry == null) return 0;
        return (int) Math.max(0, entry.respawnAtTick() - currentTick);
    }

    /**
     * Returns whether the given player is currently in the death cinematic (waiting to respawn).
     */
    public static boolean isAwaitingRespawn(UUID uuid) {
        return PENDING_RESPAWNS.containsKey(uuid);
    }

    // ─── Headshot Detection ───────────────────────────────────────────────────────

    /**
     * Determines if the killing blow was a headshot.
     * <p>
     * A headshot is detected when:
     * <ul>
     *   <li>The damage source involves a projectile (arrow, trident, etc.)</li>
     *   <li>The projectile's Y position at impact is above the player's eye height</li>
     * </ul>
     */
    private static boolean isHeadshot(ServerPlayer player, DamageSource source) {
        // Check if damage came from a projectile
        if (source.getDirectEntity() instanceof Projectile projectile) {
            Vec3 projectilePos = projectile.position();
            double playerFeetY = player.getY();
            double eyeHeight = player.getEyeHeight();
            double headThreshold = playerFeetY + eyeHeight - 0.1; // slightly below eye level

            return projectilePos.y >= headThreshold;
        }
        return false;
    }

    // ─── Internal ─────────────────────────────────────────────────────────────────

    private record RespawnEntry(long respawnAtTick, boolean headshot) {
    }
}
