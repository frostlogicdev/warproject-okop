package com.frostlogic.warproject.server.map;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.WpConfig;
import com.frostlogic.warproject.attachment.PlayerState;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Hooks the server lifecycle so every player goes through the JourneyMap
 * auto-preload spiral exactly once.
 * <p>
 * Strategy:
 * <ol>
 *   <li>On {@link PlayerEvent.PlayerLoggedInEvent} we check the player's
 *       {@code MAP_PRELOAD_DONE} attachment and {@code PlayerState}. If they
 *       are already {@code ACCEPTED} and have never been preloaded, they are
 *       added to the {@link MapPreloader} auto-queue immediately.</li>
 *   <li>For players who join in a pre-ACCEPTED state (NEW / LOGIN_PENDING /
 *       CAPTCHA / RPNAME_REQUIRED / FACTIONLESS / CANDIDATE) we re-check on a
 *       throttled server tick — once they progress through the auth + faction
 *       flow and become {@code ACCEPTED}, the same enqueue path runs.</li>
 * </ol>
 * The {@link MapPreloader} queue ensures only one player at a time is being
 * spiral-flown, so several simultaneous logins serialize cleanly.
 * <p>
 * Disabled entirely via {@code config wp-server.toml :: map.autoPreloadEnabled}.
 */
@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class AutoMapPreloadHandler {

    /** Re-scan all online players every N ticks for late state transitions. */
    private static final int SCAN_INTERVAL_TICKS = 40; // 2 seconds
    private static int tickCounter = 0;

    private AutoMapPreloadHandler() {
        // static event subscriber
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!WpConfig.MAP_AUTO_PRELOAD_ENABLED.get()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        tryEnqueue(player);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!WpConfig.MAP_AUTO_PRELOAD_ENABLED.get()) {
            return;
        }
        tickCounter++;
        if (tickCounter < SCAN_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;

        // Catch late ACCEPTED transitions (e.g. player just finished captcha
        // or got accepted by an OP after the join event already fired).
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            tryEnqueue(player);
        }
    }

    /**
     * Best-effort enqueue: skips if config disabled, already preloaded, or
     * player isn't yet in the ACCEPTED state. The {@link MapPreloader} itself
     * deduplicates queue entries, so this is safe to call repeatedly.
     */
    private static void tryEnqueue(ServerPlayer player) {
        Boolean done = player.getExistingData(WpAttachmentTypes.MAP_PRELOAD_DONE.get()).orElse(false);
        if (Boolean.TRUE.equals(done)) {
            return;
        }
        PlayerState state = player.getData(WpAttachmentTypes.PLAYER_STATE.get());
        if (state != PlayerState.ACCEPTED) {
            return;
        }
        int radius = WpConfig.MAP_AUTO_PRELOAD_RADIUS.get();
        MapPreloader.enqueueAuto(player, radius);
    }
}
