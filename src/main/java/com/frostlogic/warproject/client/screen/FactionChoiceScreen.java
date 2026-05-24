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
    private static final int CARD_H = 196;
    private static final int BTN_W = 200;
    private static final int BTN_H = 22;

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
                cardY + CARD_H - 56,
                BTN_W, BTN_H,
                Component.translatable("wp.faction.confirm"),
                PaperButton.Variant.DANGER,
                this::onConfirm));

        // Secondary — quiet "cancel" line under the danger button.
        this.addRenderableWidget(new PaperButton(
                centerX - BTN_W / 2,
                cardY + CARD_H - 56 + BTN_H + 4,
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
        PaperUi.drawCoat(g, centerX, cardY + 16);
        g.drawCenteredString(this.font,
                Component.translatable("wp.ui.oath_header"),
                centerX, cardY + 28, PaperUi.INK_FADED);
        PaperUi.drawSpacedCentered(g, this.font,
                this.title.getString(),
                centerX, cardY + 44, PaperUi.INK);
        PaperUi.drawHeaderRule(g, cardX + 16, cardY + 60, CARD_W - 32);

        // Faction emblem disc (single, large) — colored per faction.
        int discColor = factionAccent(factionId);
        PaperUi.drawDisc(g, centerX, cardY + 90, 18, PaperUi.PAPER);
        PaperUi.drawCircleOutline(g, centerX, cardY + 90, 18, discColor);
        PaperUi.drawCircleOutline(g, centerX, cardY + 90, 14, discColor);
        // First letter of the faction id.
        String initial = factionInitial(factionId);
        int iw = this.font.width(initial);
        g.drawString(this.font, initial,
                centerX - iw / 2, cardY + 90 - this.font.lineHeight / 2 + 1, discColor, false);

        // Faction name
        PaperUi.drawSpacedCentered(g, this.font,
                factionDisplayName.getString(), centerX, cardY + 116, PaperUi.INK);

        // Confirmation question
        Component question = Component.translatable("wp.faction.choice_question", factionDisplayName);
        g.drawCenteredString(this.font, question, centerX, cardY + 130, PaperUi.INK_FADED);

        // Irreversibility warning
        g.drawCenteredString(this.font,
                Component.translatable("wp.ui.oath_irreversible"),
                centerX, cardY + 142, PaperUi.SEAL);

        // Footer
        PaperUi.drawDashedRule(g, cardX + 16, cardY + CARD_H - 18, CARD_W - 32, PaperUi.INK_MUTED);
        g.drawString(this.font, "WP · т. 3.0.0", cardX + 18, cardY + CARD_H - 12, PaperUi.INK_MUTED, false);
        Component foot = Component.translatable("wp.ui.signed_voluntarily");
        g.drawString(this.font, foot, cardX + CARD_W - 16 - this.font.width(foot), cardY + CARD_H - 12, PaperUi.INK_MUTED, false);

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
