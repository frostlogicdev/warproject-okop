package com.frostlogic.warproject.client.jmplugin;

import com.frostlogic.warproject.WarProject;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * Wires {@link JourneymapConfigPatcher} into the client's life-cycle.
 * <p>
 * On the player's first login we attempt to apply our recommended JM defaults.
 * If JM hasn't been launched yet (config dir missing), the patcher is a no-op
 * and we'll retry on subsequent logins until the dir exists and the flag is written.
 * <p>
 * Note: this class never references any {@code journeymap.api.*} class — only
 * file-system patching — so it's safe to load even if JM is absent. A missing
 * JM simply means there's no config dir to patch.
 */
@EventBusSubscriber(modid = WarProject.MOD_ID, value = Dist.CLIENT)
public final class JmClientLifecycle {

    private static boolean patchApplied = false;

    private JmClientLifecycle() {}

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        if (FMLEnvironment.dist != Dist.CLIENT || patchApplied) return;
        // Run on a background thread — the patch is small but file I/O on the
        // network thread is bad form. The class is thread-safe; only one event
        // ever runs per session because of the `patchApplied` guard.
        new Thread(() -> {
            boolean result = JourneymapConfigPatcher.tryApply();
            if (result) {
                patchApplied = true;
            }
            // If not applied (JM config dir missing), we'll retry next login
        }, "WarProject-JMPatcher").start();
    }
}
