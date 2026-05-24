package com.frostlogic.warproject.client;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.client.renderer.FactionNpcRenderer;
import com.frostlogic.warproject.client.renderer.TransportNpcRenderer;
import com.frostlogic.warproject.server.faction.npc.FactionNpcEntityType;
import com.frostlogic.warproject.server.transport.TransportNpcEntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Client-side event subscriber for WarProject (NeoForge game event bus, CLIENT dist only).
 * <p>
 * Handles client payload registration, key mapping registration, and client-specific
 * setup for the new WarProject subsystems (screens, renderers, HUD overlays).
 * <p>
 * This class complements the existing {@link WarProjectClient} which handles
 * menu UI replacements and background theming. As subsystems are migrated
 * to the new architecture, their client handlers will move here.
 * <p>
 * Requirements: 4.1, 19.1
 * Design: §3
 */
@EventBusSubscriber(modid = WarProject.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientEventHandler {

    private ClientEventHandler() {
        // static event subscriber — no instantiation
    }

    /**
     * Placeholder: fires during client mod setup.
     * Future tasks will register client payload handlers, key mappings, etc. here.
     */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        WarProject.LOGGER.debug("[WarProject] ClientEventHandler: client setup complete.");
    }

    /**
     * Registers custom entity renderers on the client.
     */
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(FactionNpcEntityType.FACTION_NPC.get(), FactionNpcRenderer::new);
        event.registerEntityRenderer(TransportNpcEntityType.TRANSPORT_NPC.get(), TransportNpcRenderer::new);
    }
}
