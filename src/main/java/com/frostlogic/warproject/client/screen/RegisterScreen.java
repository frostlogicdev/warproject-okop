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
    private static final int CARD_H = 252;
    private static final int FIELD_WIDTH = 200;
    private static final int FIELD_HEIGHT = 18;
    private static final int FIELD_GAP = 28;
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 22;

    // ── Vertical anchor table (relative to cardY) ──────────────────────
    // All Y positions are derived from these constants so the form lays out
    // without overlapping (see bug report 24-V-2026: rubber stamp painted
    // over both buttons).
    private static final int Y_COAT          = 18;
    private static final int Y_RECRUIT       = 30;
    private static final int Y_TITLE         = 44;
    private static final int Y_HEADER_RULE   = 62;
    private static final int Y_SUBTITLE      = 70;
    private static final int Y_PW_LABEL      = 90;
    private static final int Y_PW_FIELD      = 100;
    private static final int Y_CONFIRM_LABEL = Y_PW_LABEL + FIELD_GAP;
    private static final int Y_CONFIRM_FIELD = Y_PW_FIELD + FIELD_GAP;
    private static final int Y_HINT          = Y_CONFIRM_FIELD + FIELD_HEIGHT + 8;     // ≈ 154
    private static final int Y_SUBMIT        = Y_HINT + 14;                            // ≈ 168
    private static final int Y_TOGGLE        = Y_SUBMIT + BUTTON_HEIGHT + 6;           // ≈ 196
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

        passwordField = new PaperEditBox(this.font, fieldX, cardY + Y_PW_FIELD, FIELD_WIDTH, FIELD_HEIGHT,
                Component.translatable("wp.auth.password"));
        passwordField.setMaxLength(RegisterRequestPayload.MAX_PASSWORD_LENGTH);
        passwordField.setHint(Component.translatable("wp.auth.password"));
        passwordField.setFormatter((input, cursorPosUnused) -> maskFormatter(input, visible));
        addRenderableWidget(passwordField);
        setInitialFocus(passwordField);

        confirmField = new PaperEditBox(this.font, fieldX, cardY + Y_CONFIRM_FIELD, FIELD_WIDTH, FIELD_HEIGHT,
                Component.translatable("wp.auth.password_confirm"));
        confirmField.setMaxLength(RegisterRequestPayload.MAX_PASSWORD_LENGTH);
        confirmField.setHint(Component.translatable("wp.auth.password_confirm"));
        confirmField.setFormatter((input, cursorPosUnused) -> maskFormatter(input, visible));
        addRenderableWidget(confirmField);

        submitButton = new PaperButton(
                centerX - BUTTON_WIDTH / 2,
                cardY + Y_SUBMIT,
                BUTTON_WIDTH,
                BUTTON_HEIGHT,
                Component.translatable("wp.auth.register"),
                PaperButton.Variant.PRIMARY,
                this::onSubmit);
        addRenderableWidget(submitButton);

        toggleButton = new PaperButton(
                centerX - BUTTON_WIDTH / 2,
                cardY + Y_TOGGLE,
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
        PaperUi.drawCoat(g, centerX, cardY + Y_COAT);
        g.drawCenteredString(this.font,
                Component.translatable("wp.ui.recruit_header"),
                centerX, cardY + Y_RECRUIT, PaperUi.INK_FADED);
        PaperUi.drawSpacedCentered(g, this.font,
                Component.translatable("wp.auth.register").getString(),
                centerX, cardY + Y_TITLE, PaperUi.INK);
        PaperUi.drawHeaderRule(g, cardX + 16, cardY + Y_HEADER_RULE, CARD_W - 32);
        g.drawCenteredString(this.font,
                Component.translatable("wp.ui.register_subtitle"),
                centerX, cardY + Y_SUBTITLE, PaperUi.INK_FADED);

        // Field labels — aligned to the field column (not a hardcoded inset)
        // so they line up cleanly with the input boxes.
        int labelX = cardX + (CARD_W - FIELD_WIDTH) / 2;
        g.drawString(this.font,
                Component.translatable("wp.auth.password").getString().toUpperCase(),
                labelX, cardY + Y_PW_LABEL, PaperUi.INK_FADED, false);
        g.drawString(this.font,
                Component.translatable("wp.auth.password_confirm").getString().toUpperCase(),
                labelX, cardY + Y_CONFIRM_LABEL, PaperUi.INK_FADED, false);

        // Status line (between the confirm field and the submit button).
        // Green tick when both fields filled and equal, red on server error,
        // faded hint otherwise.
        if (errorMessage != null) {
            g.drawCenteredString(this.font, errorMessage, centerX, cardY + Y_HINT, PaperUi.ERROR);
        } else {
            String pwd     = passwordField != null ? passwordField.getValue() : "";
            String confirm = confirmField  != null ? confirmField.getValue()  : "";
            if (!pwd.isEmpty() && !confirm.isEmpty() && pwd.equals(confirm)) {
                g.drawCenteredString(this.font,
                        Component.translatable("wp.ui.match_ok"),
                        centerX, cardY + Y_HINT, PaperUi.APPROVED);
            } else {
                g.drawCenteredString(this.font,
                        Component.translatable("wp.ui.password_hint"),
                        centerX, cardY + Y_HINT, PaperUi.INK_FADED);
            }
        }

        // Note: the round "НОВЫЙ ПРИЗЫВ" rubber stamp used to be painted at
        // cardY+CARD_H-60 — that ran over both buttons. Removed; the dashed
        // footer + spaced-letter title carry the document tone instead.

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
