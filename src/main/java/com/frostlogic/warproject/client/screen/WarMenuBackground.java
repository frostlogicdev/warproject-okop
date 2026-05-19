package com.frostlogic.warproject.client.screen;

import com.frostlogic.warproject.WarProject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Shared "Ken Burns" zoom-in/out animated background, used by every War Project menu
 * (the custom title plus vanilla Play/Server/Settings via a NeoForge event hook).
 * <p>
 * The zoom is applied through {@code GuiGraphics.pose()} matrix scaling instead of
 * recomputing integer width/height each frame — that way the image scales with
 * sub-pixel precision and stays smooth on any FPS / monitor refresh.
 */
public final class WarMenuBackground {
    public static final ResourceLocation MAIN = ResourceLocation.fromNamespaceAndPath(
            WarProject.MOD_ID, "textures/gui/title/main.png");
    public static final ResourceLocation ALTERNATIVE = ResourceLocation.fromNamespaceAndPath(
            WarProject.MOD_ID, "textures/gui/title/alternative.png");

    public static final int IMG_W = 1376;
    public static final int IMG_H = 768;

    private static final long ANIM_START_NANOS = System.nanoTime();
    private static final double PERIOD_SEC = 30.0;
    private static final float ZOOM_MIN = 1.00f;
    private static final float ZOOM_MAX = 1.035f;
    private static final float PARALLAX_PIXELS = 8.0f;
    private static final float PARALLAX_SMOOTHING = 0.12f;

    private static float smoothOffsetX;
    private static float smoothOffsetY;

    private WarMenuBackground() {}

    /** Smooth 0→1→0 cosine easing, sampled with nanosecond precision. */
    private static float currentZoom() {
        double elapsedSec = (System.nanoTime() - ANIM_START_NANOS) / 1_000_000_000.0;
        double t = (elapsedSec % PERIOD_SEC) / PERIOD_SEC;
        double ease = (1.0 - Math.cos(t * 2.0 * Math.PI)) * 0.5;
        return (float) (ZOOM_MIN + (ZOOM_MAX - ZOOM_MIN) * ease);
    }

    /** Draws {@link #MAIN} as the menu background. */
    public static void render(GuiGraphics g, int screenW, int screenH) {
        render(g, screenW, screenH, MAIN);
    }

    public static void render(GuiGraphics g, int screenW, int screenH, int mouseX, int mouseY) {
        render(g, screenW, screenH, MAIN, mouseX, mouseY);
    }

    /**
     * Draws {@code background} cover-fitted onto the screen with the slow Ken-Burns
     * zoom animation applied around the centre. Uses matrix scaling for sub-pixel
     * smoothness.
     */
    public static void render(GuiGraphics g, int screenW, int screenH, ResourceLocation background) {
        Minecraft mc = Minecraft.getInstance();
        int mouseX = screenW / 2;
        int mouseY = screenH / 2;
        if (mc != null && mc.getWindow() != null) {
            int windowW = mc.getWindow().getScreenWidth();
            int windowH = mc.getWindow().getScreenHeight();
            if (windowW > 0 && windowH > 0) {
                mouseX = (int) Math.round(mc.mouseHandler.xpos() * screenW / (double) windowW);
                mouseY = (int) Math.round(mc.mouseHandler.ypos() * screenH / (double) windowH);
            }
        }
        render(g, screenW, screenH, background, mouseX, mouseY);
    }

    public static void render(GuiGraphics g, int screenW, int screenH, ResourceLocation background, int mouseX, int mouseY) {
        float imgAspect = (float) IMG_W / IMG_H;
        float screenAspect = (float) screenW / screenH;

        int baseW;
        int baseH;
        if (screenAspect > imgAspect) {
            baseW = screenW;
            baseH = Math.round(screenW / imgAspect);
        } else {
            baseH = screenH;
            baseW = Math.round(screenH * imgAspect);
        }
        int overscan = Math.round(PARALLAX_PIXELS * 2.0f);
        baseW += overscan * 2;
        baseH += overscan * 2;
        int baseX = (screenW - baseW) / 2;
        int baseY = (screenH - baseH) / 2;

        float zoom = currentZoom();
        float cx = screenW / 2.0f;
        float cy = screenH / 2.0f;
        float targetOffsetX = screenW <= 0 ? 0f : -(((float) mouseX / screenW) * 2.0f - 1.0f) * PARALLAX_PIXELS;
        float targetOffsetY = screenH <= 0 ? 0f : -(((float) mouseY / screenH) * 2.0f - 1.0f) * PARALLAX_PIXELS;
        smoothOffsetX += (targetOffsetX - smoothOffsetX) * PARALLAX_SMOOTHING;
        smoothOffsetY += (targetOffsetY - smoothOffsetY) * PARALLAX_SMOOTHING;

        g.pose().pushPose();
        g.pose().translate(cx + smoothOffsetX, cy + smoothOffsetY, 0f);
        g.pose().scale(zoom, zoom, 1.0f);
        g.pose().translate(-cx, -cy, 0f);

        g.blit(background, baseX, baseY, baseW, baseH, 0f, 0f, IMG_W, IMG_H, IMG_W, IMG_H);

        g.pose().popPose();
    }
}
