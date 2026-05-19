package com.frostlogic.warproject.client;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.client.screen.WarLevelLoadingScreen;
import com.frostlogic.warproject.client.screen.WarMenuBackground;
import com.frostlogic.warproject.client.screen.WarProjectTitleScreen;
import com.frostlogic.warproject.client.screen.WarReceivingLevelScreen;
import com.frostlogic.warproject.client.widget.WarProgressBar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.lang.reflect.Field;
import java.util.function.BooleanSupplier;

/**
 * Client-only event subscriber for War Project menu UI.
 *
 * <ul>
 *   <li>Replaces vanilla {@link TitleScreen} with {@link WarProjectTitleScreen}.</li>
 *   <li>Replaces vanilla {@link LevelLoadingScreen} (singleplayer chunk-grid) with
 *       {@link WarLevelLoadingScreen}.</li>
 *   <li>Replaces vanilla {@link ReceivingLevelScreen} (post-chunk "Downloading
 *       terrain" wait) with {@link WarReceivingLevelScreen} — only for the OTHER
 *       reason; nether/end portal transitions are left untouched.</li>
 *   <li>Paints the alternative animated background on Play / Server / Settings
 *       and {@link ConnectScreen}.</li>
 *   <li>Adds an indeterminate horizontal progress bar to {@link ConnectScreen}.</li>
 * </ul>
 */
@EventBusSubscriber(modid = WarProject.MOD_ID, value = Dist.CLIENT)
public final class WarProjectClient {
    private static volatile Field cachedProgressListenerField;
    private static volatile Field cachedLevelReceivedField;
    private static volatile Field cachedReasonField;
    private static boolean displayOptionsApplied;

    private WarProjectClient() {}

    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        if (displayOptionsApplied) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null || mc.options == null) {
            return;
        }
        if (!mc.getWindow().isFullscreen()) {
            mc.options.fullscreen().set(true);
        }
        if (mc.options.menuBackgroundBlurriness().get() > 2) {
            mc.options.menuBackgroundBlurriness().set(2);
        }
        mc.options.save();
        displayOptionsApplied = true;
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Screen newScreen = event.getNewScreen();

        if (newScreen instanceof TitleScreen
                && !(newScreen instanceof WarProjectTitleScreen)) {
            event.setNewScreen(new WarProjectTitleScreen());
            return;
        }

        if (newScreen instanceof LevelLoadingScreen vanillaLoading
                && !(vanillaLoading instanceof WarLevelLoadingScreen)) {
            StoringChunkProgressListener listener = extractProgressListener(vanillaLoading);
            if (listener != null) {
                event.setNewScreen(new WarLevelLoadingScreen(listener));
            }
            return;
        }

        if (newScreen instanceof ReceivingLevelScreen vanillaReceiving
                && !(vanillaReceiving instanceof WarReceivingLevelScreen)) {
            ReceivingLevelScreen.Reason reason = extractReason(vanillaReceiving);
            // Only intercept the standard "downloading terrain" path. Portal transitions
            // (NETHER_PORTAL / END_PORTAL) keep their special vanilla effect.
            if (reason == ReceivingLevelScreen.Reason.OTHER) {
                BooleanSupplier supplier = extractLevelReceived(vanillaReceiving);
                if (supplier != null) {
                    event.setNewScreen(new WarReceivingLevelScreen(supplier, reason));
                }
            }
        }
    }

    /** Fires after vanilla paints its dirt-tile menu background — paint our themed
     *  image on top. Note: ReceivingLevelScreen overrides {@code renderBackground}
     *  and never fires this event, that's why it's handled via Opening above. */
    @SuppressWarnings("removal")
    @SubscribeEvent
    public static void onBackgroundRendered(ScreenEvent.BackgroundRendered event) {
        Screen screen = event.getScreen();
        if (shouldThemeBackground(screen)) {
            WarMenuBackground.render(event.getGuiGraphics(),
                    screen.width, screen.height,
                    WarMenuBackground.ALTERNATIVE);
        }
    }

    /** Indeterminate progress bar on top of vanilla render — only for the
     *  not-subclassable ConnectScreen (private constructor on vanilla). */
    @SubscribeEvent
    public static void onRenderPost(ScreenEvent.Render.Post event) {
        if (event.getScreen() instanceof ConnectScreen connect) {
            GuiGraphics g = event.getGuiGraphics();
            int barW = 360;
            int barH = 8;
            int x = (connect.width - barW) / 2;
            int y = connect.height / 2 + 6;
            WarProgressBar.renderIndeterminate(g, x, y, barW, barH);
        }
    }

    private static boolean shouldThemeBackground(Screen screen) {
        return screen instanceof SelectWorldScreen
                || screen instanceof CreateWorldScreen
                || screen instanceof JoinMultiplayerScreen
                || screen instanceof OptionsScreen
                || screen instanceof VideoSettingsScreen
                || screen instanceof SoundOptionsScreen
                || screen instanceof ControlsScreen
                || screen instanceof KeyBindsScreen
                || screen instanceof ConnectScreen;
    }

    // ----- reflection helpers (cached on first use) -------------------------

    private static StoringChunkProgressListener extractProgressListener(LevelLoadingScreen screen) {
        try {
            if (cachedProgressListenerField == null) {
                Field f = LevelLoadingScreen.class.getDeclaredField("progressListener");
                f.setAccessible(true);
                cachedProgressListenerField = f;
            }
            return (StoringChunkProgressListener) cachedProgressListenerField.get(screen);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            WarProject.LOGGER.warn("[WarProject] Couldn't reflect LevelLoadingScreen.progressListener", e);
            return null;
        }
    }

    private static BooleanSupplier extractLevelReceived(ReceivingLevelScreen screen) {
        try {
            if (cachedLevelReceivedField == null) {
                Field f = ReceivingLevelScreen.class.getDeclaredField("levelReceived");
                f.setAccessible(true);
                cachedLevelReceivedField = f;
            }
            return (BooleanSupplier) cachedLevelReceivedField.get(screen);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            WarProject.LOGGER.warn("[WarProject] Couldn't reflect ReceivingLevelScreen.levelReceived", e);
            return null;
        }
    }

    private static ReceivingLevelScreen.Reason extractReason(ReceivingLevelScreen screen) {
        try {
            if (cachedReasonField == null) {
                Field f = ReceivingLevelScreen.class.getDeclaredField("reason");
                f.setAccessible(true);
                cachedReasonField = f;
            }
            return (ReceivingLevelScreen.Reason) cachedReasonField.get(screen);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            WarProject.LOGGER.warn("[WarProject] Couldn't reflect ReceivingLevelScreen.reason", e);
            return null;
        }
    }
}
