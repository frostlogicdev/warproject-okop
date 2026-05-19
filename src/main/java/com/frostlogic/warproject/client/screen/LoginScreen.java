package com.frostlogic.warproject.client.screen;

import com.frostlogic.warproject.network.payload.c2s.LoginRequestPayload;
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
 * Single password field + login button screen for returning players.
 *
 * <p>Opened by {@code ClientPayloadHandler.onAuthScreenState} when the server
 * sends an {@code AuthScreenStatePayload(LOGIN, ...)}. Submits the typed
 * password as a C2S {@link LoginRequestPayload}; never echoes the password
 * back to the user as cleartext (formatted as bullet glyphs in the
 * {@link EditBox}).
 *
 * <p>The screen ignores ESC ({@link #shouldCloseOnEsc()} returns {@code false}),
 * does not pause the integrated server, and accepts no keyboard escape from
 * the auth flow until the server explicitly clears it (mode != LOGIN).
 *
 * <p>Requirements: 3.1 — Design: §9.1.
 */
public class LoginScreen extends Screen {

    private static final int FIELD_WIDTH = 200;
    private static final int FIELD_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BACKGROUND_COLOR = 0xFF0A0A0A;
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int ERROR_COLOR = 0xFFFF6B6B;
    private static final char MASK_GLYPH = '*';

    private EditBox passwordField;
    private Button submitButton;
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
        int centerY = this.height / 2;
        int fieldX = centerX - FIELD_WIDTH / 2;

        passwordField = new EditBox(this.font, fieldX, centerY - 10, FIELD_WIDTH, FIELD_HEIGHT,
                Component.translatable("wp.auth.password"));
        passwordField.setMaxLength(LoginRequestPayload.MAX_PASSWORD_LENGTH);
        passwordField.setHint(Component.translatable("wp.auth.password"));
        passwordField.setFormatter((input, cursorPosUnused) -> maskFormatter(input));
        addRenderableWidget(passwordField);
        setInitialFocus(passwordField);

        submitButton = Button.builder(
                        Component.translatable("wp.auth.login"),
                        b -> onSubmit())
                .bounds(centerX - BUTTON_WIDTH / 2, centerY + 20, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
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
        // The screen stays open until the server tells us to close it
        // (AuthScreenStatePayload with a different mode, or session join).
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
        // Enter submits the form.
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
        // Title above the input.
        g.drawCenteredString(this.font, this.title, centerX, centerY - 50, TITLE_COLOR);
        // Localized error line above the input, if any.
        if (errorMessage != null) {
            g.drawCenteredString(this.font, errorMessage, centerX, centerY - 30, ERROR_COLOR);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Solid dark background — blocks all underlying HUD/world rendering
        // (per Design §9.1: super.renderBackground → solid #0A0A0A).
        g.fill(0, 0, this.width, this.height, BACKGROUND_COLOR);
    }

    /** Replaces every character with a fixed glyph, regardless of input. */
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
