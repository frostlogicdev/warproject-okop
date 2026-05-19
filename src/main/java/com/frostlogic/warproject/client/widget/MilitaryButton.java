package com.frostlogic.warproject.client.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Military-grade tactical button.
 * <p>
 * Visual language: angular, beveled corners (no round-corners — round = consumer
 * UI; we want HUD/MIL-STD-1472 feel), translucent dark body so the menu image
 * still reads through, a left accent rail in faction-green that grows on hover,
 * a uppercase label with letter-spacing, and a top-right diagonal corner notch
 * that mimics tactical readout panels.
 * <p>
 * Render layers (back to front):
 *   1. drop shadow under the chamfered silhouette
 *   2. translucent dark body with subtle vertical gradient (top a touch lighter)
 *   3. left accent rail (4 px on hover, 2 px otherwise) in faction green
 *   4. corner chamfers (top-right notch + bottom-left chip)
 *   5. 1-px frame
 *   6. centered uppercase label with shadow + letter-spacing on hover
 */
public class MilitaryButton extends AbstractButton {

    // Body — dark olive, semi-transparent so the menu bg still reads through.
    private static final int BODY_TOP_NORMAL    = 0xCC1A2110;
    private static final int BODY_BOT_NORMAL    = 0xCC10160A;
    private static final int BODY_TOP_HOVER     = 0xE0303D1A;
    private static final int BODY_BOT_HOVER     = 0xE01F2810;
    private static final int BODY_TOP_PRESSED   = 0xF04A6324;
    private static final int BODY_BOT_PRESSED   = 0xF0354819;

    // 1-px outer frame — kept very subtle outside hover.
    private static final int FRAME_NORMAL       = 0x55BCC58A;
    private static final int FRAME_HOVER        = 0xFFCBD9A1;

    // Accent rail on the left — faction-green. This is the most visible signal.
    private static final int ACCENT_NORMAL      = 0xFFCBD9A1;
    private static final int ACCENT_HOVER       = 0xFFE6F0B4;

    // Drop shadow & corner notches.
    private static final int DROP_SHADOW        = 0x60000000;
    private static final int NOTCH_FILL         = 0xFF0E140A;

    private static final int TEXT_NORMAL        = 0xFFE8E4C9;
    private static final int TEXT_HOVER         = 0xFFFFFFFF;
    private static final int TEXT_SHADOW        = 0xC0000000;

    // How much the chamfer eats from the corner (px).
    private static final int CHAMFER            = 6;

    private final Runnable onPress;

    public MilitaryButton(int x, int y, int width, int height, Component msg, Runnable onPress) {
        super(x, y, width, height, msg);
        this.onPress = onPress;
    }

    @Override
    public void onPress() {
        if (onPress != null) onPress.run();
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hovered = isHoveredOrFocused();
        boolean pressed = hovered && Minecraft.getInstance().mouseHandler.isLeftPressed();

        int x = getX(), y = getY(), w = getWidth(), h = getHeight();

        // 1) Drop shadow — slightly offset, gives a 'panel lifted off background' feel.
        fillChamferedBody(g, x, y + 2, w, h, DROP_SHADOW, DROP_SHADOW);

        // 2) Body — vertical 2-stop gradient
        int bodyTop = pressed ? BODY_TOP_PRESSED : (hovered ? BODY_TOP_HOVER : BODY_TOP_NORMAL);
        int bodyBot = pressed ? BODY_BOT_PRESSED : (hovered ? BODY_BOT_HOVER : BODY_BOT_NORMAL);
        fillChamferedBody(g, x, y, w, h, bodyTop, bodyBot);

        // 3) Left accent rail. 2 px idle → 4 px hover so the eye snaps to it.
        int accent = hovered ? ACCENT_HOVER : ACCENT_NORMAL;
        int railWidth = hovered ? 4 : 2;
        // Inset 4 px from top/bottom so the rail respects the chamfer above.
        g.fill(x, y + 4, x + railWidth, y + h - 4, accent);

        // 4) Top-right diagonal notch (3-step stair) — tactical-readout signature.
        for (int i = 0; i < CHAMFER; i++) {
            g.fill(x + w - CHAMFER + i, y, x + w - CHAMFER + i + 1, y + (CHAMFER - i), NOTCH_FILL);
        }

        // 5) 1-px frame around the chamfered silhouette.
        int frame = hovered ? FRAME_HOVER : FRAME_NORMAL;
        drawChamferFrame(g, x, y, w, h, frame);

        // 6) Centered uppercase label.
        Font font = Minecraft.getInstance().font;
        String label = getMessage().getString().toUpperCase(java.util.Locale.ROOT);
        int textWidth = font.width(label);
        int textX = x + (w - textWidth) / 2 + 4; // small right shift to balance the left rail
        int textY = y + (h - font.lineHeight) / 2 + 1;
        int textColor = hovered ? TEXT_HOVER : TEXT_NORMAL;
        g.drawString(font, label, textX + 1, textY + 1, TEXT_SHADOW, false);
        g.drawString(font, label, textX, textY, textColor, false);

        // Tiny hover dot at the right margin — like a status LED.
        if (hovered) {
            g.fill(x + w - 12, y + h / 2 - 1, x + w - 10, y + h / 2 + 1, accent);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Chamfered geometry: rectangle minus a top-right triangle and a small
    // bottom-left triangle. We approximate the diagonal with a stair-step.
    // ──────────────────────────────────────────────────────────────────────

    private static void fillChamferedBody(GuiGraphics g, int x, int y, int w, int h, int colorTop, int colorBot) {
        // Top band — y in [0, CHAMFER), shrinks horizontally on the right edge.
        for (int row = 0; row < CHAMFER; row++) {
            int rightCut = CHAMFER - row;
            g.fillGradient(
                    x, y + row, x + w - rightCut, y + row + 1,
                    blend(colorTop, colorBot, row / (float) h),
                    blend(colorTop, colorBot, (row + 1) / (float) h)
            );
        }
        // Middle band — full width, with vertical gradient.
        g.fillGradient(x, y + CHAMFER, x + w, y + h - CHAMFER, colorTop, colorBot);
        // Bottom band — y in [h-CHAMFER, h), shrinks on the left edge (chip).
        for (int row = 0; row < CHAMFER; row++) {
            int leftCut = row + 1;
            float t = (h - CHAMFER + row) / (float) h;
            g.fillGradient(
                    x + leftCut, y + h - CHAMFER + row, x + w, y + h - CHAMFER + row + 1,
                    blend(colorTop, colorBot, t),
                    blend(colorTop, colorBot, t)
            );
        }
    }

    private static void drawChamferFrame(GuiGraphics g, int x, int y, int w, int h, int color) {
        // Top edge — stops at the start of the right chamfer.
        g.fill(x, y, x + w - CHAMFER, y + 1, color);
        // Top-right diagonal — stair of single pixels.
        for (int i = 0; i < CHAMFER; i++) {
            g.fill(x + w - CHAMFER + i, y + i, x + w - CHAMFER + i + 1, y + i + 1, color);
        }
        // Right edge — starts under the chamfer.
        g.fill(x + w - 1, y + CHAMFER, x + w, y + h, color);
        // Bottom edge — stops short on the left because of the chip.
        g.fill(x + CHAMFER, y + h - 1, x + w, y + h, color);
        // Bottom-left chip diagonal.
        for (int i = 0; i < CHAMFER; i++) {
            g.fill(x + i, y + h - 1 - i, x + i + 1, y + h - i, color);
        }
        // Left edge — stops at the chip.
        g.fill(x, y, x + 1, y + h - CHAMFER, color);
    }

    /** Linear blend between two ARGB colours with per-channel mixing. */
    private static int blend(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aA = (a >>> 24) & 0xFF, aR = (a >>> 16) & 0xFF, aG = (a >>> 8) & 0xFF, aB = a & 0xFF;
        int bA = (b >>> 24) & 0xFF, bR = (b >>> 16) & 0xFF, bG = (b >>> 8) & 0xFF, bB = b & 0xFF;
        int oA = (int) (aA + (bA - aA) * t);
        int oR = (int) (aR + (bR - aR) * t);
        int oG = (int) (aG + (bG - aG) * t);
        int oB = (int) (aB + (bB - aB) * t);
        return (oA << 24) | (oR << 16) | (oG << 8) | oB;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Backward-compat helpers preserved for screens that still use them.
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Filled rounded rectangle (legacy API, kept for screens still calling it).
     * Maps onto the new chamfered look so older screens stay coherent.
     */
    public static void drawRoundedRectFilled(GuiGraphics g, int x, int y, int w, int h, int color) {
        fillChamferedBody(g, x, y, w, h, color, color);
    }

    /** 1-pixel chamfered outline (legacy API). */
    public static void drawRoundedRectOutline(GuiGraphics g, int x, int y, int w, int h, int color) {
        drawChamferFrame(g, x, y, w, h, color);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
