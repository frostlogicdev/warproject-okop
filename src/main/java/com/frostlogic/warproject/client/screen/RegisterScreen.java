package com.frostlogic.warproject.client.screen;

import com.frostlogic.warproject.network.payload.c2s.RegisterRequestPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
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
 * <p>Opened by {@code ClientPayloadHandler.onAuthScreenState} when the server
 * sends an {@code AuthScreenStatePayload(REGISTER, ...)}. Submits both fields
 * as a C2S {@link RegisterRequestPayload}; the server is the source of truth
 * for password complexity rules — this screen only forwards the inputs.
 *
 * <p>The "show/hide" toggle flips between rendering the typed input as bullet
 * glyphs and rendering it raw, so the player can verify their typing without
 * leaking the password elsewhere.
 *
 * <p>The screen ignores ESC ({@link #shouldCloseOnEsc()} returns {@code false}),
 * does not pause the integrated server, and persists until the server clears
 * the auth flow (mode != REGISTER).
 *
 * <p>Requirements: 2.1 — Design: §9.1.
 */
public class RegisterScreen extends Screen {

    private static final int FIELD_WIDTH = 200;
    private static final int FIELD_HEIGHT = 20;
    private static final int FIELD_GAP = 6;
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int TOGGLE_WIDTH = 60;
    private static final int BACKGROUND_COLOR = 0xFF0A0A0A;
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int ERROR_COLOR = 0xFFFF6B6B;
    private static final char MASK_GLYPH = '*';

    private EditBox passwordField;
    private EditBox confirmField;
    private Button submitButton;
    private Button toggleButton;
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
        int centerY = this.height / 2;
        int fieldX = centerX - FIELD_WIDTH / 2;
        int passwordY = centerY - 20;
        int confirmY = passwordY + FIELD_HEIGHT + FIELD_GAP;

        passwordField = new EditBox(this.font, fieldX, passwordY, FIELD_WIDTH, FIELD_HEIGHT,
                Component.translatable("wp.auth.password"));
        passwordField.setMaxLength(RegisterRequestPayload.MAX_PASSWORD_LENGTH);
        passwordField.setHint(Component.translatable("wp.auth.password"));
        passwordField.setFormatter((input, cursorPosUnused) -> maskFormatter(input, visible));
        addRenderableWidget(passwordField);
        setInitialFocus(passwordField);

        confirmField = new EditBox(this.font, fieldX, confirmY, FIELD_WIDTH, FIELD_HEIGHT,
                Component.translatable("wp.auth.password_confirm"));
        confirmField.setMaxLength(RegisterRequestPayload.MAX_PASSWORD_LENGTH);
        confirmField.setHint(Component.translatable("wp.auth.password_confirm"));
        confirmField.setFormatter((input, cursorPosUnused) -> maskFormatter(input, visible));
        addRenderableWidget(confirmField);

        submitButton = Button.builder(
                        Component.translatable("wp.auth.register"),
                        b -> onSubmit())
                .bounds(centerX - BUTTON_WIDTH / 2, confirmY + FIELD_HEIGHT + 10, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        addRenderableWidget(submitButton);

        toggleButton = Button.builder(
                        Component.translatable(visible ? "wp.auth.hide" : "wp.auth.show"),
                        b -> onToggleVisibility())
                .bounds(centerX - BUTTON_WIDTH / 2, confirmY + FIELD_HEIGHT + 10 + BUTTON_HEIGHT + 4,
                        TOGGLE_WIDTH, BUTTON_HEIGHT)
                .build();
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
        // Server may bounce the form back with an error; clear inputs to avoid
        // leaving cleartext in the textbox while we wait for the response.
        passwordField.setValue("");
        confirmField.setValue("");
    }

    private void onToggleVisibility() {
        visible = !visible;
        if (toggleButton != null) {
            toggleButton.setMessage(Component.translatable(visible ? "wp.auth.hide" : "wp.auth.show"));
        }
        // Force the EditBox to re-run the formatter against the current value.
        if (passwordField != null) {
            passwordField.setValue(passwordField.getValue());
        }
        if (confirmField != null) {
            confirmField.setValue(confirmField.getValue());
        }
    }

    /**
     * Called externally when the server reports a register error and we want
     * to keep this screen open with a localized message.
     */
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
        // Enter submits the form when focus is on the confirm field.
        if (keyCode == 257 /* GLFW_KEY_ENTER */ || keyCode == 335 /* GLFW_KEY_KP_ENTER */) {
            onSubmit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        // Title above the inputs.
        g.drawCenteredString(this.font, this.title, centerX, centerY - 60, TITLE_COLOR);
        // Localized error line above the inputs, if any.
        if (errorMessage != null) {
            g.drawCenteredString(this.font, errorMessage, centerX, centerY - 40, ERROR_COLOR);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, BACKGROUND_COLOR);
    }

    /**
     * Returns either the input itself or a bullet-mask depending on
     * {@code visible}. Cursor positions handled by {@link EditBox} are not
     * affected because the formatter only changes glyph rendering.
     */
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
