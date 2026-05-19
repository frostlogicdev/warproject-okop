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
    private static final int INNER_RADIUS = 36;
    private static final int OUTER_RADIUS = 96;
    /** Number of triangle-strip steps used to approximate the arc within one sector. */
    private static final int SEGMENTS_PER_SECTOR = 24;

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

        // Dim background
        gfx.fill(0, 0, width, height, (alpha / 2) << 24);

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
            // §9.2 colors: hovered ≈ #80C8FFFF, normal ≈ #80808080
            int r, g, b;
            if (isHover) {
                r = 0xC8;
                g = 0xE0;
                b = 0xFF;
            } else {
                r = 0x80;
                g = 0x80;
                b = 0x80;
            }
            int a = Math.min(0x80, (int) ((alpha / 255.0F) * 0x80));

            double sStart = startAngle + i * sweep;
            double sEnd = sStart + sweep;

            BufferBuilder buf = Tesselator.getInstance()
                    .begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            for (int s = 0; s <= SEGMENTS_PER_SECTOR; s++) {
                double a2 = sStart + (sEnd - sStart) * s / (double) SEGMENTS_PER_SECTOR;
                float cos = (float) Math.cos(a2);
                float sin = (float) Math.sin(a2);
                buf.addVertex(mat, cx + cos * innerR, cy + sin * innerR, 0.0F).setColor(r, g, b, a);
                buf.addVertex(mat, cx + cos * outerR, cy + sin * outerR, 0.0F).setColor(r, g, b, a);
            }
            BufferUploader.drawWithShader(buf.buildOrThrow());
        }

        RenderSystem.disableBlend();
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
            int color = sectors.get(i) == hoveredItem ? 0xFFFFFF : 0xCCCCCC;
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

        gfx.drawCenteredString(font, name, cx, cy - 14, (alpha << 24) | 0xFFFFFF);
        gfx.drawCenteredString(font, faction, cx, cy - 2, (alpha << 24) | 0xFFFFFF);
        gfx.drawCenteredString(font, statusComp, cx, cy + 10, (alpha << 24) | 0xCCCCCC);

        if (targetView.collaborator()) {
            gfx.drawCenteredString(font,
                    Component.translatable("wp.collab.tag").withStyle(ChatFormatting.RED),
                    cx, cy + 22, (alpha << 24) | 0xFF5555);
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
