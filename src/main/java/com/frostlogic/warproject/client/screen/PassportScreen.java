package com.frostlogic.warproject.client.screen;

import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.attachment.PlayerState;
import com.frostlogic.warproject.server.passport.PassportData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Client-side passport viewer screen showing all passport fields.
 * <p>
 * Opened by {@code ClientPayloadHandler.onPassportSnapshot} in response to a
 * server-sent {@link com.frostlogic.warproject.network.payload.s2c.PassportSnapshotPayload}.
 * Displays passport id, faction, RP-name, date of birth, status, and acceptance info,
 * along with a trophy badge if the passport was captured.
 * <p>
 * Closeable on ESC. Uses a dark themed background.
 * <p>
 * Requirements: 12.3 — Design: §9.3.
 */
public class PassportScreen extends Screen {

    private static final int BACKGROUND_COLOR = 0xFF14110E;
    private static final int PANEL_COLOR = 0xFF1F1A14;
    private static final int PANEL_BORDER = 0xFF6B5A33;
    private static final int TITLE_COLOR = 0xFFE0C97A;
    private static final int LABEL_COLOR = 0xFFE0E0E0;
    private static final int TROPHY_COLOR = 0xFFFF5555;

    private static final int PANEL_WIDTH = 240;
    private static final int PANEL_HEIGHT = 180;
    private static final int LINE_HEIGHT = 12;

    private final PassportData data;

    public PassportScreen(PassportData data) {
        super(Component.translatable("wp.passport.title"));
        this.data = data;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Solid dark background blocks the underlying world.
        g.fill(0, 0, this.width, this.height, BACKGROUND_COLOR);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int panelLeft = centerX - PANEL_WIDTH / 2;
        int panelTop = centerY - PANEL_HEIGHT / 2;
        int panelRight = panelLeft + PANEL_WIDTH;
        int panelBottom = panelTop + PANEL_HEIGHT;

        // Themed inner panel with a thin border.
        g.fill(panelLeft, panelTop, panelRight, panelBottom, PANEL_COLOR);
        g.fill(panelLeft, panelTop, panelRight, panelTop + 1, PANEL_BORDER);
        g.fill(panelLeft, panelBottom - 1, panelRight, panelBottom, PANEL_BORDER);
        g.fill(panelLeft, panelTop, panelLeft + 1, panelBottom, PANEL_BORDER);
        g.fill(panelRight - 1, panelTop, panelRight, panelBottom, PANEL_BORDER);

        // Title centered at the top of the panel.
        g.drawCenteredString(this.font, this.title, centerX, panelTop + 8, TITLE_COLOR);

        int textLeft = panelLeft + 12;
        int y = panelTop + 26;

        g.drawString(this.font,
                Component.translatable("wp.passport.id", data.passportId()),
                textLeft, y, LABEL_COLOR, false);
        y += LINE_HEIGHT;

        Component factionName = factionDisplayName(data.faction());
        g.drawString(this.font,
                Component.translatable("wp.passport.faction", factionName),
                textLeft, y, LABEL_COLOR, false);
        y += LINE_HEIGHT;

        g.drawString(this.font,
                Component.translatable("wp.passport.name", data.rpName(), data.rpSurname()),
                textLeft, y, LABEL_COLOR, false);
        y += LINE_HEIGHT;

        g.drawString(this.font,
                Component.translatable("wp.passport.dob", data.dateOfBirth()),
                textLeft, y, LABEL_COLOR, false);
        y += LINE_HEIGHT;

        Component statusName = playerStateDisplayName(data.status());
        g.drawString(this.font,
                Component.translatable("wp.passport.status", statusName),
                textLeft, y, LABEL_COLOR, false);
        y += LINE_HEIGHT;

        if (data.acceptedAt() != null) {
            g.drawString(this.font,
                    Component.translatable("wp.passport.accepted", formatTimestamp(data.acceptedAt())),
                    textLeft, y, LABEL_COLOR, false);
            y += LINE_HEIGHT;
        }

        if (data.acceptedBy() != null && !data.acceptedBy().isEmpty()) {
            g.drawString(this.font,
                    Component.translatable("wp.passport.accepted_by", data.acceptedBy()),
                    textLeft, y, LABEL_COLOR, false);
            y += LINE_HEIGHT;
        }

        if (data.trophy()) {
            Component trophy = Component.translatable("wp.passport.trophy")
                    .withStyle(ChatFormatting.BOLD)
                    .withStyle(ChatFormatting.RED);
            g.drawCenteredString(this.font, trophy, centerX, panelBottom - 18, TROPHY_COLOR);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static Component factionDisplayName(FactionId faction) {
        return Component.translatable(faction.displayNameKey());
    }

    private static Component playerStateDisplayName(PlayerState state) {
        return Component.translatable("wp.player_state." + state.getSerializedName());
    }

    /**
     * Formats a Unix epoch milliseconds timestamp as a human-readable UTC date/time string.
     */
    private static String formatTimestamp(long epochMillis) {
        SimpleDateFormat fmt = new SimpleDateFormat("dd.MM.yyyy HH:mm 'UTC'", Locale.ROOT);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        return fmt.format(new Date(epochMillis));
    }
}
