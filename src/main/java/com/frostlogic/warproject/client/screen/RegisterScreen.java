package com.frostlogic.warproject.client.screen;

import com.frostlogic.warproject.client.widget.PaperButton;
import com.frostlogic.warproject.client.widget.PaperEditBox;
import com.frostlogic.warproject.client.widget.PaperUi;
import com.frostlogic.warproject.network.payload.c2s.RegisterRequestPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

import org.jetbrains.annotations.Nullable;

/**
 * Two password fields (password + confirm) + register button + show/hide
 * toggle. Used for the player's first connection.
 *
 * <p>Visual direction: "paper passport" — recruitment intake form A-1.
 *
 * <p>The screen ignores ESC ({@link #shouldCloseOnEsc()} returns {@code false}),
 * does not pause the integrated server, and persists until the server clears
 * the auth flow (mode != REGISTER).
 *
 * <p>Requirements: 2.1 — Design: §9.1.
 */
public class RegisterScreen extends Screen {

    private static final int CARD_W = 260;
    private static final int CARD_H = 240;
    private static final int FIELD_WIDTH = 200;
    private static final int FIELD_HEIGHT = 18;
    private static final int FIELD_GAP = 28;
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 22;
    private static final char MASK_GLYPH = '*';

    private PaperEditBox passwordField;
    private PaperEditBox confirmField;
    private PaperButton submitButton;
    private PaperButton toggleButton;
    private boolean visible;
    private @Nullable Component errorMessage;

    public RegisterScreen(@Nullable Component errorMessage) {
        super(Component.translatable("wp.auth.title"));
        this.errorMessage = errorMessage;
    }

    public RegisterScreen() {
        this(null);
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int cardY = (this.height - CARD_H) / 2;
        int fieldX = centerX - FIELD_WIDTH / 2;
        int passwordY = cardY + 100;
        int confirmY = passwordY + FIELD_GAP;

        passwordField = new PaperEditBox(this.font, fieldX, passwordY, FIELD_WIDTH, FIELD_HEIGHT,
                Component.translatable("wp.auth.password"));
        passwordField.setMaxLength(RegisterRequestPayload.MAX_PASSWORD_LENGTH);
        passwordField.setHint(Component.translatable("wp.auth.password"));
        passwordField.setFormatter((input, cursorPosUnused) -> maskFormatter(input, visible));
        addRenderableWidget(passwordField);
        setInitialFocus(passwordField);

        confirmField = new PaperEditBox(this.font, fieldX, confirmY, FIELD_WIDTH, FIELD_HEIGHT,
                Component.translatable("wp.auth.password_confirm"));
        confirmField.setMaxLength(RegisterRequestPayload.MAX_PASSWORD_LENGTH);
        confirmField.setHint(Component.translatable("wp.auth.password_confirm"));
        confirmField.setFormatter((input, cursorPosUnused) -> maskFormatter(input, visible));
        addRenderableWidget(confirmField);

        submitButton = new PaperButton(
                centerX - BUTTON_WIDTH / 2,
                confirmY + FIELD_HEIGHT + 18,
                BUTTON_WIDTH,
                BUTTON_HEIGHT,
                Component.translatable("wp.auth.register"),
                PaperButton.Variant.PRIMARY,
                this::onSubmit);
        addRenderableWidget(submitButton);

        toggleButton = new PaperButton(
                centerX - BUTTON_WIDTH / 2,
                confirmY + FIELD_HEIGHT + 18 + BUTTON_HEIGHT + 4,
                BUTTON_WIDTH,
                BUTTON_HEIGHT - 4,
                Component.translatable(visible ? "wp.auth.hide" : "wp.auth.show"),
                PaperButton.Variant.SECONDARY,
                this::onToggleVisibility);
        addRenderableWidget(toggleButton);
    }

    private void onSubmit() {
        if (passwordField == null || confirmField == null) {
            return;
        }
        String password = passwordField.getValue();
        String confirm = confirmField.getValue();
        if (password.isEmpty()) {
            return;
        }
        PacketDistributor.sendToServer(new RegisterRequestPayload(password, confirm));
        passwordField.setValue("");
        confirmField.setValue("");
    }

    private void onToggleVisibility() {
        visible = !visible;
        if (toggleButton != null) {
            toggleButton.setMessage(Component.translatable(visible ? "wp.auth.hide" : "wp.auth.show"));
        }
        if (passwordField != null) {
            passwordField.setValue(passwordField.getValue());
        }
        if (confirmField != null) {
            confirmField.setValue(confirmField.getValue());
        }
    }

    public void setError(@Nullable Component message) {
        this.errorMessage = message;
        if (passwordField != null) {
            passwordField.setValue("");
            setInitialFocus(passwordField);
        }
        if (confirmField != null) {
            confirmField.setValue("");
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            onSubmit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
                Component.translatable("wp.ui.recruit_header"),
                centerX, cardY + 28, PaperUi.INK_FADED);
        PaperUi.drawSpacedCentered(g, this.font,
                Component.translatable("wp.auth.register").getString(),
                centerX, cardY + 44, PaperUi.INK);
        PaperUi.drawHeaderRule(g, cardX + 16, cardY + 60, CARD_W - 32);
        g.drawCenteredString(this.font,
                Component.translatable("wp.ui.register_subtitle"),
                centerX, cardY + 66, PaperUi.INK_FADED);

        // Labels for the two fields
        g.drawString(this.font,
                Component.translatable("wp.auth.password").getString().toUpperCase(),
                cardX + 30, cardY + 90, PaperUi.INK_FADED, false);
        g.drawString(this.font,
                Component.translatable("wp.auth.password_confirm").getString().toUpperCase(),
                cardX + 30, cardY + 90 + FIELD_GAP, PaperUi.INK_FADED, false);

        // Confirm-state line — green tick if both filled and equal, red on error, faded hint otherwise.
        if (errorMessage != null) {
            g.drawCenteredString(this.font, errorMessage, centerX, cardY + 150, PaperUi.ERROR);
        } else {
            String pwd     = passwordField != null ? passwordField.getValue() : "";
            String confirm = confirmField  != null ? confirmField.getValue()  : "";
            if (!pwd.isEmpty() && !confirm.isEmpty() && pwd.equals(confirm)) {
                g.drawCenteredString(this.font,
                        Component.translatable("wp.ui.match_ok"),
                        centerX, cardY + 150, PaperUi.APPROVED);
            } else {
                g.drawCenteredString(this.font,
                        Component.translatable("wp.ui.password_hint"),
                        centerX, cardY + 150, PaperUi.INK_FADED);
            }
        }

        // Rubber stamp — bottom-right (recruitment seal).
        PaperUi.drawSeal(g, cardX + CARD_W - 36, cardY + CARD_H - 60, 22,
                Component.translatable("wp.ui.stamp_new1"),
                Component.translatable("wp.ui.stamp_new2"));

        // Footer
        PaperUi.drawDashedRule(g, cardX + 16, cardY + CARD_H - 18, CARD_W - 32, PaperUi.INK_MUTED);
        g.drawString(this.font, "WP · т. 3.0.0", cardX + 18, cardY + CARD_H - 12, PaperUi.INK_MUTED, false);
        Component foot = Component.translatable("wp.ui.id_issued");
        g.drawString(this.font, foot, cardX + CARD_W - 16 - this.font.width(foot), cardY + CARD_H - 12, PaperUi.INK_MUTED, false);

        for (Renderable r : this.renderables) {
            r.render(g, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No-op: render() does everything in one pass.
    }

    private static FormattedCharSequence maskFormatter(String input, boolean visible) {
        if (input == null || input.isEmpty()) {
            return FormattedCharSequence.EMPTY;
        }
        if (visible) {
            return FormattedCharSequence.forward(input, Style.EMPTY);
        }
        StringBuilder masked = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            masked.append(MASK_GLYPH);
        }
        return FormattedCharSequence.forward(masked.toString(), Style.EMPTY);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
