package com.frostlogic.warproject.client;

import com.frostlogic.warproject.attachment.PlayerState;
import com.frostlogic.warproject.network.payload.PublicView;
import com.frostlogic.warproject.network.payload.c2s.RadialActionPayload;
import com.frostlogic.warproject.server.radial.RadialMenuItem;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Client-side radial menu screen. Opens <em>only</em> in response to a
 * {@link com.frostlogic.warproject.network.payload.s2c.RadialMenuPayload}
 * received from the server (see SP-7 in design §11) — the client never opens it
 * locally on hotkey press; instead, pressing the configured key sends a
 * {@link com.frostlogic.warproject.network.payload.c2s.RadialOpenRequestPayload}
 * and the server replies with the filtered set of {@link RadialMenuItem}s.
 * <p>
 * Renders an annular ring with one sector per visible item (per design §9.2),
 * a centered target card with the public view of the target, and a hint string
 * for the hovered item. Sectors are rendered via {@link Tesselator} as a
 * {@link VertexFormat.Mode#TRIANGLE_STRIP} between the inner and outer radii.
 * <p>
 * Closes on Esc, repeated press of the radial-menu key, or selection of any
 * sector. On selection it sends a {@link RadialActionPayload}; the server
 * re-validates authorization there (the visible-items list is purely for UX).
 * <p>
 * Requirements: 9.1, 9.4, 9.5, 9.6, 9.7
 * Design: §5.3, §9.2, §11 SP-7
 */
public class RadialMenuScreen extends Screen {
    private static final long ANIMATION_DURATION_MS = 220L;
    private static final int INNER_RADIUS = 42;
    private static final int OUTER_RADIUS = 108;
    /** Number of triangle-strip steps used to approximate the arc within one sector. */
    private static final int SEGMENTS_PER_SECTOR = 28;
    /** Sliver of empty space between adjacent sectors (radians) — crisp dividers. */
    private static final float SECTOR_GAP_RADIANS = 0.012F;

    // ─── Military palette (matches MilitaryButton rev 2) ─────────────────────
    // ARGB packed colours; alpha is then scaled by the animation envelope.
    private static final int COL_BODY_TOP    = 0x2E3A22; // dark olive top
    private static final int COL_BODY_MID    = 0x3E4D2F; // mid olive
    private static final int COL_BODY_BOT    = 0x252E1B; // darkest at bottom
    private static final int COL_BODY_HOVER  = 0x5A7236; // brighter olive hover
    private static final int COL_FRAME       = 0x8AA15A; // pale green outer frame
    private static final int COL_FRAME_HOVER = 0xC7DB94; // bright lime hover frame
    private static final int COL_DIVIDER     = 0x65784A; // sector divider tick
    private static final int COL_CARD_FILL   = 0x161B11; // center card body
    private static final int COL_CARD_FRAME  = 0xB7CC7E; // center card frame

    private final UUID targetUuid;
    /** Ordered list of sectors. EnumSet preserves natural enum order. */
    private final List<RadialMenuItem> sectors;
    private final PublicView targetView;

    private long openedAt;
    private boolean closing;
    private long closingStart;
    @Nullable
    private RadialMenuItem hoveredItem;

    public RadialMenuScreen(UUID targetUuid, EnumSet<RadialMenuItem> visibleItems, PublicView targetView) {
        super(Component.translatable("wp.radial.title"));
        this.targetUuid = targetUuid;
        this.sectors = new ArrayList<>(visibleItems);
        this.targetView = targetView;
    }

    @Override
    protected void init() {
        this.openedAt = System.currentTimeMillis();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Esc must run our closing animation, not vanilla's instant {@code setScreen(null)}.
     */
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();
        float t = closing
                ? Math.max(0.0F, 1.0F - (now - closingStart) / (float) ANIMATION_DURATION_MS)
                : Math.min(1.0F, (now - openedAt) / (float) ANIMATION_DURATION_MS);

        if (closing && t <= 0.001F) {
            this.minecraft.setScreen(null);
            return;
        }

        float eased = closing ? t : easeOutBack(t);
        int alpha = (int) (t * 220.0F) & 0xFF;
        int cx = width / 2;
        int cy = height / 2;

        // Dim background — a touch darker than vanilla, with a hint of olive
        // so the ring reads against it without overpowering the world view.
        int bgAlpha = (int) (t * 160.0F) & 0xFF;
        gfx.fill(0, 0, width, height, (bgAlpha << 24) | 0x0A0F06);

        // Hover detection in polar coordinates (only when fully open and not closing)
        hoveredItem = !closing ? hitTest(mouseX, mouseY, cx, cy, eased) : null;

        // Annular sectors
        if (!sectors.isEmpty()) {
            renderSectors(gfx, cx, cy, eased, alpha);
            renderLabels(gfx, cx, cy, eased, alpha);
        } else {
            gfx.drawCenteredString(font,
                    Component.translatable("wp.radial.no_actions").withStyle(ChatFormatting.GRAY),
                    cx, cy - OUTER_RADIUS - 14, (alpha << 24) | 0xCCCCCC);
        }

        // Center card with target's public view
        renderTargetCard(gfx, cx, cy, alpha);

        // Hint for hovered item
        if (hoveredItem != null) {
            Component hint = Component.translatable("wp.radial.item." + lowerName(hoveredItem) + ".hint")
                    .withStyle(ChatFormatting.GRAY);
            gfx.drawCenteredString(font, hint, cx, cy + OUTER_RADIUS + 14, (alpha << 24) | 0xCCCCCC);
        }
    }

    /**
     * Returns the sector currently under the cursor, or {@code null} if outside
     * the annular ring or no sectors are present.
     */
    @Nullable
    private RadialMenuItem hitTest(int mouseX, int mouseY, int cx, int cy, float eased) {
        if (sectors.isEmpty()) {
            return null;
        }
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double dist = Math.hypot(dx, dy);
        // Use eased radii so hover only becomes active as the ring grows in
        double inner = INNER_RADIUS * eased;
        double outer = OUTER_RADIUS * eased;
        if (dist < inner || dist > outer) {
            return null;
        }
        double sweep = (2 * Math.PI) / sectors.size();
        // atan2 returns -π..π with 0 at +X; we want sectors to start at top (-π/2)
        // and proceed clockwise, matching the renderer.
        double angle = Math.atan2(dy, dx);
        double normalized = angle - (-Math.PI / 2.0);
        while (normalized < 0) normalized += 2 * Math.PI;
        while (normalized >= 2 * Math.PI) normalized -= 2 * Math.PI;
        int idx = (int) Math.floor(normalized / sweep);
        if (idx < 0 || idx >= sectors.size()) {
            return null;
        }
        return sectors.get(idx);
    }

    private void renderSectors(GuiGraphics gfx, int cx, int cy, float eased, int alpha) {
        Matrix4f mat = gfx.pose().last().pose();
        float innerR = INNER_RADIUS * eased;
        float outerR = OUTER_RADIUS * eased;
        double startAngle = -Math.PI / 2.0;
        double sweep = (2 * Math.PI) / sectors.size();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        for (int i = 0; i < sectors.size(); i++) {
            boolean isHover = sectors.get(i) == hoveredItem;

            // Leave a sliver of empty space between sectors so each one reads
            // as a distinct chip instead of a continuous ring. The gap is
            // applied symmetrically; on hover we shrink the gap slightly so
            // the hovered sector "breathes outward" a hair.
            double gap = isHover ? SECTOR_GAP_RADIANS * 0.6F : SECTOR_GAP_RADIANS;
            double sStart = startAngle + i * sweep + gap;
            double sEnd = startAngle + (i + 1) * sweep - gap;

            // 1) Body fill with vertical gradient (top→mid→bottom). Triangle
            //    strip from inner edge to outer edge gives per-vertex colours,
            //    so we lerp by the screen-space Y of each vertex relative to
            //    the ring centre.
            BufferBuilder buf = Tesselator.getInstance()
                    .begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            for (int s = 0; s <= SEGMENTS_PER_SECTOR; s++) {
                double a2 = sStart + (sEnd - sStart) * s / (double) SEGMENTS_PER_SECTOR;
                float cos = (float) Math.cos(a2);
                float sin = (float) Math.sin(a2);
                float ix = cos * innerR;
                float iy = sin * innerR;
                float ox = cos * outerR;
                float oy = sin * outerR;
                int innerCol = gradient(iy / outerR, isHover);
                int outerCol = gradient(oy / outerR, isHover);
                int innerAlpha = scaleAlpha(innerCol, alpha, 0xD0);
                int outerAlpha = scaleAlpha(outerCol, alpha, 0xD0);
                addVertexColor(buf, mat, cx + ix, cy + iy, innerCol, innerAlpha);
                addVertexColor(buf, mat, cx + ox, cy + oy, outerCol, outerAlpha);
            }
            BufferUploader.drawWithShader(buf.buildOrThrow());

            // 2) Outer-edge accent ring (1 px arc just outside outerR). On
            //    hover the colour brightens to lime; idle is pale green.
            int frameCol = isHover ? COL_FRAME_HOVER : COL_FRAME;
            int frameAlpha = scaleAlpha(frameCol, alpha, isHover ? 0xFF : 0xC8);
            float frameThickness = isHover ? 2.0F : 1.0F;
            drawArc(mat, cx, cy, outerR, outerR - frameThickness, sStart, sEnd, frameCol, frameAlpha);

            // 3) Inner-edge thin line (matches outer edge for a closed look).
            drawArc(mat, cx, cy, innerR + 1.0F, innerR, sStart, sEnd, frameCol, frameAlpha);

            // 4) Radial tick lines at sector borders — only on idle sectors
            //    next to the hovered one so the dividers don't clutter when
            //    the hover frame is already drawing a bright outline.
            int dividerAlpha = scaleAlpha(COL_DIVIDER, alpha, 0xA0);
            drawRadialLine(mat, cx, cy, innerR, outerR, sStart, COL_DIVIDER, dividerAlpha);
            drawRadialLine(mat, cx, cy, innerR, outerR, sEnd, COL_DIVIDER, dividerAlpha);
        }

        RenderSystem.disableBlend();
    }

    /** Vertical-gradient colour at a normalised Y in [-1, 1] across the ring. */
    private static int gradient(float yNorm, boolean hover) {
        if (hover) {
            // On hover the whole chip gets a single brighter olive — gradient
            // is subtle (just a touch darker at the bottom).
            int top = COL_BODY_HOVER;
            int bot = darken(COL_BODY_HOVER, 0.78F);
            return lerpRgb(top, bot, (yNorm + 1.0F) * 0.5F);
        }
        // Idle: 3-stop gradient (top → mid → bottom).
        float t = (yNorm + 1.0F) * 0.5F;
        if (t < 0.5F) {
            return lerpRgb(COL_BODY_TOP, COL_BODY_MID, t * 2.0F);
        }
        return lerpRgb(COL_BODY_MID, COL_BODY_BOT, (t - 0.5F) * 2.0F);
    }

    private static int lerpRgb(int a, int b, float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int rr = (int) (ar + (br - ar) * t);
        int rg = (int) (ag + (bg - ag) * t);
        int rb = (int) (ab + (bb - ab) * t);
        return (rr << 16) | (rg << 8) | rb;
    }

    private static int darken(int rgb, float f) {
        int r = Math.max(0, (int) (((rgb >> 16) & 0xFF) * f));
        int g = Math.max(0, (int) (((rgb >> 8) & 0xFF) * f));
        int b = Math.max(0, (int) ((rgb & 0xFF) * f));
        return (r << 16) | (g << 8) | b;
    }

    /** Combines the screen-fade alpha (0..255) with a per-element alpha. */
    private static int scaleAlpha(int rgb, int screenAlpha, int maxAlpha) {
        return (int) ((screenAlpha / 255.0F) * maxAlpha) & 0xFF;
    }

    private static void addVertexColor(BufferBuilder buf, Matrix4f mat, float x, float y, int rgb, int alpha) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        buf.addVertex(mat, x, y, 0.0F).setColor(r, g, b, alpha);
    }

    /** Draws a thin arc (annular strip) between r1 and r2 across [s, e]. */
    private static void drawArc(Matrix4f mat, int cx, int cy, float r1, float r2,
                                 double s, double e, int rgb, int alpha) {
        BufferBuilder buf = Tesselator.getInstance()
                .begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        int steps = 24;
        for (int i = 0; i <= steps; i++) {
            double a = s + (e - s) * i / (double) steps;
            float cos = (float) Math.cos(a);
            float sin = (float) Math.sin(a);
            addVertexColor(buf, mat, cx + cos * r1, cy + sin * r1, rgb, alpha);
            addVertexColor(buf, mat, cx + cos * r2, cy + sin * r2, rgb, alpha);
        }
        BufferUploader.drawWithShader(buf.buildOrThrow());
    }

    /** Draws a 1px-wide radial line from inner to outer radius at angle a. */
    private static void drawRadialLine(Matrix4f mat, int cx, int cy, float innerR, float outerR,
                                        double angle, int rgb, int alpha) {
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        // Perpendicular offset (half-thickness) so we get a 1px strip rather than a hairline.
        float px = -sin * 0.5F;
        float py = cos * 0.5F;
        BufferBuilder buf = Tesselator.getInstance()
                .begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        addVertexColor(buf, mat, cx + cos * innerR - px, cy + sin * innerR - py, rgb, alpha);
        addVertexColor(buf, mat, cx + cos * innerR + px, cy + sin * innerR + py, rgb, alpha);
        addVertexColor(buf, mat, cx + cos * outerR - px, cy + sin * outerR - py, rgb, alpha);
        addVertexColor(buf, mat, cx + cos * outerR + px, cy + sin * outerR + py, rgb, alpha);
        BufferUploader.drawWithShader(buf.buildOrThrow());
    }

    private void renderLabels(GuiGraphics gfx, int cx, int cy, float eased, int alpha) {
        float labelR = (INNER_RADIUS + OUTER_RADIUS) / 2.0F * eased;
        double startAngle = -Math.PI / 2.0;
        double sweep = (2 * Math.PI) / sectors.size();
        for (int i = 0; i < sectors.size(); i++) {
            double midAngle = startAngle + (i + 0.5) * sweep;
            int lx = cx + (int) Math.round(Math.cos(midAngle) * labelR);
            int ly = cy + (int) Math.round(Math.sin(midAngle) * labelR) - 4;
            Component label = Component.translatable("wp.radial.item." + lowerName(sectors.get(i)));
            boolean hover = sectors.get(i) == hoveredItem;
            // Cream-on-olive when hovered (matches MilitaryButton hover text),
            // muted pale-green when idle. The drop shadow under each label is
            // free via drawCenteredString.
            int color = hover ? 0xF1E9CC : 0xC9D4A6;
            gfx.drawCenteredString(font, label, lx, ly, (alpha << 24) | color);
        }
    }

    private void renderTargetCard(GuiGraphics gfx, int cx, int cy, int alpha) {
        Component name = targetView.rpFullName() != null
                ? Component.literal(targetView.rpFullName())
                : Component.translatable("wp.radial.unknown_name").withStyle(ChatFormatting.ITALIC);
        Component faction = Component.translatable(targetView.faction().displayNameKey())
                .withStyle(targetView.faction().color());

        PlayerState status = targetView.status();
        Component statusComp = Component.translatable("wp.player_state." + status.getSerializedName())
                .withStyle(ChatFormatting.GRAY);

        // Dark slab behind the centre text — a "passport window" framed in pale
        // green. Sized to fit name + faction + status (and optional collab tag).
        boolean hasCollab = targetView.collaborator();
        int cardW = 86;
        int cardH = hasCollab ? 56 : 44;
        int cardX = cx - cardW / 2;
        int cardY = cy - cardH / 2;

        int cardAlphaMain = (alpha * 0xE0) / 255;
        int cardAlphaFrame = (alpha * 0xFF) / 255;

        // Body
        gfx.fill(cardX, cardY, cardX + cardW, cardY + cardH,
                (cardAlphaMain << 24) | COL_CARD_FILL);
        // 1px frame
        int frameCol = (cardAlphaFrame << 24) | COL_CARD_FRAME;
        gfx.fill(cardX, cardY, cardX + cardW, cardY + 1, frameCol);
        gfx.fill(cardX, cardY + cardH - 1, cardX + cardW, cardY + cardH, frameCol);
        gfx.fill(cardX, cardY, cardX + 1, cardY + cardH, frameCol);
        gfx.fill(cardX + cardW - 1, cardY, cardX + cardW, cardY + cardH, frameCol);
        // Single chamfer in the top-right corner (matches MilitaryButton DNA)
        int chamfer = 3;
        int bgCol = (cardAlphaMain << 24) | 0x000000; // transparent-ish black
        for (int i = 0; i < chamfer; i++) {
            gfx.fill(cardX + cardW - chamfer + i, cardY, cardX + cardW, cardY + 1 + i, 0);
            // Repaint that triangle with a diagonal frame stub
            gfx.fill(cardX + cardW - chamfer + i, cardY + (chamfer - 1 - i),
                    cardX + cardW - chamfer + i + 1, cardY + (chamfer - i), frameCol);
        }

        int textTop = cardY + 6;
        gfx.drawCenteredString(font, name, cx, textTop, (alpha << 24) | 0xF1E9CC);
        gfx.drawCenteredString(font, faction, cx, textTop + 12, (alpha << 24) | 0xFFFFFF);
        gfx.drawCenteredString(font, statusComp, cx, textTop + 24, (alpha << 24) | 0xCFD7B6);

        if (hasCollab) {
            gfx.drawCenteredString(font,
                    Component.translatable("wp.collab.tag").withStyle(ChatFormatting.RED),
                    cx, textTop + 36, (alpha << 24) | 0xFF6E6E);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || closing) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (hoveredItem != null) {
            PacketDistributor.sendToServer(new RadialActionPayload(targetUuid, hoveredItem));
            beginClosing();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Close on Esc only — the radial-menu key now uses hold-to-show, driven
        // externally by WarClientInputHandler (closes when the key is released).
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            beginClosing();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        super.tick();
        // Defense in depth: if the user released the radial-menu key, close
        // the screen. The main close-on-release path is in WarClientInputHandler.
        if (!closing && !WarKeyBindings.RADIAL_MENU.isDown()) {
            beginClosing();
        }
    }

    private void beginClosing() {
        if (closing) {
            return;
        }
        closing = true;
        closingStart = System.currentTimeMillis();
    }

    private static String lowerName(RadialMenuItem item) {
        return item.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static float easeOutBack(float x) {
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        float delta = x - 1.0F;
        return 1.0F + c3 * delta * delta * delta + c1 * delta * delta;
    }
}
