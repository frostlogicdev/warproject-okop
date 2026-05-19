package com.frostlogic.warproject.client.widget;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Horizontal capsule-shaped progress bar in the same olive/cream palette as
 * {@link MilitaryButton}. Two render modes: determinate (0..1 fill) and
 * indeterminate (sliding pulse).
 */
public final class WarProgressBar {
    private static final int TRACK_BG     = 0xB0000000;     // dark translucent rail
    private static final int TRACK_BORDER = 0xFF6F8244;     // olive border
    private static final int FILL_COLOR   = 0xFFCBD9A1;     // cream-olive fill
    private static final int FILL_GLOW    = 0x66FFFFFF;     // top highlight on the fill

    private static final int CORNER_R = 4;

    private WarProgressBar() {}

    /**
     * Draws a determinate (0..1) progress fill.
     */
    public static void renderDeterminate(GuiGraphics g, int x, int y, int w, int h, float progress) {
        progress = Math.max(0f, Math.min(1f, progress));
        drawTrack(g, x, y, w, h);

        int innerX = x + 2;
        int innerY = y + 2;
        int innerW = w - 4;
        int innerH = h - 4;
        int fillW = Math.round(innerW * progress);
        if (fillW > 0) {
            drawCapsuleFill(g, innerX, innerY, fillW, innerH, FILL_COLOR);
            // top 1px highlight
            if (fillW > 4) {
                g.fill(innerX + 2, innerY, innerX + fillW - 2, innerY + 1, FILL_GLOW);
            }
        }
    }

    /**
     * Draws an indeterminate "pulse" that slides back and forth, used when no
     * actual progress percentage is available (e.g. server connect).
     */
    public static void renderIndeterminate(GuiGraphics g, int x, int y, int w, int h) {
        drawTrack(g, x, y, w, h);

        int innerX = x + 2;
        int innerY = y + 2;
        int innerW = w - 4;
        int innerH = h - 4;
        int segW = Math.max(20, innerW / 3);

        long now = System.nanoTime() / 1_000_000L;          // ms with ~ns resolution
        double period = 1800.0;
        double t = (now % (long) period) / period;          // 0..1
        // Smooth sine for ease-in-out motion
        double pos = (Math.sin(t * 2 * Math.PI) * 0.5 + 0.5);
        int segX = innerX + (int) Math.round((innerW - segW) * pos);

        drawCapsuleFill(g, segX, innerY, segW, innerH, FILL_COLOR);
        if (segW > 4) {
            g.fill(segX + 2, innerY, segX + segW - 2, innerY + 1, FILL_GLOW);
        }
    }

    /** Outer rounded track + border. */
    private static void drawTrack(GuiGraphics g, int x, int y, int w, int h) {
        drawCapsuleFill(g, x, y, w, h, TRACK_BG);
        drawCapsuleOutline(g, x, y, w, h, TRACK_BORDER);
    }

    /**
     * Capsule-shaped fill. Corner radius adapts to bar height — for thin bars
     * (h≤8) it becomes a real pill; for fatter bars it stays at {@link #CORNER_R}.
     */
    private static void drawCapsuleFill(GuiGraphics g, int x, int y, int w, int h, int color) {
        int r = Math.min(CORNER_R, h / 2);
        // Side caps
        for (int i = 0; i < r; i++) {
            int inset = capsuleInset(i, r);
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
        // Body
        if (h - 2 * r > 0) {
            g.fill(x, y + r, x + w, y + h - r, color);
        }
    }

    private static void drawCapsuleOutline(GuiGraphics g, int x, int y, int w, int h, int color) {
        int r = Math.min(CORNER_R, h / 2);
        // top + bottom edges
        g.fill(x + r, y,         x + w - r, y + 1, color);
        g.fill(x + r, y + h - 1, x + w - r, y + h, color);
        // side caps for top (1px stripes outlining the corner cuts)
        for (int i = 1; i < r; i++) {
            int inset = capsuleInset(i, r);
            int prevInset = capsuleInset(i - 1, r);
            g.fill(x + inset,             y + i, x + prevInset,         y + i + 1, color);
            g.fill(x + w - prevInset,     y + i, x + w - inset,         y + i + 1, color);
            g.fill(x + inset,             y + h - 1 - i, x + prevInset, y + h - i, color);
            g.fill(x + w - prevInset,     y + h - 1 - i, x + w - inset, y + h - i, color);
        }
        // vertical sides
        if (h - 2 * r > 0) {
            g.fill(x,         y + r, x + 1,     y + h - r, color);
            g.fill(x + w - 1, y + r, x + w,     y + h - r, color);
        }
    }

    /**
     * Approximates a circular corner — at row {@code i} (0 = topmost),
     * how many pixels to inset from the side.
     */
    private static int capsuleInset(int i, int r) {
        // inset = r - sqrt(r² - (r - i)²) — but rounded to int with a small bias
        double k = r - i - 0.5;
        double inside = (double) r * r - k * k;
        if (inside < 0) inside = 0;
        return (int) Math.round(r - Math.sqrt(inside));
    }
}
