package com.frostlogic.warproject.server.wguard;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.WpConfig;
import com.frostlogic.warproject.attachment.PlayerState;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Event-driven WGuard anti-cheat checks.
 * <p>
 * Subscribes to server-side events and runs the following checks:
 * <ul>
 *   <li><b>SPEED</b> — on player tick, compares actual horizontal movement
 *       against the configured maximum blocks/tick</li>
 *   <li><b>FLY</b> — on player tick, detects sustained airborne movement
 *       without flight permission (mayfly/creative/captcha sky-cage)</li>
 *   <li><b>REACH</b> — on attack, measures distance between attacker and target</li>
 *   <li><b>KILL_AURA</b> — tracks attack frequency and head rotation speed</li>
 *   <li><b>FAST_BREAK</b> — measures time between successive block breaks</li>
 *   <li><b>NUKER</b> — detects breaking many different blocks in a short window</li>
 *   <li><b>NO_FALL</b> — detects players who should take fall damage but don't</li>
 * </ul>
 * <p>
 * All checks delegate to {@link WGuardService#flag} which handles VL tracking,
 * audit logging, and threshold actions.
 */
@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class WGuardEventHandler {

    private WGuardEventHandler() {}

    // ─── Per-player tracking state ─────────────────────────────────────────────

    /** Last recorded position per player for speed check. */
    private static final Map<UUID, PosSnapshot> lastPos = new HashMap<>();

    /** Attack timestamps per player for KillAura frequency check. */
    private static final Map<UUID, LongList> attackTimestamps = new HashMap<>();

    /** Last head rotation per player for KillAura rotation check. */
    private static final Map<UUID, Float> lastYaw = new HashMap<>();
    private static final Map<UUID, Float> lastPitch = new HashMap<>();
    private static final Map<UUID, Long> lastRotationTick = new HashMap<>();

    /** Block break timestamps per player for FastBreak/Nuker checks. */
    private static final Map<UUID, LongList> breakTimestamps = new HashMap<>();
    private static final Map<UUID, Integer> breakCount = new HashMap<>();

    /** Consecutive airborne ticks for Fly check. */
    private static final Map<UUID, Integer> airborneTicks = new HashMap<>();

    /** Fall start Y per player for NoFall check. */
    private static final Map<UUID, Double> fallStartY = new HashMap<>();
    private static final Map<UUID, Boolean> wasFalling = new HashMap<>();

    /** WGuardService instance — set during server startup. */
    private static volatile WGuardService service;

    public static void init(WGuardService svc) {
        service = svc;
    }

    // ─── Player Tick — SPEED / FLY / NO_FALL ──────────────────────────────────

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (service == null || !WpConfig.WGUARD_ENABLED.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // Skip frozen-state players (captcha, onboarding)
        PlayerState state = player.getData(WpAttachmentTypes.PLAYER_STATE.get());
        if (state != PlayerState.ACCEPTED && !player.hasPermissions(2)) return;

        UUID uuid = player.getUUID();

        // Decay VL
        long tick = player.server.getTickCount();
        service.decayAll(tick);

        // ── Vehicle exemption: skip movement checks for riding players ──
        boolean isRiding = player.getVehicle() != null;
        boolean isRidingKnownVehicle = isRiding && isKnownVehicle(player.getVehicle());
        boolean skipMovementChecks = isRiding &&
                (WpConfig.WGUARD_SKIP_RIDING_PLAYERS.get() || isRidingKnownVehicle);

        if (!skipMovementChecks) {
            // ── SPEED check ──
            checkSpeed(player, uuid);
            // ── FLY check ──
            checkFly(player, uuid);
            // ── NO_FALL check ──
            checkNoFall(player, uuid);
        } else if (isRidingKnownVehicle) {
            // Still check speed but with a much higher limit for vehicles
            checkSpeedVehicle(player, uuid);
        }
    }

    private static void checkSpeed(ServerPlayer player, UUID uuid) {
        double currentX = player.getX();
        double currentZ = player.getZ();
        PosSnapshot last = lastPos.get(uuid);

        if (last != null) {
            double dx = currentX - last.x;
            double dz = currentZ - last.z;
            double horizontalDist = Math.sqrt(dx * dx + dz * dz);

            // Account for vanilla speed effects
            double maxSpeed = WpConfig.WGUARD_SPEED_MAX_BLOCKS_PER_TICK.get() / 10.0;
            if (player.getAbilities().flying) {
                maxSpeed *= 2.0; // Creative/spectator flight is faster
            }

            // Allow speed effect multiplier
            if (player.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED)) {
                maxSpeed *= 1.5;
            }

            if (horizontalDist > maxSpeed) {
                service.flag(player, WGuardService.CheckType.SPEED,
                        String.format("moved %.2f blocks/tick (max %.2f)", horizontalDist, maxSpeed));
            }
        }

        lastPos.put(uuid, new PosSnapshot(currentX, player.getY(), currentZ));
    }

    private static void checkFly(ServerPlayer player, UUID uuid) {
        // Skip players with legitimate flight
        if (player.getAbilities().mayfly || player.isSpectator() || player.isCreative()) return;
        // Skip captcha sky-cage (NoGravity + CAPTCHA state)
        if (player.isNoGravity()) return;
        // Skip players in vehicles
        if (player.getVehicle() != null) return;

        boolean onGround = player.onGround();
        boolean isInFluid = player.isInWater() || player.isInLava();

        if (!onGround && !isInFluid) {
            int ticks = airborneTicks.merge(uuid, 1, Integer::sum);

            // Check if the player is ascending or hovering without ground/fluid
            PosSnapshot last = lastPos.get(uuid);
            if (last != null && ticks > WpConfig.WGUARD_FLY_VIOLATION_THRESHOLD.get()) {
                double dy = player.getY() - last.y;
                // If ascending or hovering (not falling) for too many ticks
                if (dy >= -0.1) { // Not significantly falling
                    service.flag(player, WGuardService.CheckType.FLY,
                            String.format("airborne %d ticks, dy=%.2f", ticks, dy));
                    airborneTicks.put(uuid, 0);
                }
            }
        } else {
            airborneTicks.remove(uuid);
        }
    }

    private static void checkNoFall(ServerPlayer player, UUID uuid) {
        boolean currentlyFalling = !player.onGround() && !player.isInWater() && !player.isInLava()
                && player.getVehicle() == null && player.getDeltaMovement().y < -0.5;

        if (currentlyFalling && !wasFalling.getOrDefault(uuid, false)) {
            // Started falling — record start Y
            fallStartY.put(uuid, player.getY());
            wasFalling.put(uuid, true);
        }

        if (!currentlyFalling && wasFalling.getOrDefault(uuid, false)) {
            // Landed — check if fall damage should have been taken
            Double startY = fallStartY.get(uuid);
            if (startY != null) {
                double fallDistance = startY - player.getY();
                // Vanilla: fall damage starts at 4 blocks, 1 HP per block after 3
                if (fallDistance > 4.0 && player.getHealth() == player.getMaxHealth()) {
                    // Player fell > 4 blocks but took no damage
                    service.flag(player, WGuardService.CheckType.NO_FALL,
                            String.format("fell %.1f blocks with no damage", fallDistance));
                }
            }
            fallStartY.remove(uuid);
            wasFalling.put(uuid, false);
        }
    }

    // ─── Attack Events — REACH / KILL_AURA ────────────────────────────────────

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(AttackEntityEvent event) {
        if (service == null || !WpConfig.WGUARD_ENABLED.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.hasPermissions(2)) return;

        Entity target = event.getTarget();
        UUID uuid = player.getUUID();

        // ── REACH check ──
        double distance = player.distanceTo(target);
        double maxReach = WpConfig.WGUARD_REACH_MAX_DISTANCE.get() / 10.0;
        if (distance > maxReach) {
            service.flag(player, WGuardService.CheckType.REACH,
                    String.format("attacked at %.1f blocks (max %.1f)", distance, maxReach));
        }

        // ── KILL_AURA frequency check ──
        // Relax limits if player is holding a weapon from a known gun mod
        boolean holdingGunModWeapon = isHoldingWeaponModItem(player);
        int maxAttacks = WpConfig.WGUARD_KILLAURA_MAX_ATTACKS_PER_SECOND.get();
        float maxRotation = WpConfig.WGUARD_KILLAURA_MAX_ROTATION_DEGREES_PER_TICK.get();

        if (holdingGunModWeapon) {
            // Gun mods can fire much faster than melee — use 4x multiplier
            maxAttacks *= 4;
            maxRotation *= 3; // Aiming with mouse while shooting = faster rotation
        }

        long now = System.currentTimeMillis();
        LongList timestamps = attackTimestamps.computeIfAbsent(uuid, k -> new LongList());
        timestamps.add(now);
        // Prune entries older than 1 second
        timestamps.pruneOlder(now - 1000);
        int attacksPerSecond = timestamps.size();
        if (attacksPerSecond > maxAttacks) {
            service.flag(player, WGuardService.CheckType.KILL_AURA,
                    String.format("%d attacks/sec (max %d)%s", attacksPerSecond, maxAttacks,
                            holdingGunModWeapon ? "" : " [melee]"));
        }

        // ── KILL_AURA rotation check ──
        float currentYaw = player.getYRot();
        float currentPitch = player.getXRot();
        Float prevYaw = lastYaw.get(uuid);
        Float prevPitch = lastPitch.get(uuid);
        Long prevTick = lastRotationTick.get(uuid);
        long currentTick = player.server.getTickCount();

        if (prevYaw != null && prevPitch != null && prevTick != null) {
            long tickDelta = currentTick - prevTick;
            if (tickDelta > 0) {
                float yawDelta = Math.abs(angleDelta(prevYaw, currentYaw));
                float pitchDelta = Math.abs(prevPitch - currentPitch);
                float totalRotation = yawDelta + pitchDelta;
                float degreesPerTick = totalRotation / tickDelta;

                if (degreesPerTick > maxRotation) {
                    service.flag(player, WGuardService.CheckType.KILL_AURA,
                            String.format("rotation %.1f°/tick (max %.1f°)%s", degreesPerTick, maxRotation,
                                    holdingGunModWeapon ? "" : " [melee]"));
                }
            }
        }

        lastYaw.put(uuid, currentYaw);
        lastPitch.put(uuid, currentPitch);
        lastRotationTick.put(uuid, currentTick);
    }

    // ─── Block Break — FAST_BREAK / NUKER ──────────────────────────────────────

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (service == null || !WpConfig.WGUARD_ENABLED.get()) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (player.hasPermissions(2)) return;

        UUID uuid = player.getUUID();
        long now = player.server.getTickCount();

        // ── FAST_BREAK check ──
        LongList timestamps = breakTimestamps.computeIfAbsent(uuid, k -> new LongList());
        timestamps.add(now);
        // Keep last 20 breaks
        timestamps.pruneTo(20);

        if (timestamps.size() >= 2) {
            long oldest = timestamps.get(0);
            long newest = timestamps.get(timestamps.size() - 1);
            long span = newest - oldest;
            if (span > 0 && timestamps.size() > span / WpConfig.WGUARD_FASTBREAK_MIN_TICKS.get()) {
                service.flag(player, WGuardService.CheckType.FAST_BREAK,
                        String.format("%d breaks in %d ticks", timestamps.size(), span));
            }
        }

        // ── NUKER check ──
        // Track how many *different* blocks were broken in the last 40 ticks
        int nukerCount = breakCount.merge(uuid, 1, Integer::sum);
        // Reset counter every 40 ticks
        // Simple approach: if count is high relative to time, flag
        if (timestamps.size() >= 5) {
            long span = now - timestamps.get(0);
            if (span < 40 && nukerCount > 5) {
                service.flag(player, WGuardService.CheckType.NUKER,
                        String.format("%d different blocks in %d ticks", nukerCount, span));
                breakCount.put(uuid, 0);
            }
        }
    }

    // ─── Cleanup ───────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID uuid = player.getUUID();
        lastPos.remove(uuid);
        attackTimestamps.remove(uuid);
        lastYaw.remove(uuid);
        lastPitch.remove(uuid);
        lastRotationTick.remove(uuid);
        breakTimestamps.remove(uuid);
        breakCount.remove(uuid);
        airborneTicks.remove(uuid);
        fallStartY.remove(uuid);
        wasFalling.remove(uuid);
        if (service != null) service.removePlayer(uuid);
    }

    @SubscribeEvent
    public static void onPlayerJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // Reset tracking state on join
        UUID uuid = player.getUUID();
        lastPos.put(uuid, new PosSnapshot(player.getX(), player.getY(), player.getZ()));
        airborneTicks.remove(uuid);
        wasFalling.put(uuid, false);
    }

    // ─── Utility ───────────────────────────────────────────────────────────────

    /**
     * Checks if the given entity belongs to a known vehicle mod by inspecting
     * its EntityType's registry namespace.
     */
    private static boolean isKnownVehicle(Entity vehicle) {
        if (vehicle == null) return false;
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType());
        String entityNamespace = key != null ? key.getNamespace() : "";
        for (String ns : WpConfig.WGUARD_VEHICLE_MOD_NAMESPACES.get()) {
            if (entityNamespace.startsWith(ns)) return true;
        }
        return false;
    }

    /**
     * Checks if the player is holding an item from a known weapon/gun mod.
     * Checks both main hand and off hand.
     */
    private static boolean isHoldingWeaponModItem(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        return isWeaponModItem(mainHand) || isWeaponModItem(offHand);
    }

    /**
     * Checks if a single ItemStack belongs to a known weapon mod namespace.
     */
    private static boolean isWeaponModItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String itemNamespace = key != null ? key.getNamespace() : "";
        for (String ns : WpConfig.WGUARD_WEAPON_MOD_NAMESPACES.get()) {
            if (itemNamespace.startsWith(ns)) return true;
        }
        return false;
    }

    /**
     * Speed check with a much higher limit for known vehicle mods.
     */
    private static void checkSpeedVehicle(ServerPlayer player, UUID uuid) {
        double currentX = player.getX();
        double currentZ = player.getZ();
        PosSnapshot last = lastPos.get(uuid);

        if (last != null) {
            double dx = currentX - last.x;
            double dz = currentZ - last.z;
            double horizontalDist = Math.sqrt(dx * dx + dz * dz);

            double maxSpeed = (WpConfig.WGUARD_SPEED_MAX_BLOCKS_PER_TICK.get() / 10.0)
                    * WpConfig.WGUARD_VEHICLE_SPEED_MULTIPLIER.get();

            if (horizontalDist > maxSpeed) {
                service.flag(player, WGuardService.CheckType.SPEED,
                        String.format("vehicle moved %.2f blocks/tick (max %.2f)", horizontalDist, maxSpeed));
            }
        }

        lastPos.put(uuid, new PosSnapshot(currentX, player.getY(), currentZ));
    }

    /** Computes the shortest angular distance between two yaw angles. */
    private static float angleDelta(float a, float b) {
        float delta = Math.abs(a - b) % 360;
        return delta > 180 ? 360 - delta : delta;
    }

    /** Lightweight position snapshot. */
    private record PosSnapshot(double x, double y, double z) {}

    /** Simple long list with pruning support. */
    private static final class LongList {
        private final long[] data = new long[64];
        private int size = 0;

        void add(long value) {
            if (size >= data.length) {
                System.arraycopy(data, 1, data, 0, data.length - 1);
                data[data.length - 1] = value;
            } else {
                data[size++] = value;
            }
        }

        int size() { return size; }

        long get(int index) {
            if (index < 0 || index >= size) throw new IndexOutOfBoundsException(index);
            return data[index];
        }

        void pruneOlder(long threshold) {
            int firstValid = 0;
            while (firstValid < size && data[firstValid] < threshold) firstValid++;
            if (firstValid > 0) {
                System.arraycopy(data, firstValid, data, 0, size - firstValid);
                size -= firstValid;
            }
        }

        void pruneTo(int maxSize) {
            if (size > maxSize) {
                int excess = size - maxSize;
                System.arraycopy(data, excess, data, 0, maxSize);
                size = maxSize;
            }
        }
    }
}
