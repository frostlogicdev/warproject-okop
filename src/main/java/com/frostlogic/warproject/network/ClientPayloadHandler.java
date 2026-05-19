package com.frostlogic.warproject.network;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.client.DeathCinematicOverlay;
import com.frostlogic.warproject.client.PlayerPublicViewCache;
import com.frostlogic.warproject.client.RadialMenuScreen;
import com.frostlogic.warproject.client.screen.FactionChoiceScreen;
import com.frostlogic.warproject.client.screen.LoginScreen;
import com.frostlogic.warproject.client.screen.MilitaryIdScreen;
import com.frostlogic.warproject.client.screen.PassportScreen;
import com.frostlogic.warproject.client.screen.RegisterScreen;
import com.frostlogic.warproject.network.payload.AuthMode;
import com.frostlogic.warproject.network.payload.s2c.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Network-level client-side handlers for all S2C (server-to-client) payloads.
 * <p>
 * Each handler receives the payload and the context, enqueues work on the client thread,
 * and delegates to the appropriate client screen/renderer. Placeholder implementations
 * log the receipt and will be connected to real client code in subsequent tasks.
 * <p>
 * Note: This is the network-layer handler in the {@code network} package.
 * The existing {@code client.ClientPayloadHandler} in the client package handles
 * client-specific rendering logic and will be integrated later.
 * <p>
 * Requirements: 4.1, 4.2
 * Design: §6
 */
public final class ClientPayloadHandler {

    private ClientPayloadHandler() {
        // static handlers only
    }

    public static void onAuthScreenState(AuthScreenStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            WarProject.LOGGER.debug("[WP Net] Received AuthScreenStatePayload: mode={}", payload.mode());
            // Open or replace the auth screen based on AuthMode. The error key,
            // if present, is forwarded as a translatable Component so the
            // screen can render a localized error line above the form.
            // Requirements: 2.1, 3.1 — Design: §9.1, §6.
            Minecraft mc = Minecraft.getInstance();
            AuthMode mode = payload.mode();
            String errorKey = payload.errorKey();
            Component errorComponent = errorKey != null ? Component.translatable(errorKey) : null;

            if (mode == AuthMode.REGISTER) {
                Screen current = mc.screen;
                if (current instanceof RegisterScreen existing) {
                    existing.setError(errorComponent);
                } else {
                    mc.setScreen(new RegisterScreen(errorComponent));
                }
            } else if (mode == AuthMode.LOGIN) {
                Screen current = mc.screen;
                if (current instanceof LoginScreen existing) {
                    existing.setError(errorComponent);
                } else {
                    mc.setScreen(new LoginScreen(errorComponent));
                }
            }
        });
    }

    public static void onCaptchaState(CaptchaStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            WarProject.LOGGER.debug("[WP Net] Received CaptchaStatePayload: remaining={}s, attempts={}", payload.remainingSeconds(), payload.attemptsLeft());
            // Display captcha code via actionbar on the client. The code is already
            // shown via title/subtitle by the server, but this payload keeps the
            // client HUD updated with remaining time and attempts.
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("wp.captcha.hud",
                                payload.code(), payload.remainingSeconds(), payload.attemptsLeft()),
                        true // actionbar
                );
            }
        });
    }

    public static void onRadialMenu(RadialMenuPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            WarProject.LOGGER.debug("[WP Net] Received RadialMenuPayload: target={}, items={}", payload.targetUuid(), payload.visibleItems());
            // Per design §11 SP-7, the radial menu is opened ONLY in response to
            // this server-sent payload — never locally on hotkey press. The hotkey
            // sends RadialOpenRequestPayload to the server, which performs the
            // raytrace and authorization, then replies with this payload.
            Minecraft.getInstance().setScreen(
                    new RadialMenuScreen(payload.targetUuid(), payload.visibleItems(), payload.targetView())
            );
        });
    }

    public static void onPassportSnapshot(PassportSnapshotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            WarProject.LOGGER.debug("[WP Net] Received PassportSnapshotPayload: id={}", payload.data().passportId());
            // Open the passport viewer screen with all fields from the snapshot.
            // Requirements: 12.3; Design: §9.3
            Minecraft.getInstance().setScreen(new PassportScreen(payload.data()));
        });
    }

    public static void onPlayerPublicView(PlayerPublicViewPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            WarProject.LOGGER.debug("[WP Net] Received PlayerPublicViewPayload: uuid={}, faction={}, collab={}",
                    payload.uuid(), payload.faction(), payload.collaborator());
            // Cache the public view so client renderers (name tag, TAB) can read
            // per-player state without storing it on the entity itself.
            // Requirements: 11.2; Design: §9.4
            PlayerPublicViewCache.put(payload);
        });
    }

    public static void onAuthError(AuthErrorPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            WarProject.LOGGER.debug("[WP Net] Received AuthErrorPayload: key={}", payload.i18nKey());
            // Display the localized error on the current auth screen (if open)
            Minecraft mc = Minecraft.getInstance();
            Screen current = mc.screen;
            Component errorComponent = Component.translatable(payload.i18nKey());
            if (current instanceof RegisterScreen registerScreen) {
                registerScreen.setError(errorComponent);
            } else if (current instanceof LoginScreen loginScreen) {
                loginScreen.setError(errorComponent);
            } else if (mc.player != null) {
                // Fallback: show as system message if no auth screen is open
                mc.player.displayClientMessage(errorComponent, false);
            }
        });
    }

    public static void onOpenFactionChoice(OpenFactionChoicePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            WarProject.LOGGER.debug("[WP Net] Received OpenFactionChoicePayload: faction={}", payload.factionId());
            Minecraft.getInstance().setScreen(new FactionChoiceScreen(payload.factionId()));
        });
    }

    public static void onOpenPassport(OpenPassportPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            WarProject.LOGGER.debug("[WP Net] Received OpenPassportPayload: success={}", payload.success());
            // The passport viewer is opened via PassportSnapshotPayload (which carries
            // the full data). OpenPassportPayload is a lightweight ack/nack — if success
            // is false, show an error message to the player.
            if (!payload.success()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.displayClientMessage(
                            Component.translatable("wp.passport.error.cannot_open"), false);
                }
            }
        });
    }

    public static void onMilitaryIdSnapshot(MilitaryIdSnapshotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            WarProject.LOGGER.debug("[WP Net] Received MilitaryIdSnapshotPayload");
            // Open MilitaryIdScreen with the received data.
            // Requirements: 6.1; Design: §2 Military ID System — MilitaryIdScreen
            Minecraft.getInstance().setScreen(new MilitaryIdScreen(payload.data()));
        });
    }

    public static void onAllyPositions(com.frostlogic.warproject.network.payload.s2c.AllyPositionsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Forward through the static bridge so this handler stays free of any
            // direct JourneyMap API reference. If JM isn't installed, the bridge
            // is a no-op and nothing here loads JM classes.
            com.frostlogic.warproject.client.jmplugin.JmDataBridge.publishAllies(payload);
        });
    }

    public static void onEnemyVisible(com.frostlogic.warproject.network.payload.s2c.EnemyVisiblePayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                com.frostlogic.warproject.client.jmplugin.JmDataBridge.publishEnemies(payload)
        );
    }

    public static void onFactionBases(com.frostlogic.warproject.network.payload.s2c.FactionBasesPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                com.frostlogic.warproject.client.jmplugin.JmDataBridge.publishBases(payload)
        );
    }

    public static void onFactionMarkerActive(com.frostlogic.warproject.network.payload.s2c.FactionMarkerActivePayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                com.frostlogic.warproject.client.jmplugin.JmDataBridge.publishMarkerActive(payload)
        );
    }

    public static void onFactionMarkerClear(com.frostlogic.warproject.network.payload.s2c.FactionMarkerClearPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                com.frostlogic.warproject.client.jmplugin.JmDataBridge.publishMarkerClear(payload)
        );
    }

    public static void onDeathCinematic(com.frostlogic.warproject.network.payload.s2c.DeathCinematicPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            WarProject.LOGGER.debug("[WP Net] Received DeathCinematicPayload: headshot={}, delay={}t",
                    payload.headshot(), payload.respawnDelayTicks());
            DeathCinematicOverlay.start(payload.headshot(), payload.respawnDelayTicks());
        });
    }
}
