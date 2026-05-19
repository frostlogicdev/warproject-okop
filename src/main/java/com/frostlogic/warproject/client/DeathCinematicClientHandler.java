package com.frostlogic.warproject.client;

import com.frostlogic.warproject.WarProject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Client-side event handler for the realistic death cinematic.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Renders the {@link DeathCinematicOverlay} on top of the HUD every frame</li>
 *   <li>Suppresses the vanilla {@link DeathScreen} while the cinematic is active</li>
 *   <li>Stops the cinematic when the player respawns</li>
 * </ul>
 */
@EventBusSubscriber(modid = WarProject.MOD_ID, value = Dist.CLIENT)
public final class DeathCinematicClientHandler {

    private DeathCinematicClientHandler() {
    }

    /**
     * Renders the death cinematic overlay on top of all GUI layers.
     * Uses {@link RenderGuiLayerEvent.Post} to draw after all vanilla HUD elements.
     */
    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiLayerEvent.Post event) {
        if (!DeathCinematicOverlay.isActive()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        DeathCinematicOverlay.render(event.getGuiGraphics(), width, height);
    }

    /**
     * Suppresses the vanilla death screen while the cinematic is playing.
     * The player cannot click "Respawn" until the timer expires — the server
     * handles auto-respawn via {@code RealisticDeathHandler}.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!DeathCinematicOverlay.isActive()) {
            return;
        }

        // Block the vanilla DeathScreen from opening while cinematic plays
        if (event.getNewScreen() instanceof DeathScreen) {
            event.setCanceled(true);
        }
    }

    /**
     * Detects when the player is no longer dead (respawned) and stops the cinematic.
     */
    @SubscribeEvent
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        if (!DeathCinematicOverlay.isActive()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        // Player respawned or disconnected — stop cinematic
        if (mc.player != null && !mc.player.isDeadOrDying()) {
            DeathCinematicOverlay.stop();
        }
        // Disconnected from server
        if (mc.level == null) {
            DeathCinematicOverlay.stop();
        }
    }
}
