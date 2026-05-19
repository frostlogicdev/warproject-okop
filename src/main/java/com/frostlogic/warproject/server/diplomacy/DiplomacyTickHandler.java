package com.frostlogic.warproject.server.diplomacy;

import com.frostlogic.warproject.WarProject;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Server tick handler that drives {@link DiplomacyService#tickTruce(MinecraftServer)}
 * every server tick to check for proposal timeouts and truce expiration.
 * <p>
 * Subscribes to {@link ServerTickEvent.Post} so that all game logic for the tick
 * has already executed before diplomacy timeouts are evaluated.
 * <p>
 * Requirements: 10.1, 10.5, 10.10, 11.7
 * Design: §4 Diplomacy System — tickTruce
 */
@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class DiplomacyTickHandler {

    private static @Nullable DiplomacyService diplomacyService;

    private DiplomacyTickHandler() {
        // static event subscriber — no instantiation
    }

    /**
     * Initializes the handler with the {@link DiplomacyService} instance.
     * Must be called during server startup (from {@code WpCommandRoot.onServerStarted})
     * before any tick events are processed.
     */
    public static void init(DiplomacyService service) {
        diplomacyService = service;
    }

    /**
     * Clears the service reference on server stop to prevent stale references
     * across server restarts (e.g. in single-player or integrated server).
     */
    public static void clear() {
        diplomacyService = null;
    }

    /**
     * Called every server tick (post-phase). Delegates to
     * {@link DiplomacyService#tickTruce(MinecraftServer)} which handles:
     * <ul>
     *   <li>Truce proposal timeout (60s)</li>
     *   <li>Exchange proposal timeout (300s)</li>
     *   <li>Active truce expiration</li>
     * </ul>
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (diplomacyService == null) {
            return;
        }
        diplomacyService.tickTruce(event.getServer());
    }
}
