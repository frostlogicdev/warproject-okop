package com.frostlogic.warproject.client.screen;

import com.frostlogic.warproject.client.widget.PaperButton;
import com.frostlogic.warproject.client.widget.PaperUi;
import com.frostlogic.warproject.network.payload.c2s.FactionChoicePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client-side confirmation modal for faction choice.
 *
 * <p>Opened when the server sends {@code OpenFactionChoicePayload} after the
 * player right-clicks a {@code FactionNpcEntity}. Renders as a single
 * passport-style card with the chosen faction's emblem letter + name + a stern
 * irreversibility note, then a primary "swear the oath" button in DANGER red
 * and a secondary "cancel" button.
 *
 * <p>Sends a C2S {@link FactionChoicePayload} on confirm.
 *
 * <p>Requirements: 6.3 — Design: §3, §8.2.
 */
public class FactionChoiceScreen extends Screen {

    private static final int CARD_W = 240;
    // CARD_H was 196 → cramped: the irreversibility line collided with the
    // primary button, the cancel button sat on top of the footer dashed rule,
    // and the footer text overlapped the cancel widget. New value gives 14-16 px
    // gaps between every label, the disc, the buttons and the footer.
    private static final int CARD_H = 240;
    private static final int BTN_W = 200;
    private static final int BTN_H = 22;

    // Vertical layout (offsets from cardY).  Kept as named constants so we can
    // reason about gaps in one place — every value is at least 12 px below the
    // bottom of the previous element to avoid overlap with 9-px glyphs.
    private static final int Y_COAT        = 14;
    private static final int Y_SUBTITLE    = 32;
    private static final int Y_TITLE       = 48;
    private static final int Y_RULE        = 64;
    private static final int Y_DISC_CENTER = 96;
    private static final int DISC_R        = 16;
    private static final int Y_FACTION     = 126;
    private static final int Y_QUESTION    = 142;
    private static final int Y_WARNING     = 156;
    private static final int Y_BTN_CONFIRM = 174;          // bottom = 196
    private static final int Y_BTN_CANCEL  = 200;          // bottom = 218 (BTN_H-4 = 18)
    private static final int Y_FOOT_RULE   = CARD_H - 18;  // 222
    private static final int Y_FOOT_TEXT   = CARD_H - 12;  // 228

    private final String factionId;
    private final Component factionDisplayName;

    /**
     * @param factionId serialized faction ID (e.g. "zarnavia" or "chernogryad")
     */
    public FactionChoiceScreen(String factionId) {
        super(Component.translatable("wp.faction.choice_title"));
        this.factionId = factionId;
        this.factionDisplayName = resolveFactionDisplayName(factionId);
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int cardY = (this.height - CARD_H) / 2;

        // Primary CTA — irreversible, so DANGER variant (crimson body).
        this.addRenderableWidget(new PaperButton(
                centerX - BTN_W / 2,
                cardY + Y_BTN_CONFIRM,
                BTN_W, BTN_H,
                Component.translatable("wp.faction.confirm"),
                PaperButton.Variant.DANGER,
                this::onConfirm));

        // Secondary — quiet "cancel" line under the danger button.
        this.addRenderableWidget(new PaperButton(
                centerX - BTN_W / 2,
                cardY + Y_BTN_CANCEL,
                BTN_W, BTN_H - 4,
                Component.translatable("wp.faction.cancel"),
                PaperButton.Variant.SECONDARY,
                this::onCancel));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Custom paint order — see LoginScreen for rationale.
        int centerX = this.width / 2;
        int cardX = (this.width - CARD_W) / 2;
        int cardY = (this.height - CARD_H) / 2;

        PaperUi.drawPaperBackground(g, this.width, this.height);
        PaperUi.drawCardFrame(g, cardX, cardY, CARD_W, CARD_H);

        // Header
        PaperUi.drawCoat(g, centerX, cardY + Y_COAT);
        g.drawCenteredString(this.font,
                Component.translatable("wp.ui.oath_header"),
                centerX, cardY + Y_SUBTITLE, PaperUi.INK_FADED);
        PaperUi.drawSpacedCentered(g, this.font,
                this.title.getString(),
                centerX, cardY + Y_TITLE, PaperUi.INK);
        PaperUi.drawHeaderRule(g, cardX + 16, cardY + Y_RULE, CARD_W - 32);

        // Faction emblem disc (single, large) — colored per faction.
        int discColor = factionAccent(factionId);
        PaperUi.drawDisc(g, centerX, cardY + Y_DISC_CENTER, DISC_R, PaperUi.PAPER);
        PaperUi.drawCircleOutline(g, centerX, cardY + Y_DISC_CENTER, DISC_R, discColor);
        PaperUi.drawCircleOutline(g, centerX, cardY + Y_DISC_CENTER, DISC_R - 4, discColor);
        // First letter of the faction id.
        String initial = factionInitial(factionId);
        int iw = this.font.width(initial);
        g.drawString(this.font, initial,
                centerX - iw / 2,
                cardY + Y_DISC_CENTER - this.font.lineHeight / 2 + 1,
                discColor, false);

        // Faction name
        PaperUi.drawSpacedCentered(g, this.font,
                factionDisplayName.getString(), centerX, cardY + Y_FACTION, PaperUi.INK);

        // Confirmation question
        Component question = Component.translatable("wp.faction.choice_question", factionDisplayName);
        g.drawCenteredString(this.font, question, centerX, cardY + Y_QUESTION, PaperUi.INK_FADED);

        // Irreversibility warning
        g.drawCenteredString(this.font,
                Component.translatable("wp.ui.oath_irreversible"),
                centerX, cardY + Y_WARNING, PaperUi.SEAL);

        // Footer
        PaperUi.drawDashedRule(g, cardX + 16, cardY + Y_FOOT_RULE, CARD_W - 32, PaperUi.INK_MUTED);
        g.drawString(this.font, "WP · т. 3.0.0", cardX + 18, cardY + Y_FOOT_TEXT, PaperUi.INK_MUTED, false);
        Component foot = Component.translatable("wp.ui.signed_voluntarily");
        g.drawString(this.font, foot,
                cardX + CARD_W - 16 - this.font.width(foot),
                cardY + Y_FOOT_TEXT, PaperUi.INK_MUTED, false);

        for (Renderable r : this.renderables) {
            r.render(g, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No-op: render() does everything in one pass.
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void onConfirm() {
        PacketDistributor.sendToServer(new FactionChoicePayload(factionId));
        this.onClose();
    }

    private void onCancel() {
        this.onClose();
    }

    private static Component resolveFactionDisplayName(String factionId) {
        return switch (factionId) {
            case "zarnavia" -> Component.translatable("wp.faction.zarnavia");
            case "chernogryad" -> Component.translatable("wp.faction.chernogryad");
            default -> Component.literal(factionId);
        };
    }

    private static int factionAccent(String factionId) {
        return switch (factionId) {
            case "zarnavia" -> 0xFF3A6A2A;     // muted green
            case "chernogryad" -> PaperUi.SEAL; // crimson
            default -> PaperUi.INK;
        };
    }

    private static String factionInitial(String factionId) {
        return switch (factionId) {
            case "zarnavia" -> "З";
            case "chernogryad" -> "Ч";
            default -> "?";
        };
    }
}
