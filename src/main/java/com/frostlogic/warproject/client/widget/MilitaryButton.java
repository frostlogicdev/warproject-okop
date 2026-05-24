package com.frostlogic.warproject.client.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * "Tactical panel" main-menu button.
 * <p>
 * Design language (rev 2 — polished):
 * <ul>
 *   <li><b>Material</b>: dark olive translucent panel with a three-stop
 *       vertical gradient. The menu image still reads through, but a
 *       darker mid-band gives a sense of depth so the label is always
 *       legible regardless of the background tone.</li>
 *   <li><b>Edges</b>: a single 4-px micro-chamfer in the top-right corner
 *       (the only asymmetry — a designer hallmark, mil-spec readout look).
 *       Everywhere else the corners are crisp 90°.</li>
 *   <li><b>Stratified frame</b>: a 1-px highlight along the very top (cool
 *       olive), a 1-px shadow along the very bottom (deep ink), and a
 *       1-px outer frame in faction-green. Three discrete pixel lines
 *       give the panel a "rolled steel plate" volume without any
 *       expensive shading tricks.</li>
 *   <li><b>Left side</b>: 3-px accent rail (5-px on hover), followed by
 *       a slot index ("01", "02", …) drawn in olive monospace; a 1-px
 *       hairline separator divides the index from the label.</li>
 *   <li><b>Label</b>: uppercase, slight per-character letter-spacing on
 *       hover (manual draw to add 1-px gaps) — gives a "letter-pressed"
 *       feel without needing a custom font.</li>
 *   <li><b>Right side</b>: idle = small dim olive dot. Hover = a chevron
 *       "›" that nudges 1-2 px on a sine cycle, drawing the eye.</li>
 *   <li><b>Shadow</b>: two-pass soft drop shadow under the panel for a
 *       subtle "lifted off the bg image" feel.</li>
 *   <li><b>Press feedback</b>: the panel pushes down 1-px and brightens
 *       (warm olive) while the shadow collapses — physical click feel.</li>
 * </ul>
 * <p>
 * The widget exposes a {@link #setIndex(int)} helper so screens that lay
 * out multiple buttons can hand each one its slot number. Buttons without
 * an index simply skip the slot column and centre the label more aggressively.
 */
public class MilitaryButton extends AbstractButton {

    // ── Body (translucent so the menu image bleeds through) ───────────────
    private static final int BODY_TOP_NORMAL    = 0xCC1F2812;
    private static final int BODY_MID_NORMAL    = 0xCC151C0C;
    private static final int BODY_BOT_NORMAL    = 0xCC0D1207;

    private static final int BODY_TOP_HOVER     = 0xE03A4A1F;
    private static final int BODY_MID_HOVER     = 0xE0283617;
    private static final int BODY_BOT_HOVER     = 0xE01C2710;

    private static final int BODY_TOP_PRESSED   = 0xF05A7A2C;
    private static final int BODY_MID_PRESSED   = 0xF0405822;
    private static final int BODY_BOT_PRESSED   = 0xF02C3E18;

    // ── Stratified frame ──────────────────────────────────────────────────
    private static final int EDGE_HIGHLIGHT     = 0x40FFFFFF; // top hairline
    private static final int EDGE_SHADOW        = 0x60000000; // bottom hairline
    private static final int FRAME_NORMAL       = 0x55BCC58A;
    private static final int FRAME_HOVER        = 0xFFCBD9A1;

    // ── Left accent rail ──────────────────────────────────────────────────
    private static final int ACCENT_NORMAL      = 0xFFCBD9A1;
    private static final int ACCENT_HOVER       = 0xFFE6F0B4;
    private static final int ACCENT_GLOW        = 0x40CBD9A1; // wide soft halo
    private static final int RAIL_W_NORMAL      = 3;
    private static final int RAIL_W_HOVER       = 5;

    // ── Drop shadow (two-pass) ────────────────────────────────────────────
    private static final int SHADOW_NEAR        = 0x55000000;
    private static final int SHADOW_FAR         = 0x28000000;

    // ── Text ──────────────────────────────────────────────────────────────
    private static final int TEXT_NORMAL        = 0xFFE8E4C9;
    private static final int TEXT_HOVER         = 0xFFFFFDE8;
    private static final int TEXT_SHADOW        = 0xC0000000;
    private static final int INDEX_NORMAL       = 0x99B6BF7E;
    private static final int INDEX_HOVER        = 0xFFE6F0B4;

    // ── Misc geometry ─────────────────────────────────────────────────────
    private static final int CHAMFER            = 4; // single top-right cut
    private static final int LEFT_PAD           = 14; // rail + breathing room
    private static final int INDEX_GUTTER       = 30; // width reserved for "01 │"
    private static final int RIGHT_PAD          = 14;

    private final Runnable onPress;
    /** Optional slot index, 0 = "01". Negative = no index shown. */
    private int slotIndex = -1;

    public MilitaryButton(int x, int y, int width, int height, Component msg, Runnable onPress) {
        super(x, y, width, height, msg);
        this.onPress = onPress;
    }

    /** Sets the displayed slot number (0 → "01", 1 → "02", …). Negative hides it. */
    public MilitaryButton setIndex(int index) {
        this.slotIndex = index;
        return this;
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

        // While pressed the panel sinks by 1 px (and its shadow collapses).
        int sink = pressed ? 1 : 0;

        // ── 1. Drop shadow (skip while pressed) ──────────────────────────
        if (!pressed) {
            fillChamferedRect(g, x + 1, y + 2, w, h, SHADOW_NEAR);
            fillChamferedRect(g, x + 2, y + 4, w, h, SHADOW_FAR);
        }

        // ── 2. Body — three-stop vertical gradient ───────────────────────
        int bodyTop, bodyMid, bodyBot;
        if (pressed) {
            bodyTop = BODY_TOP_PRESSED; bodyMid = BODY_MID_PRESSED; bodyBot = BODY_BOT_PRESSED;
        } else if (hovered) {
            bodyTop = BODY_TOP_HOVER;   bodyMid = BODY_MID_HOVER;   bodyBot = BODY_BOT_HOVER;
        } else {
            bodyTop = BODY_TOP_NORMAL;  bodyMid = BODY_MID_NORMAL;  bodyBot = BODY_BOT_NORMAL;
        }
        fillBodyThreeStop(g, x, y + sink, w, h, bodyTop, bodyMid, bodyBot);

        // ── 3. Stratified frame (highlight + shadow + outer frame) ───────
        int frame = hovered ? FRAME_HOVER : FRAME_NORMAL;
        // Top inner highlight (1 px just below the top edge), stopping before chamfer.
        g.fill(x + 1, y + sink + 1, x + w - CHAMFER, y + sink + 2, EDGE_HIGHLIGHT);
        // Bottom inner shadow (1 px just above the bottom edge).
        g.fill(x + 1, y + sink + h - 2, x + w - 1, y + sink + h - 1, EDGE_SHADOW);
        // Outer chamfer-aware frame.
        drawChamferFrame(g, x, y + sink, w, h, frame);

        // ── 4. Left accent rail + soft halo ──────────────────────────────
        int accent = hovered ? ACCENT_HOVER : ACCENT_NORMAL;
        int railW = hovered ? RAIL_W_HOVER : RAIL_W_NORMAL;
        // Halo behind the rail — a faint glow on hover, invisible idle.
        if (hovered) {
            g.fill(x, y + sink + 2, x + railW + 4, y + sink + h - 2, ACCENT_GLOW);
        }
        g.fill(x, y + sink + 2, x + railW, y + sink + h - 2, accent);

        Font font = Minecraft.getInstance().font;

        // ── 5. Slot index + hairline separator ───────────────────────────
        int contentLeft = x + LEFT_PAD;
        if (slotIndex >= 0) {
            String indexText = String.format(Locale.ROOT, "%02d", slotIndex + 1);
            int indexColor = hovered ? INDEX_HOVER : INDEX_NORMAL;
            int indexX = x + LEFT_PAD;
            int indexY = y + sink + (h - font.lineHeight) / 2 + 1;
            g.drawString(font, indexText, indexX + 1, indexY + 1, TEXT_SHADOW, false);
            g.drawString(font, indexText, indexX, indexY, indexColor, false);

            // Vertical hairline separator between index and label.
            int sepX = x + LEFT_PAD + INDEX_GUTTER - 8;
            g.fill(sepX, y + sink + 8, sepX + 1, y + sink + h - 8,
                   hovered ? FRAME_HOVER : FRAME_NORMAL);
            contentLeft = x + LEFT_PAD + INDEX_GUTTER;
        }

        // ── 6. Label (uppercase, optional hover letter-spacing) ──────────
        String label = getMessage().getString().toUpperCase(Locale.ROOT);
        int textColor = hovered ? TEXT_HOVER : TEXT_NORMAL;
        int labelAreaLeft = contentLeft;
        int labelAreaRight = x + w - RIGHT_PAD - CHAMFER;
        int labelAreaW = labelAreaRight - labelAreaLeft;

        int labelW = hovered ? letterSpacedWidth(font, label, 1) : font.width(label);
        int textX = labelAreaLeft + Math.max(0, (labelAreaW - labelW) / 2);
        int textY = y + sink + (h - font.lineHeight) / 2 + 1;

        if (hovered) {
            drawLetterSpaced(g, font, label, textX, textY, textColor, TEXT_SHADOW, 1);
        } else {
            g.drawString(font, label, textX + 1, textY + 1, TEXT_SHADOW, false);
            g.drawString(font, label, textX, textY, textColor, false);
        }

        // ── 7. Right-side indicator ──────────────────────────────────────
        int rightCenterY = y + sink + h / 2;
        if (hovered) {
            // Chevron "›" with a gentle 0..2 px horizontal nudge, looped.
            float phase = (System.currentTimeMillis() % 1200L) / 1200.0F; // 0..1
            int nudge = (int) Math.round(Math.sin(phase * Math.PI * 2.0) * 1.5 + 1.5);
            String chev = "\u203A"; // ›
            int chevX = x + w - RIGHT_PAD - font.width(chev) - CHAMFER + nudge;
            int chevY = rightCenterY - font.lineHeight / 2;
            g.drawString(font, chev, chevX + 1, chevY + 1, TEXT_SHADOW, false);
            g.drawString(font, chev, chevX, chevY, accent, false);
        } else {
            // Idle status dot — 2×2 px in dim olive.
            int dotX = x + w - RIGHT_PAD - 2 - CHAMFER;
            g.fill(dotX, rightCenterY - 1, dotX + 2, rightCenterY + 1, ACCENT_NORMAL & 0x80FFFFFF);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Geometry helpers — chamfered rectangle (single cut on top-right).
    // ──────────────────────────────────────────────────────────────────────

    /** Solid-colour fill of a rectangle with the top-right corner chamfered. */
    private static void fillChamferedRect(GuiGraphics g, int x, int y, int w, int h, int color) {
        // Top band — y in [0, CHAMFER), shrinks horizontally on the right edge.
        for (int row = 0; row < CHAMFER; row++) {
            int rightCut = CHAMFER - row;
            g.fill(x, y + row, x + w - rightCut, y + row + 1, color);
        }
        // Rest — full width.
        g.fill(x, y + CHAMFER, x + w, y + h, color);
    }

    /** Three-stop vertical gradient fill of a chamfered rectangle. */
    private static void fillBodyThreeStop(GuiGraphics g, int x, int y, int w, int h,
                                          int colorTop, int colorMid, int colorBot) {
        int half = h / 2;
        // Top half — top→mid gradient, respecting the chamfer for the very top rows.
        for (int row = 0; row < CHAMFER; row++) {
            int rightCut = CHAMFER - row;
            float t0 = row / (float) h;
            float t1 = (row + 1) / (float) h;
            int c0 = blend(colorTop, colorMid, Math.min(1f, t0 * 2f));
            int c1 = blend(colorTop, colorMid, Math.min(1f, t1 * 2f));
            g.fillGradient(x, y + row, x + w - rightCut, y + row + 1, c0, c1);
        }
        g.fillGradient(x, y + CHAMFER, x + w, y + half, colorTop, colorMid);
        // Bottom half — mid→bot gradient.
        g.fillGradient(x, y + half, x + w, y + h, colorMid, colorBot);
    }

    /** 1-px outline of a chamfered rectangle. */
    private static void drawChamferFrame(GuiGraphics g, int x, int y, int w, int h, int color) {
        // Top edge — stops at the start of the right chamfer.
        g.fill(x, y, x + w - CHAMFER, y + 1, color);
        // Top-right diagonal stair.
        for (int i = 0; i < CHAMFER; i++) {
            g.fill(x + w - CHAMFER + i, y + i, x + w - CHAMFER + i + 1, y + i + 1, color);
        }
        // Right edge — starts under the chamfer.
        g.fill(x + w - 1, y + CHAMFER, x + w, y + h, color);
        // Bottom edge — full width.
        g.fill(x, y + h - 1, x + w, y + h, color);
        // Left edge — full height.
        g.fill(x, y, x + 1, y + h, color);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Text helpers.
    // ──────────────────────────────────────────────────────────────────────

    private static int letterSpacedWidth(Font font, String text, int spacing) {
        if (text.isEmpty()) return 0;
        int total = 0;
        for (int i = 0; i < text.length(); i++) {
            total += font.width(String.valueOf(text.charAt(i)));
        }
        return total + spacing * (text.length() - 1);
    }

    private static void drawLetterSpaced(GuiGraphics g, Font font, String text,
                                         int x, int y, int color, int shadow, int spacing) {
        int cursor = x;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            g.drawString(font, ch, cursor + 1, y + 1, shadow, false);
            g.drawString(font, ch, cursor, y, color, false);
            cursor += font.width(ch) + spacing;
        }
    }

    /** Linear blend between two ARGB colours (per-channel). */
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
     * Filled rounded rectangle (legacy API, kept for screens still calling it
     * such as {@link com.frostlogic.warproject.client.screen.AboutScreen}).
     * Maps onto a solid-colour chamfered fill so older screens stay coherent.
     */
    public static void drawRoundedRectFilled(GuiGraphics g, int x, int y, int w, int h, int color) {
        fillChamferedRect(g, x, y, w, h, color);
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
