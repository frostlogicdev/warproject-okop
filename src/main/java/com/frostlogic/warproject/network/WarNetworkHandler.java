package com.frostlogic.warproject.network;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.server.WarLoginHandler;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.UUID;

@EventBusSubscriber(modid = WarProject.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class WarNetworkHandler {
    private WarNetworkHandler() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(InteractPayload.TYPE, InteractPayload.STREAM_CODEC, WarNetworkHandler::handleInteract);
        registrar.playToServer(SubmitPasswordPayload.TYPE, SubmitPasswordPayload.STREAM_CODEC, WarNetworkHandler::handleSubmitPassword);
        // S2C handlers must only resolve on the physical client side; we still
        // register the payload itself on both ends so codec is symmetric.
        registrar.playToClient(OpenLoginScreenPayload.TYPE, OpenLoginScreenPayload.STREAM_CODEC, WarNetworkHandler::handleOpenLoginScreen);
        registrar.playToClient(LoginResultPayload.TYPE, LoginResultPayload.STREAM_CODEC, WarNetworkHandler::handleLoginResult);
    }

    private static void handleInteract(InteractPayload payload, IPayloadContext context) {
        // Legacy radial-menu interaction path. Authoritative server-side handling has been
        // moved to com.frostlogic.warproject.server.radial.RadialMenuDispatcher (task 13.2).
        // The new flow is driven by RadialOpenRequestPayload / RadialActionPayload. Tasks
        // 13.3 and 13.4 will retire this packet entirely; until then we accept it but
        // perform no action so older clients do not crash the server.
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                WarProject.LOGGER.debug(
                        "[WP] Legacy InteractPayload received from {} (action={}, target={}); ignored — use RadialActionPayload instead.",
                        player.getGameProfile().getName(), payload.action(), payload.target());
            }
        });
    }

    private static void handleSubmitPassword(SubmitPasswordPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                WarLoginHandler.handleSubmit(player, payload);
            }
        });
    }

    private static void handleOpenLoginScreen(OpenLoginScreenPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            invokeClient("handleOpenScreen", OpenLoginScreenPayload.class, payload, context);
        }
    }

    private static void handleLoginResult(LoginResultPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            invokeClient("handleResult", LoginResultPayload.class, payload, context);
        }
    }

    private static void invokeClient(String methodName, Class<?> payloadType, Object payload, IPayloadContext context) {
        try {
            Class<?> client = Class.forName("com.frostlogic.warproject.client.WarLoginClient");
            client.getMethod(methodName, payloadType, IPayloadContext.class).invoke(null, payload, context);
        } catch (ReflectiveOperationException ex) {
            WarProject.LOGGER.error("Failed to handle WarProject client payload {}", methodName, ex);
        }
    }

    public static void sendInteractToServer(UUID target, String action) {
        PacketDistributor.sendToServer(new InteractPayload(target, action));
    }
}
