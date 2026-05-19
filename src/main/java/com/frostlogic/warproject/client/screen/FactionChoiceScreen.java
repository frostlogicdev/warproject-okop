package com.frostlogic.warproject.client.screen;

import com.frostlogic.warproject.network.payload.c2s.FactionChoicePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client-side confirmation screen for faction choice.
 * <p>
 * Opened when the server sends {@code OpenFactionChoicePayload} after the player
 * right-clicks on a FactionNpcEntity. Displays a confirmation dialog with the
 * faction name and two buttons: confirm and cancel.
 * <p>
 * On confirmation, sends a C2S {@link FactionChoicePayload} back to the server.
 * <p>
 * Requirements: 6.3
 * Design: §3, §8.2
 */
public class FactionChoiceScreen extends Screen {

    private static final int BUTTON_WIDTH = 120;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 10;

    private final String factionId;
    private final Component factionDisplayName;

    /**
     * Creates the faction choice confirmation screen.
     *
     * @param factionId the serialized faction ID (e.g. "zarnavia" or "chernogryad")
     */
    public FactionChoiceScreen(String factionId) {
        super(Component.translatable("wp.faction.choice_title"));
        this.factionId = factionId;
        // Resolve the display name from the faction ID
        this.factionDisplayName = resolveFactionDisplayName(factionId);
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Confirm button
        this.addRenderableWidget(Button.builder(
                Component.translatable("wp.faction.confirm"),
                button -> onConfirm()
        ).bounds(
                centerX - BUTTON_WIDTH - BUTTON_SPACING / 2,
                centerY + 20,
                BUTTON_WIDTH,
                BUTTON_HEIGHT
        ).build());

        // Cancel button
        this.addRenderableWidget(Button.builder(
                Component.translatable("wp.faction.cancel"),
                button -> onCancel()
        ).bounds(
                centerX + BUTTON_SPACING / 2,
                centerY + 20,
                BUTTON_WIDTH,
                BUTTON_HEIGHT
        ).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Title
        guiGraphics.drawCenteredString(this.font, this.title, centerX, centerY - 40, 0xFFFFFF);

        // Confirmation question with faction name
        Component question = Component.translatable("wp.faction.choice_question", factionDisplayName);
        guiGraphics.drawCenteredString(this.font, question, centerX, centerY - 10, 0xCCCCCC);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void onConfirm() {
        // Send C2S packet to confirm faction choice
        PacketDistributor.sendToServer(new FactionChoicePayload(factionId));
        this.onClose();
    }

    private void onCancel() {
        this.onClose();
    }

    /**
     * Resolves the display name component for the given faction ID.
     * Uses translation keys matching the FactionId enum's displayNameKey().
     */
    private static Component resolveFactionDisplayName(String factionId) {
        return switch (factionId) {
            case "zarnavia" -> Component.translatable("wp.faction.zarnavia");
            case "chernogryad" -> Component.translatable("wp.faction.chernogryad");
            default -> Component.literal(factionId);
        };
    }
}
