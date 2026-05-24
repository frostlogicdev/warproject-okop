package com.frostlogic.warproject.client.screen;

import com.frostlogic.warproject.client.widget.PaperButton;
import com.frostlogic.warproject.client.widget.PaperEditBox;
import com.frostlogic.warproject.client.widget.PaperUi;
import com.frostlogic.warproject.network.payload.c2s.LoginRequestPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
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
    private static final int CARD_H = 204;
    private static final int FIELD_WIDTH = 200;
    private static final int FIELD_HEIGHT = 18;
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 22;

    // ── Vertical anchor table (all relative to cardY) ──────────────────
    // Keep these in sync with the constants used in init() and render() so
    // refactors don't reintroduce element overlap (see image bug report
    // 24-V-2026: hint text painted under the submit button).
    private static final int Y_COAT          = 18;
    private static final int Y_REPUBLIC      = 30;
    private static final int Y_TITLE         = 44;
    private static final int Y_HEADER_RULE   = 62;
    private static final int Y_SUBTITLE      = 70;
    private static final int Y_FIELD_LABEL   = 96;
    private static final int Y_FIELD         = 110;
    private static final int Y_HINT          = 136;
    private static final int Y_SUBMIT        = 152;
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

        // Password field — centred horizontally.
        int fieldX = centerX - FIELD_WIDTH / 2;
        passwordField = new PaperEditBox(this.font, fieldX, cardY + Y_FIELD, FIELD_WIDTH, FIELD_HEIGHT,
                Component.translatable("wp.auth.password"));
        passwordField.setMaxLength(LoginRequestPayload.MAX_PASSWORD_LENGTH);
        passwordField.setHint(Component.translatable("wp.auth.password"));
        passwordField.setFormatter((input, cursorPosUnused) -> maskFormatter(input));
        addRenderableWidget(passwordField);
        setInitialFocus(passwordField);

        // Primary action — confirm login.
        submitButton = new PaperButton(
                centerX - BUTTON_WIDTH / 2,
                cardY + Y_SUBMIT,
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
        PaperUi.drawCoat(g, centerX, cardY + Y_COAT);
        g.drawCenteredString(this.font,
                Component.translatable("wp.ui.republic_header"),
                centerX, cardY + Y_REPUBLIC, PaperUi.INK_FADED);
        PaperUi.drawSpacedCentered(g, this.font,
                this.title.getString(), centerX, cardY + Y_TITLE, PaperUi.INK);
        // Crimson rule under the header.
        PaperUi.drawHeaderRule(g, cardX + 16, cardY + Y_HEADER_RULE, CARD_W - 32);
        g.drawCenteredString(this.font,
                Component.translatable("wp.ui.login_subtitle"),
                centerX, cardY + Y_SUBTITLE, PaperUi.INK_FADED);

        // Field label — sits 14 px above the field, left-aligned to the field column.
        int fieldLabelX = cardX + (CARD_W - FIELD_WIDTH) / 2;
        g.drawString(this.font,
                Component.translatable("wp.auth.password").getString().toUpperCase(),
                fieldLabelX, cardY + Y_FIELD_LABEL, PaperUi.INK_FADED, false);

        // Status line — sits between field and submit button.
        // Error in red if login failed; otherwise the subtle confidentiality hint.
        if (errorMessage != null) {
            g.drawCenteredString(this.font, errorMessage, centerX, cardY + Y_HINT, PaperUi.ERROR);
        } else {
            g.drawCenteredString(this.font,
                    Component.translatable("wp.ui.login_hint"),
                    centerX, cardY + Y_HINT, PaperUi.INK_FADED);
        }

        // Footer rule + footer labels (ink-muted)
        PaperUi.drawDashedRule(g, cardX + 16, cardY + CARD_H - 18, CARD_W - 32, PaperUi.INK_MUTED);
        g.drawString(this.font, "WP · т. 3.0.0", cardX + 18, cardY + CARD_H - 12, PaperUi.INK_MUTED, false);
        Component sec = Component.translatable("wp.ui.secure_link");
        g.drawString(this.font, sec, cardX + CARD_W - 16 - this.font.width(sec), cardY + CARD_H - 12, PaperUi.INK_MUTED, false);

        // Note: the round "ВХОД" rubber stamp was removed — at 22 px radius it
        // visually collided with the centred title across the top of the card.
        // The crimson header rule + spaced title already carry the "official
        // document" tone the seal was meant to reinforce.

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
