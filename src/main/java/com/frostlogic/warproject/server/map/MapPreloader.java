package com.frostlogic.warproject.server.map;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side map preloader for JourneyMap.
 * <p>
 * When activated via {@code /wp mapfill <radius>}, teleports the executing player
 * in a spiral pattern across the world surface, forcing chunk loading. JourneyMap
 * (running on the player's client) will render each chunk as it loads, building
 * up the full map.
 * <p>
 * The player is set invisible and invulnerable during the process. Movement is
 * done at ~2 chunks/tick (every 10 ticks = 0.5s per jump) to give JM time to
 * render each area.
 * <p>
 * Usage: {@code /wp mapfill 500} — fills a 500-block radius around current position.
 * Cancel: {@code /wp mapfill stop}
 */
@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class MapPreloader {

    private static final int TICKS_PER_JUMP = 10; // 0.5 seconds between jumps
    private static final int CHUNK_STEP = 16;     // move 1 chunk at a time

    @Nullable
    private static PreloadSession activeSession = null;

    /**
     * Queue of players waiting for an auto-preload session. Only one player
     * can be teleport-flown at a time (the routine yanks the player around the
     * world), so additional candidates wait their turn here.
     */
    private static final Deque<UUID> pendingAutoQueue = new ArrayDeque<>();
    /** Set of UUIDs already enqueued or actively running, prevents double-queue. */
    private static final Set<UUID> enqueuedOrActive = new HashSet<>();

    private MapPreloader() {
    }

    /**
     * Queues an automatic preload for the given player at the given radius.
     * <p>
     * Unlike {@link #start(ServerPlayer, int)} this is a "silent" variant —
     * fewer chat messages, marks {@code MAP_PRELOAD_DONE} attachment when
     * complete so reconnects don't repeat the spiral, and queues behind any
     * currently active session instead of refusing.
     */
    public static void enqueueAuto(ServerPlayer player, int radius) {
        UUID uuid = player.getUUID();
        if (enqueuedOrActive.contains(uuid)) {
            return; // already in queue / running
        }
        if (player.getExistingData(WpAttachmentTypes.MAP_PRELOAD_DONE.get()).orElse(false)) {
            return; // already preloaded historically
        }
        enqueuedOrActive.add(uuid);
        pendingAutoQueue.add(uuid);
        player.sendSystemMessage(Component.literal(String.format(
                "[WP Map] Карта будет прогружена автоматически (радиус %d блоков). Не выходите из игры.",
                radius)));
        WarProject.LOGGER.info("[WP Map] Auto preload queued for {} (radius={}, queue size now {})",
                player.getGameProfile().getName(), radius, pendingAutoQueue.size());
        // Stash the requested radius on the player as a transient marker —
        // we re-read it when the queue actually starts so it works even if
        // config changes mid-flight.
        pendingRadius.put(uuid, radius);
    }

    private static final java.util.Map<UUID, Integer> pendingRadius = new java.util.HashMap<>();

    /**
     * Starts a map preload session for the given player.
     *
     * @param player the player who will be teleported around
     * @param radius radius in blocks from the center to preload
     */
    public static void start(ServerPlayer player, int radius) {
        startInternal(player, radius, false);
    }

    private static void startInternal(ServerPlayer player, int radius, boolean auto) {
        if (activeSession != null) {
            if (!auto) {
                player.sendSystemMessage(Component.literal("[WP Map] Прогрузка уже запущена. Используйте /wp mapfill stop для отмены."));
            }
            return;
        }

        BlockPos center = player.blockPosition();
        Deque<BlockPos> positions = generateSpiralPositions(center, radius);

        activeSession = new PreloadSession(
                player.getUUID(),
                positions,
                center,
                player.serverLevel(),
                0,
                auto
        );

        // Make player invisible and invulnerable
        player.setInvisible(true);
        player.getAbilities().invulnerable = true;
        player.getAbilities().flying = true;
        player.getAbilities().mayfly = true;
        player.onUpdateAbilities();

        int totalJumps = positions.size();
        int estimatedSeconds = totalJumps * TICKS_PER_JUMP / 20;
        if (auto) {
            player.sendSystemMessage(Component.literal(String.format(
                    "[WP Map] Прогрузка карты началась. Радиус: %d блоков, ~%d сек. Не выходите.",
                    radius, estimatedSeconds)));
        } else {
            player.sendSystemMessage(Component.literal(String.format(
                    "[WP Map] Прогрузка карты запущена. Радиус: %d блоков, ~%d точек, ~%d сек.",
                    radius, totalJumps, estimatedSeconds)));
        }

        WarProject.LOGGER.info("[WP Map] Preload started{} for {} — radius={}, points={}",
                auto ? " (auto)" : "",
                player.getGameProfile().getName(), radius, totalJumps);
    }

    /**
     * Stops the active preload session.
     */
    public static void stop(@Nullable ServerPlayer player) {
        if (activeSession == null) {
            if (player != null) {
                player.sendSystemMessage(Component.literal("[WP Map] Нет активной прогрузки."));
            }
            return;
        }

        // Teleport back to center and restore abilities
        if (player != null) {
            player.teleportTo(activeSession.level,
                    activeSession.center.getX() + 0.5,
                    activeSession.center.getY(),
                    activeSession.center.getZ() + 0.5,
                    player.getYRot(), player.getXRot());
            restorePlayer(player);
            player.sendSystemMessage(Component.literal("[WP Map] Прогрузка остановлена."));
        }

        enqueuedOrActive.remove(activeSession.playerUuid);
        activeSession = null;
    }

    /**
     * Returns whether a preload session is currently active.
     */
    public static boolean isActive() {
        return activeSession != null;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        // ── Drain the auto-preload queue when no session is active ─────────
        if (activeSession == null && !pendingAutoQueue.isEmpty()) {
            UUID next = pendingAutoQueue.peek();
            ServerPlayer nextPlayer = event.getServer().getPlayerList().getPlayer(next);
            if (nextPlayer == null) {
                // Player offline — drop them, retry on their next login.
                pendingAutoQueue.poll();
                enqueuedOrActive.remove(next);
                pendingRadius.remove(next);
                return;
            }
            // Only start auto-preload if the player has reached ACCEPTED state —
            // teleporting someone stuck in the login or captcha screen would
            // be jarring and pointless (their JM client may not even be ready).
            com.frostlogic.warproject.attachment.PlayerState ps =
                    nextPlayer.getData(WpAttachmentTypes.PLAYER_STATE.get());
            if (ps != com.frostlogic.warproject.attachment.PlayerState.ACCEPTED) {
                return; // try again next tick
            }
            pendingAutoQueue.poll();
            int radius = pendingRadius.getOrDefault(next, 800);
            pendingRadius.remove(next);
            startInternal(nextPlayer, radius, true);
            // fall through and tick the freshly-started session this same call
        }

        if (activeSession == null) return;

        activeSession = activeSession.withTick(activeSession.tickCounter + 1);

        if (activeSession.tickCounter % TICKS_PER_JUMP != 0) return;

        // Find the player
        ServerPlayer player = event.getServer().getPlayerList().getPlayer(activeSession.playerUuid);
        if (player == null) {
            // Player disconnected — cancel, allow retry on next login.
            UUID gone = activeSession.playerUuid;
            enqueuedOrActive.remove(gone);
            activeSession = null;
            return;
        }

        if (activeSession.positions.isEmpty()) {
            // Done!
            player.teleportTo(activeSession.level,
                    activeSession.center.getX() + 0.5,
                    activeSession.center.getY(),
                    activeSession.center.getZ() + 0.5,
                    player.getYRot(), player.getXRot());
            restorePlayer(player);
            if (activeSession.auto) {
                player.setData(WpAttachmentTypes.MAP_PRELOAD_DONE.get(), true);
                player.sendSystemMessage(Component.literal("[WP Map] ✓ Карта прогружена. Приятной игры!"));
            } else {
                player.sendSystemMessage(Component.literal("[WP Map] ✓ Прогрузка карты завершена!"));
            }
            WarProject.LOGGER.info("[WP Map] Preload completed{} for {}",
                    activeSession.auto ? " (auto)" : "",
                    player.getGameProfile().getName());
            enqueuedOrActive.remove(activeSession.playerUuid);
            activeSession = null;
            return;
        }

        // Teleport to next position
        BlockPos next = activeSession.positions.poll();
        // Find surface Y at this position
        int surfaceY = activeSession.level.getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                next.getX(), next.getZ()) + 10;

        player.teleportTo(activeSession.level,
                next.getX() + 0.5, surfaceY, next.getZ() + 0.5,
                player.getYRot(), player.getXRot());

        // Progress update every 50 jumps
        int remaining = activeSession.positions.size();
        if (remaining % 50 == 0 && remaining > 0) {
            player.sendSystemMessage(Component.literal(
                    String.format("[WP Map] Прогрузка... осталось %d точек", remaining)));
        }
    }

    private static void restorePlayer(ServerPlayer player) {
        player.setInvisible(false);
        boolean creative = player.isCreative();
        player.getAbilities().invulnerable = creative;
        player.getAbilities().flying = creative;
        player.getAbilities().mayfly = creative;
        player.onUpdateAbilities();
    }

    /**
     * Generates positions in a spiral pattern from center outward.
     */
    private static Deque<BlockPos> generateSpiralPositions(BlockPos center, int radius) {
        Deque<BlockPos> positions = new ArrayDeque<>();
        int cx = center.getX();
        int cz = center.getZ();
        int cy = center.getY();

        // Spiral outward in chunk-sized steps
        int maxSteps = radius / CHUNK_STEP;
        int x = 0, z = 0;
        int dx = 0, dz = -1;

        int totalPoints = (2 * maxSteps + 1) * (2 * maxSteps + 1);
        for (int i = 0; i < totalPoints; i++) {
            if (-maxSteps <= x && x <= maxSteps && -maxSteps <= z && z <= maxSteps) {
                positions.add(new BlockPos(cx + x * CHUNK_STEP, cy, cz + z * CHUNK_STEP));
            }

            // Spiral logic
            if (x == z || (x < 0 && x == -z) || (x > 0 && x == 1 - z)) {
                int temp = dx;
                dx = -dz;
                dz = temp;
            }
            x += dx;
            z += dz;
        }

        return positions;
    }

    private record PreloadSession(
            UUID playerUuid,
            Deque<BlockPos> positions,
            BlockPos center,
            ServerLevel level,
            int tickCounter,
            boolean auto
    ) {
        PreloadSession withTick(int newTick) {
            return new PreloadSession(playerUuid, positions, center, level, newTick, auto);
        }
    }
}
