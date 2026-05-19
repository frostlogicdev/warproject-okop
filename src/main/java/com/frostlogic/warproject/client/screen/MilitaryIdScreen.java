package com.frostlogic.warproject.client.screen;

import com.frostlogic.warproject.server.militaryid.MilitaryIdData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Client-side Military ID viewer screen showing all military identity fields.
 * <p>
 * Opened by {@code ClientPayloadHandler.onMilitaryIdSnapshot} in response to a
 * server-sent {@link com.frostlogic.warproject.network.payload.s2c.MilitaryIdSnapshotPayload}.
 * Displays faction, rank, subdivision (if non-empty), awards (if non-empty),
 * enlistment date, and accepting officer.
 * <p>
 * Uses the same dark-themed visual style as {@link PassportScreen}: dark background,
 * bordered center panel, gold title text, and light-colored label text.
 * <p>
 * Closeable on ESC. Does not pause the game.
 * <p>
 * Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 16.1, 16.6
 * Design: §2 Military ID System — MilitaryIdScreen
 */
public class MilitaryIdScreen extends Screen {

    private static final int BACKGROUND_COLOR = 0xFF14110E;
    private static final int PANEL_COLOR = 0xFF1F1A14;
    private static final int PANEL_BORDER = 0xFF6B5A33;
    private static final int TITLE_COLOR = 0xFFE0C97A;
    private static final int LABEL_COLOR = 0xFFE0E0E0;

    private static final int PANEL_WIDTH = 260;
    private static final int LINE_HEIGHT = 12;
    private static final int AWARD_LINE_HEIGHT = 18;
    private static final int AWARD_ICON_SIZE = 16;
    private static final int PADDING_TOP = 26;
    private static final int PADDING_BOTTOM = 12;
    private static final int PADDING_LEFT = 12;

    /** Placeholder icon for award badges (16×16). */
    private static final ResourceLocation AWARD_ICON =
            ResourceLocation.fromNamespaceAndPath("warproject", "textures/gui/award_badge.png");

    private final MilitaryIdData data;

    public MilitaryIdScreen(MilitaryIdData data) {
        super(Component.translatable("wp.military_id.title"));
        this.data = data;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, BACKGROUND_COLOR);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        int panelHeight = calculatePanelHeight();
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int panelLeft = centerX - PANEL_WIDTH / 2;
        int panelTop = centerY - panelHeight / 2;
        int panelRight = panelLeft + PANEL_WIDTH;
        int panelBottom = panelTop + panelHeight;

        // Themed inner panel with a thin border (matching PassportScreen style).
        g.fill(panelLeft, panelTop, panelRight, panelBottom, PANEL_COLOR);
        g.fill(panelLeft, panelTop, panelRight, panelTop + 1, PANEL_BORDER);
        g.fill(panelLeft, panelBottom - 1, panelRight, panelBottom, PANEL_BORDER);
        g.fill(panelLeft, panelTop, panelLeft + 1, panelBottom, PANEL_BORDER);
        g.fill(panelRight - 1, panelTop, panelRight, panelBottom, PANEL_BORDER);

        // Title centered at the top of the panel.
        g.drawCenteredString(this.font, this.title, centerX, panelTop + 8, TITLE_COLOR);

        int textLeft = panelLeft + PADDING_LEFT;
        int y = panelTop + PADDING_TOP;

        // Faction
        g.drawString(this.font,
                Component.translatable("wp.military_id.faction", data.faction()),
                textLeft, y, LABEL_COLOR, false);
        y += LINE_HEIGHT;

        // Rank
        g.drawString(this.font,
                Component.translatable("wp.military_id.rank", data.rankId()),
                textLeft, y, LABEL_COLOR, false);
        y += LINE_HEIGHT;

        // Subdivision (omit if empty)
        if (!data.subdivision().isEmpty()) {
            g.drawString(this.font,
                    Component.translatable("wp.military_id.subdivision", data.subdivision()),
                    textLeft, y, LABEL_COLOR, false);
            y += LINE_HEIGHT;
        }

        // Awards (omit section if empty)
        List<String> awards = data.awards();
        if (awards != null && !awards.isEmpty()) {
            g.drawString(this.font,
                    Component.translatable("wp.military_id.awards"),
                    textLeft, y, LABEL_COLOR, false);
            y += LINE_HEIGHT;

            for (String award : awards) {
                // Render 16×16 award badge icon
                g.blit(AWARD_ICON, textLeft + 4, y, 0, 0,
                        AWARD_ICON_SIZE, AWARD_ICON_SIZE, AWARD_ICON_SIZE, AWARD_ICON_SIZE);
                // Render award name next to the icon
                g.drawString(this.font,
                        Component.literal(award),
                        textLeft + 4 + AWARD_ICON_SIZE + 4, y + 4, LABEL_COLOR, false);
                y += AWARD_LINE_HEIGHT;
            }
        }

        // Enlistment date
        g.drawString(this.font,
                Component.translatable("wp.military_id.enlistment_date", data.enlistmentDate()),
                textLeft, y, LABEL_COLOR, false);
        y += LINE_HEIGHT;

        // Accepting officer
        g.drawString(this.font,
                Component.translatable("wp.military_id.accepting_officer", data.acceptingOfficer()),
                textLeft, y, LABEL_COLOR, false);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Calculates the dynamic panel height based on which fields are present.
     * Empty subdivision and empty awards list are omitted.
     */
    private int calculatePanelHeight() {
        int height = PADDING_TOP + PADDING_BOTTOM;

        // Faction + Rank (always present)
        height += LINE_HEIGHT * 2;

        // Subdivision (only if non-empty)
        if (!data.subdivision().isEmpty()) {
            height += LINE_HEIGHT;
        }

        // Awards section (only if non-empty)
        List<String> awards = data.awards();
        if (awards != null && !awards.isEmpty()) {
            height += LINE_HEIGHT; // "Награды:" label
            height += AWARD_LINE_HEIGHT * awards.size();
        }

        // Enlistment date + Accepting officer (always present)
        height += LINE_HEIGHT * 2;

        return height;
    }
}
