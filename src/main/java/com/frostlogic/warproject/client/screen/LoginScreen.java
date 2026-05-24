package com.frostlogic.warproject.client.screen;

import com.frostlogic.warproject.client.widget.PaperButton;
import com.frostlogic.warproject.client.widget.PaperEditBox;
import com.frostlogic.warproject.client.widget.PaperUi;
import com.frostlogic.warproject.network.payload.c2s.LoginRequestPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Renderable;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

import org.jetbrains.annotations.Nullable;

/**
 * Single password field + login button screen for returning players.
 *
 * <p>Opened by {@code ClientPayloadHandler.onAuthScreenState} when the server
 * sends an {@code AuthScreenStatePayload(LOGIN, ...)}. Submits the typed
 * password as a C2S {@link LoginRequestPayload}; never echoes the password
 * back to the user as cleartext (formatted as bullet glyphs in the
 * {@link EditBox}).
 *
 * <p>Visual direction: "paper passport" — cream paper card centred on the page,
 * crimson seal stamp, ink-on-paper inputs, double-ruled primary action. See
 * {@link PaperUi}.
 *
 * <p>The screen ignores ESC ({@link #shouldCloseOnEsc()} returns {@code false}),
 * does not pause the integrated server, and accepts no keyboard escape from
 * the auth flow until the server explicitly clears it (mode != LOGIN).
 *
 * <p>Requirements: 3.1 — Design: §9.1.
 */
public class LoginScreen extends Screen {

    private static final int CARD_W = 260;
    private static final int CARD_H = 196;
    private static final int FIELD_WIDTH = 200;
    private static final int FIELD_HEIGHT = 18;
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 22;
    private static final char MASK_GLYPH = '*';

    private PaperEditBox passwordField;
    private PaperButton submitButton;
    private @Nullable Component errorMessage;

    public LoginScreen(@Nullable Component errorMessage) {
        super(Component.translatable("wp.auth.title"));
        this.errorMessage = errorMessage;
    }

    public LoginScreen() {
        this(null);
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int cardY = (this.height - CARD_H) / 2;

        // Password field — centred horizontally, ~ ⅔ down the card.
        int fieldX = centerX - FIELD_WIDTH / 2;
        int fieldY = cardY + 110;
        passwordField = new PaperEditBox(this.font, fieldX, fieldY, FIELD_WIDTH, FIELD_HEIGHT,
                Component.translatable("wp.auth.password"));
        passwordField.setMaxLength(LoginRequestPayload.MAX_PASSWORD_LENGTH);
        passwordField.setHint(Component.translatable("wp.auth.password"));
        passwordField.setFormatter((input, cursorPosUnused) -> maskFormatter(input));
        addRenderableWidget(passwordField);
        setInitialFocus(passwordField);

        // Primary action — confirm login.
        submitButton = new PaperButton(
                centerX - BUTTON_WIDTH / 2,
                fieldY + FIELD_HEIGHT + 16,
                BUTTON_WIDTH,
                BUTTON_HEIGHT,
                Component.translatable("wp.auth.login"),
                PaperButton.Variant.PRIMARY,
                this::onSubmit);
        addRenderableWidget(submitButton);
    }

    private void onSubmit() {
        if (passwordField == null) {
            return;
        }
        String password = passwordField.getValue();
        if (password.isEmpty()) {
            return;
        }
        PacketDistributor.sendToServer(new LoginRequestPayload(password));
        passwordField.setValue("");
    }

    /**
     * Called externally when the server reports a login error and we want to
     * keep this screen open with a localized message.
     */
    public void setError(@Nullable Component message) {
        this.errorMessage = message;
        if (passwordField != null) {
            passwordField.setValue("");
            setInitialFocus(passwordField);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 /* GLFW_KEY_ENTER */ || keyCode == 335 /* GLFW_KEY_KP_ENTER */) {
            onSubmit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Fully custom render order: paper bg → card → decorations → widgets on top.
        // We intentionally do NOT call super.render(), because vanilla Screen.render()
        // would re-invoke renderBackground() and paint over our decorations.
        int centerX = this.width / 2;
        int cardX = (this.width - CARD_W) / 2;
        int cardY = (this.height - CARD_H) / 2;

        // 1) Paper + card frame.
        PaperUi.drawPaperBackground(g, this.width, this.height);
        PaperUi.drawCardFrame(g, cardX, cardY, CARD_W, CARD_H);

        // 2) Header / decorations (drawn before widgets so widgets sit on top).
        // Header eagle + republic line + title (handled outside super.render so super doesn't
        // paint widgets over the title).
        PaperUi.drawCoat(g, centerX, cardY + 16);
        g.drawCenteredString(this.font,
                Component.translatable("wp.ui.republic_header"),
                centerX, cardY + 28, PaperUi.INK_FADED);
        PaperUi.drawSpacedCentered(g, this.font,
                this.title.getString(), centerX, cardY + 44, PaperUi.INK);
        // Crimson rule under the header.
        PaperUi.drawHeaderRule(g, cardX + 16, cardY + 60, CARD_W - 32);
        g.drawCenteredString(this.font,
                Component.translatable("wp.ui.login_subtitle"),
                centerX, cardY + 66, PaperUi.INK_FADED);

        // Field label
        g.drawString(this.font,
                Component.translatable("wp.auth.password").getString().toUpperCase(),
                cardX + 30, cardY + 100, PaperUi.INK_FADED, false);

        // Error message — drawn under the field if present.
        if (errorMessage != null) {
            g.drawCenteredString(this.font, errorMessage, centerX, cardY + 152, PaperUi.ERROR);
        } else {
            g.drawCenteredString(this.font,
                    Component.translatable("wp.ui.password_hint"),
                    centerX, cardY + 152, PaperUi.INK_FADED);
        }

        // Footer rule + footer labels (ink-faded)
        PaperUi.drawDashedRule(g, cardX + 16, cardY + CARD_H - 18, CARD_W - 32, PaperUi.INK_MUTED);
        g.drawString(this.font, "WP · т. 3.0.0", cardX + 18, cardY + CARD_H - 12, PaperUi.INK_MUTED, false);
        Component sec = Component.translatable("wp.ui.secure_link");
        g.drawString(this.font, sec, cardX + CARD_W - 16 - this.font.width(sec), cardY + CARD_H - 12, PaperUi.INK_MUTED, false);

        // Rubber stamp — top-right of the card, rendered behind the form via z-index of draw order.
        // It's drawn here (in render, before widgets) so widgets sit on top.
        PaperUi.drawSeal(g, cardX + CARD_W - 32, cardY + 32, 22,
                Component.translatable("wp.ui.stamp_login"),
                Component.literal("24·V·MMXXVI"));

        // 3) Widgets — manually iterate so they always paint on top of our decorations.
        for (Renderable r : this.renderables) {
            r.render(g, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No-op: render() above paints the entire surface in one pass. We override
        // here so the engine cannot draw the vanilla dirt/menu background under us.
    }

    private static FormattedCharSequence maskFormatter(String input) {
        if (input == null || input.isEmpty()) {
            return FormattedCharSequence.EMPTY;
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
