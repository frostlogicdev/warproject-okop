package com.frostlogic.warproject.client.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Shared style primitives for the "Paper Passport" UI direction (sepia paper,
 * crimson seals, dark ink, serif-feel via uppercase + letter-spacing).
 *
 * <p>All draw methods take pre-computed pixel positions and use vanilla {@link
 * GuiGraphics} primitives only — no textures, no shaders, no resource packs to
 * keep working. Frames, divider, eagle, and rubber-stamp seal are all drawn
 * procedurally so the look survives any client texture pack.
 *
 * <p>The aesthetic was selected by the user from three mock-up directions on
 * 2026-05-24. See thread `01u16jspaspjgna` history if a redesign is ever
 * considered.
 */
public final class PaperUi {
    private PaperUi() {}

    // ── Palette ─────────────────────────────────────────────────────────
    /** Aged-paper warm cream — the field of every screen. */
    public static final int PAPER          = 0xFFF4E8C8;
    /** Slightly darker paper tint used for stat cells / disabled cards. */
    public static final int PAPER_DARK     = 0xFFECDAB0;
    /** Bright highlight tint for nested cards on top of paper. */
    public static final int PAPER_LIGHT    = 0xFFFFF6E0;
    /** Page bevel — barely visible band that hints at a book spread. */
    public static final int PAPER_SHADOW   = 0xFFD8C599;
    /** Burnt corner mask for the page; very subtle. */
    public static final int CORNER_BURN    = 0x44502810;
    /** Primary ink — used for body type, buttons, and most borders. */
    public static final int INK            = 0xFF1A1410;
    /** Sepia faded ink — used for sub-labels and footer text. */
    public static final int INK_FADED      = 0xFF5A4A30;
    /** Even softer brown — placeholder type and dashed dividers. */
    public static final int INK_MUTED      = 0xFF8B7758;
    /** Soviet-passport crimson — used for seals, the under-title rule, and accents. */
    public static final int SEAL           = 0xFF8B1A1A;
    /** Brighter crimson — used for hover/focus highlights only. */
    public static final int SEAL_HOVER     = 0xFFB42626;
    /** Approval green — confirmation messages (e.g. "passwords match"). */
    public static final int APPROVED       = 0xFF1A6610;
    /** Error red — bumped up from crimson so it does not blur with seals. */
    public static final int ERROR          = 0xFFC22020;

    // ── Background ──────────────────────────────────────────────────────

    /**
     * Fills the entire screen with the paper field plus a couple of warm
     * radial vignettes and burnt-corner shadows. Cheap, no textures.
     */
    public static void drawPaperBackground(GuiGraphics g, int w, int h) {
        // Two-stop vertical gradient: PAPER → PAPER_DARK
        g.fillGradient(0, 0, w, h, PAPER, PAPER_DARK);
        // Vignette band at the very edges
        g.fillGradient(0, 0, w, 24,        0x18000000, 0x00000000);
        g.fillGradient(0, h - 24, w, h,    0x00000000, 0x18000000);
        // Burn marks in the four corners — soft brown stains
        int burn = 64;
        for (int i = 0; i < burn; i++) {
            int alpha = (int) (0x44 * (1f - i / (float) burn));
            int c = (alpha << 24) | (CORNER_BURN & 0x00FFFFFF);
            g.fill(0,            i,        burn - i,        i + 1, c);
            g.fill(w - burn + i, i,        w,               i + 1, c);
            g.fill(0,            h - 1 - i, burn - i,       h - i, c);
            g.fill(w - burn + i, h - 1 - i, w,              h - i, c);
        }
    }

    // ── Frame ───────────────────────────────────────────────────────────

    /** Draws the passport card: paper rectangle, double-rule frame, ink under-rule under the header. */
    public static void drawCardFrame(GuiGraphics g, int x, int y, int w, int h) {
        // Card body — lighter than the page so the card "lifts" off the background.
        g.fill(x, y, x + w, y + h, PAPER_LIGHT);
        // Inner shadow at top + left = page bevel
        g.fill(x, y, x + w, y + 1, PAPER_SHADOW);
        g.fill(x, y, x + 1, y + h, PAPER_SHADOW);
        // 2 px dark outline (double-rule look: 1 px ink + 1 px gap + 1 px ink — fake with double line)
        drawRect(g, x - 1, y - 1, w + 2, h + 2, INK);
        drawRect(g, x - 3, y - 3, w + 6, h + 6, INK);
    }

    /** Hollow rectangle, 1 px stroke. */
    public static void drawRect(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y,         x + w,     y + 1,     color);
        g.fill(x, y + h - 1, x + w,     y + h,     color);
        g.fill(x, y,         x + 1,     y + h,     color);
        g.fill(x + w - 1, y, x + w,     y + h,     color);
    }

    /** Horizontal crimson rule under the header. */
    public static void drawHeaderRule(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y,     x + w, y + 1, SEAL);
        g.fill(x, y + 1, x + w, y + 2, SEAL);
    }

    /** Dashed sepia divider — used between sections. */
    public static void drawDashedRule(GuiGraphics g, int x, int y, int w, int color) {
        for (int i = 0; i < w; i += 4) {
            g.fill(x + i, y, x + i + 2, y + 1, color);
        }
    }

    // ── Decorative glyphs ───────────────────────────────────────────────

    /** Tiny pixel coat-of-arms (a 9×9 cross-with-rays). Drawn in crimson. */
    public static void drawCoat(GuiGraphics g, int cx, int cy) {
        int s = SEAL;
        // Vertical bar
        g.fill(cx - 1, cy - 4, cx + 1, cy + 5, s);
        // Horizontal bar
        g.fill(cx - 4, cy - 1, cx + 5, cy + 1, s);
        // Diagonal dots — fake rays
        g.fill(cx - 3, cy - 3, cx - 2, cy - 2, s);
        g.fill(cx + 2, cy - 3, cx + 3, cy - 2, s);
        g.fill(cx - 3, cy + 2, cx - 2, cy + 3, s);
        g.fill(cx + 2, cy + 2, cx + 3, cy + 3, s);
    }

    /**
     * Round rubber stamp — outline ring + optional text written across.
     * Drawn semi-transparent so it reads as an over-stamp on the paper.
     */
    public static void drawSeal(GuiGraphics g, int cx, int cy, int radius, Component line1, Component line2) {
        // Two concentric rings, slightly transparent for that "smudged stamp" look.
        int ring = (SEAL & 0x00FFFFFF) | 0x80000000; // 50 % alpha
        drawCircleOutline(g, cx, cy, radius,     ring);
        drawCircleOutline(g, cx, cy, radius - 4, ring);
        // Text inside — drawn opaque-ish for legibility.
        Font font = Minecraft.getInstance().font;
        int textColor = (SEAL & 0x00FFFFFF) | 0xCC000000;
        if (line1 != null) {
            int w1 = font.width(line1);
            g.drawString(font, line1, cx - w1 / 2, cy - font.lineHeight, textColor, false);
        }
        if (line2 != null) {
            int w2 = font.width(line2);
            g.drawString(font, line2, cx - w2 / 2, cy + 2, textColor, false);
        }
    }

    /** Hollow circle, 1 px thick, drawn with 360 polar fills. */
    public static void drawCircleOutline(GuiGraphics g, int cx, int cy, int radius, int color) {
        // Two passes at slightly different radii so the line never has gaps.
        for (int step = 0; step < 360; step++) {
            double rad = Math.toRadians(step);
            int x = cx + (int) Math.round(radius * Math.cos(rad));
            int y = cy + (int) Math.round(radius * Math.sin(rad));
            g.fill(x, y, x + 1, y + 1, color);
            int x2 = cx + (int) Math.round((radius - 1) * Math.cos(rad));
            int y2 = cy + (int) Math.round((radius - 1) * Math.sin(rad));
            g.fill(x2, y2, x2 + 1, y2 + 1, color);
        }
    }

    /** Filled disc — used as faction emblem background. */
    public static void drawDisc(GuiGraphics g, int cx, int cy, int radius, int color) {
        int r2 = radius * radius;
        for (int dy = -radius; dy <= radius; dy++) {
            int span = (int) Math.sqrt(r2 - dy * dy);
            g.fill(cx - span, cy + dy, cx + span, cy + dy + 1, color);
        }
    }

    // ── Text helpers ────────────────────────────────────────────────────

    /** Draw centered uppercase string with letter-spacing (1 px gap between glyphs). */
    public static void drawSpacedCentered(GuiGraphics g, Font font, String text, int cx, int y, int color) {
        String upper = text.toUpperCase(java.util.Locale.ROOT);
        // Compute total width with 1 px gap between chars
        int total = -1;
        for (int i = 0; i < upper.length(); i++) {
            total += font.width(String.valueOf(upper.charAt(i))) + 1;
        }
        if (total < 0) total = 0;
        int x = cx - total / 2;
        for (int i = 0; i < upper.length(); i++) {
            String ch = String.valueOf(upper.charAt(i));
            g.drawString(font, ch, x, y, color, false);
            x += font.width(ch) + 1;
        }
    }

    /** Width of a string when rendered with 1 px letter-spacing (for centring around it). */
    public static int spacedWidth(Font font, String text) {
        String upper = text.toUpperCase(java.util.Locale.ROOT);
        int total = -1;
        for (int i = 0; i < upper.length(); i++) {
            total += font.width(String.valueOf(upper.charAt(i))) + 1;
        }
        return Math.max(total, 0);
    }
}
