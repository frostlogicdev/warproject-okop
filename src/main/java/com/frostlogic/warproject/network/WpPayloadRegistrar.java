package com.frostlogic.warproject.network;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.network.payload.c2s.*;
import com.frostlogic.warproject.network.payload.s2c.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Central payload registration for all WarProject network packets.
 * <p>
 * Handles {@link RegisterPayloadHandlersEvent} on the MOD event bus, registering
 * all C2S and S2C payload types under version "1" with {@code optional()} to allow
 * graceful handling when the client mod is absent (server will disconnect via
 * separate check in ServerEvents).
 * <p>
 * Requirements: 4.1, 4.2
 * Design: §6
 */
@EventBusSubscriber(modid = WarProject.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class WpPayloadRegistrar {

    private WpPayloadRegistrar() {
        // static event subscriber — no instantiation
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar reg = event.registrar("warproject").versioned("1").optional();

        // --- C2S (Client → Server) ---
        reg.playToServer(
                RegisterRequestPayload.TYPE,
                RegisterRequestPayload.STREAM_CODEC,
                ServerPayloadHandler::onRegister
        );
        reg.playToServer(
                LoginRequestPayload.TYPE,
                LoginRequestPayload.STREAM_CODEC,
                ServerPayloadHandler::onLogin
        );
        reg.playToServer(
                RadialOpenRequestPayload.TYPE,
                RadialOpenRequestPayload.STREAM_CODEC,
                ServerPayloadHandler::onRadialOpenRequest
        );
        reg.playToServer(
                RadialActionPayload.TYPE,
                RadialActionPayload.STREAM_CODEC,
                ServerPayloadHandler::onRadialAction
        );
        reg.playToServer(
                PassportOpenPayload.TYPE,
                PassportOpenPayload.STREAM_CODEC,
                ServerPayloadHandler::onPassportOpen
        );
        reg.playToServer(
                RpNameSubmitPayload.TYPE,
                RpNameSubmitPayload.STREAM_CODEC,
                ServerPayloadHandler::onRpNameSubmit
        );
        reg.playToServer(
                CaptchaSubmitPayload.TYPE,
                CaptchaSubmitPayload.STREAM_CODEC,
                ServerPayloadHandler::onCaptchaSubmit
        );
        reg.playToServer(
                FactionChoicePayload.TYPE,
                FactionChoicePayload.STREAM_CODEC,
                ServerPayloadHandler::onFactionChoice
        );
        reg.playToServer(
                SetFactionMarkerPayload.TYPE,
                SetFactionMarkerPayload.STREAM_CODEC,
                ServerPayloadHandler::onSetFactionMarker
        );

        // --- S2C (Server → Client) ---
        // S2C handlers must be wrapped in dist-guarded lambdas to prevent
        // ClientPayloadHandler (which references client-only classes like Screen)
        // from being loaded on dedicated servers. A method reference would force
        // LambdaMetafactory to resolve ClientPayloadHandler at registration time,
        // which trips NeoForge's RuntimeDistCleaner. The lambda body is verified
        // lazily (only when the lambda is actually invoked), so the symbolic
        // reference to ClientPayloadHandler is never resolved on the server side.
        reg.playToClient(
                AuthScreenStatePayload.TYPE,
                AuthScreenStatePayload.STREAM_CODEC,
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        ClientPayloadHandler.onAuthScreenState(payload, context);
                    }
                }
        );
        reg.playToClient(
                CaptchaStatePayload.TYPE,
                CaptchaStatePayload.STREAM_CODEC,
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        ClientPayloadHandler.onCaptchaState(payload, context);
                    }
                }
        );
        reg.playToClient(
                RadialMenuPayload.TYPE,
                RadialMenuPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        ClientPayloadHandler.onRadialMenu(payload, context);
                    }
                }
        );
        reg.playToClient(
                PassportSnapshotPayload.TYPE,
                PassportSnapshotPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        ClientPayloadHandler.onPassportSnapshot(payload, context);
                    }
                }
        );
        reg.playToClient(
                PlayerPublicViewPayload.TYPE,
                PlayerPublicViewPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        ClientPayloadHandler.onPlayerPublicView(payload, context);
                    }
                }
        );
        reg.playToClient(
                AuthErrorPayload.TYPE,
                AuthErrorPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        ClientPayloadHandler.onAuthError(payload, context);
                    }
                }
        );
        reg.playToClient(
                OpenFactionChoicePayload.TYPE,
                OpenFactionChoicePayload.STREAM_CODEC,
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        ClientPayloadHandler.onOpenFactionChoice(payload, context);
                    }
                }
        );
        reg.playToClient(
                OpenPassportPayload.TYPE,
                OpenPassportPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        ClientPayloadHandler.onOpenPassport(payload, context);
                    }
                }
        );
        reg.playToClient(
                MilitaryIdSnapshotPayload.TYPE,
                MilitaryIdSnapshotPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        ClientPayloadHandler.onMilitaryIdSnapshot(payload, context);
                    }
                }
        );
        reg.playToClient(
                AllyPositionsPayload.TYPE,
                AllyPositionsPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        ClientPayloadHandler.onAllyPositions(payload, context);
                    }
                }
        );
        reg.playToClient(
                EnemyVisiblePayload.TYPE,
                EnemyVisiblePayload.STREAM_CODEC,
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        ClientPayloadHandler.onEnemyVisible(payload, context);
                    }
                }
        );
        reg.playToClient(
                FactionBasesPayload.TYPE,
                FactionBasesPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        ClientPayloadHandler.onFactionBases(payload, context);
                    }
                }
        );
        reg.playToClient(
                FactionMarkerActivePayload.TYPE,
                FactionMarkerActivePayload.STREAM_CODEC,
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        ClientPayloadHandler.onFactionMarkerActive(payload, context);
                    }
                }
        );
        reg.playToClient(
                FactionMarkerClearPayload.TYPE,
                FactionMarkerClearPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        ClientPayloadHandler.onFactionMarkerClear(payload, context);
                    }
                }
        );

        reg.playToClient(
                DeathCinematicPayload.TYPE,
                DeathCinematicPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        ClientPayloadHandler.onDeathCinematic(payload, context);
                    }
                }
        );

        // ── Transport NPC (vehicle requisition) ─────────────────────────────
        reg.playToServer(
                com.frostlogic.warproject.network.payload.c2s.TransportChoicePayload.TYPE,
                com.frostlogic.warproject.network.payload.c2s.TransportChoicePayload.STREAM_CODEC,
                com.frostlogic.warproject.server.transport.TransportChoiceHandler::onChoice
        );
        reg.playToClient(
                com.frostlogic.warproject.network.payload.s2c.OpenTransportChoicePayload.TYPE,
                com.frostlogic.warproject.network.payload.s2c.OpenTransportChoicePayload.STREAM_CODEC,
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        ClientPayloadHandler.onOpenTransportChoice(payload, context);
                    }
                }
        );

        WarProject.LOGGER.info("[WarProject] Registered {} C2S and {} S2C payload types (version \"1\", optional)", 10, 15);
    }
}
