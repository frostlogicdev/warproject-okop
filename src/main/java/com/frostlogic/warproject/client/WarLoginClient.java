package com.frostlogic.warproject.client;

import com.frostlogic.warproject.client.screen.WarLoginScreen;
import com.frostlogic.warproject.network.LoginResultPayload;
import com.frostlogic.warproject.network.OpenLoginScreenPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-only entry points for the WarProject login protocol.
 * <p>
 * - {@link #handleOpenScreen} swaps the active screen to {@link WarLoginScreen}
 *   in the requested mode (register vs login).
 * <p>
 * - {@link #handleResult} either closes the screen on success or pushes the
 *   error message into the open screen so the user can retry.
 */
public final class WarLoginClient {
    /**
     * Persists across ticks so the login screen survives race conditions with
     * {@link ReceivingLevelScreen} closing (which calls {@code mc.setScreen(null)}).
     * Cleared only on successful authentication.
     */
    private static volatile Boolean pendingRegister = null;

    private WarLoginClient() {
    }

    public static void handleOpenScreen(OpenLoginScreenPayload payload, IPayloadContext context) {
        pendingRegister = payload.register();
        context.enqueueWork(WarLoginClient::tryOpenLoginScreen);
    }

    public static void handleResult(LoginResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (payload.success()) {
                pendingRegister = null;
                if (mc.screen instanceof WarLoginScreen) {
                    mc.setScreen(null);
                }
            } else {
                if (mc.screen instanceof WarLoginScreen loginScreen) {
                    loginScreen.onLoginFailure(payload.message());
                }
            }
        });
    }

    /**
     * Called every client tick from {@link WarClientInputHandler}.
     * If there is a pending login screen that is not yet visible, opens it.
     */
    public static void tickPending() {
        if (pendingRegister != null) {
            tryOpenLoginScreen();
        }
    }

    private static void tryOpenLoginScreen() {
        Boolean mode = pendingRegister;
        if (mode == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Screen current = mc.screen;
        // Already showing the correct screen — nothing to do.
        if (current instanceof WarLoginScreen warLogin && warLogin.isRegister() == mode) {
            return;
        }
        // Don't replace ReceivingLevelScreen — wait for it to finish on its own,
        // then tickPending() will open the login screen on the next tick.
        if (current instanceof ReceivingLevelScreen) {
            return;
        }
        mc.setScreen(new WarLoginScreen(mode));
    }
}
