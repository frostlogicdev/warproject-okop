package com.frostlogic.warproject.client.screen;

import com.frostlogic.warproject.client.widget.EyeToggleButton;
import com.frostlogic.warproject.client.widget.MilitaryButton;
import com.frostlogic.warproject.network.SubmitPasswordPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Full-screen login / registration overlay shown to a player on connect until
 * they authenticate. The screen:
 * <ul>
 *   <li>Refuses to close on ESC ({@link #shouldCloseOnEsc()} returns false).</li>
 *   <li>Does not pause the singleplayer integrated server.</li>
 *   <li>Masks password inputs visually via an {@link EditBox} formatter.</li>
 *   <li>Uses {@link WarMenuBackground} for the same Ken-Burns background as the
 *       custom title screen for visual consistency.</li>
 * </ul>
 * The actual authentication is server-side ({@code WarLoginHandler}); this
 * widget only collects input and ships it via {@link SubmitPasswordPayload}.
 */
public class WarLoginScreen extends Screen {
    private static final int FIELD_WIDTH  = 240;
    private static final int FIELD_HEIGHT = 22;
    private static final int FIELD_GAP    = 10;
    private static final int BUTTON_W     = 240;
    private static final int BUTTON_H     = 32;

    private static final int LABEL_COLOR  = 0xFFE8E4C9;
    private static final int ACCENT_COLOR = 0xFFCBD9A1;
    private static final int ERROR_COLOR  = 0xFFFF6B6B;
    private static final int SHADOW_COLOR = 0xC0000000;
    private static final int PANEL_COLOR  = 0xB0000000;
    private static final int PANEL_BORDER = 0xFF6F8244;

    /** Replaces every character with this glyph when rendering masked input. */
    private static final char MASK_GLYPH = '\u2022'; // bullet •

    /** Gap between a password field and its eye toggle, in pixels. */
    private static final int EYE_GAP = 4;
    /** Eye toggle square side, mirrors {@link EyeToggleButton}. */
    private static final int EYE_SIZE = 16;

    private final boolean register;
    private EditBox passwordField;
    private EditBox confirmField; // only when register == true
    private MilitaryButton submitButton;
    private String errorMessage = "";
    private boolean passwordVisible = false;
    private boolean confirmVisible = false;

    public WarLoginScreen(boolean register) {
        super(Component.literal(register ? "Регистрация" : "Авторизация"));
        this.register = register;
    }

    public boolean isRegister() {
        return register;
    }

    @Override
    protected void init() {
        // Vertical layout, centred.
        int fieldCount = register ? 2 : 1;
        int totalH = fieldCount * FIELD_HEIGHT + (fieldCount - 1) * FIELD_GAP + 16 + BUTTON_H;
        int startY = (this.height - totalH) / 2 + 24;
        int centerX = this.width / 2;
        int fieldX = centerX - FIELD_WIDTH / 2;

        passwordField = new EditBox(this.font, fieldX, startY, FIELD_WIDTH, FIELD_HEIGHT, Component.literal("Пароль"));
        passwordField.setMaxLength(SubmitPasswordPayload.MAX_PASSWORD_LENGTH);
        passwordField.setHint(Component.literal(register ? "Придумай пароль" : "Пароль").withStyle(style -> style.withColor(0xFF8A8A6A)));
        passwordField.setFormatter(this::formatPassword);
        addRenderableWidget(passwordField);
        setInitialFocus(passwordField);

        int eyeY = startY + (FIELD_HEIGHT - EYE_SIZE) / 2;
        int eyeX = fieldX + FIELD_WIDTH + EYE_GAP;
        addRenderableWidget(new EyeToggleButton(eyeX, eyeY, passwordVisible, visible -> passwordVisible = visible));

        int submitY;
        if (register) {
            int confirmY = startY + FIELD_HEIGHT + FIELD_GAP;
            confirmField = new EditBox(this.font, fieldX, confirmY, FIELD_WIDTH, FIELD_HEIGHT, Component.literal("Подтвердите пароль"));
            confirmField.setMaxLength(SubmitPasswordPayload.MAX_PASSWORD_LENGTH);
            confirmField.setHint(Component.literal("Повтори пароль").withStyle(style -> style.withColor(0xFF8A8A6A)));
            confirmField.setFormatter(this::formatConfirm);
            addRenderableWidget(confirmField);

            int eye2Y = confirmY + (FIELD_HEIGHT - EYE_SIZE) / 2;
            addRenderableWidget(new EyeToggleButton(eyeX, eye2Y, confirmVisible, visible -> confirmVisible = visible));
            submitY = confirmY + FIELD_HEIGHT + 16;
        } else {
            submitY = startY + FIELD_HEIGHT + 16;
        }

        Component buttonLabel = Component.literal(register ? "Зарегистрироваться" : "Войти");
        submitButton = new MilitaryButton(centerX - BUTTON_W / 2, submitY, BUTTON_W, BUTTON_H, buttonLabel, this::onSubmit);
        addRenderableWidget(submitButton);
    }

    /** Formatter for the main password field. Bound to instance state so the
     *  visibility toggle just flips {@link #passwordVisible} and the next
     *  formatter invocation reflects the new state. */
    private FormattedCharSequence formatPassword(String input, int cursorPosUnused) {
        return passwordVisible ? rawFormatter(input) : maskFormatter(input);
    }

    private FormattedCharSequence formatConfirm(String input, int cursorPosUnused) {
        return confirmVisible ? rawFormatter(input) : maskFormatter(input);
    }

    private void onSubmit() {
        if (passwordField == null) {
            return;
        }
        String password = passwordField.getValue();
        if (password.isEmpty()) {
            errorMessage = "Введи пароль.";
            return;
        }
        if (register) {
            String confirm = confirmField == null ? "" : confirmField.getValue();
            if (!password.equals(confirm)) {
                errorMessage = "Пароли не совпадают.";
                if (confirmField != null) confirmField.setValue("");
                return;
            }
        }
        errorMessage = "";
        PacketDistributor.sendToServer(new SubmitPasswordPayload(register, password));
    }

    /** Called by the network handler when the server rejects the submission. */
    public void onLoginFailure(String message) {
        this.errorMessage = message == null ? "" : message;
        if (passwordField != null) passwordField.setValue("");
        if (confirmField != null) confirmField.setValue("");
        if (passwordField != null) setInitialFocus(passwordField);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Custom background (no vanilla blur/dirt).
        WarMenuBackground.render(g, this.width, this.height, mouseX, mouseY);

        // Header banner.
        String brand = "WAR PROJECT";
        int brandScale = 2;
        int brandTextWidth = font.width(brand) * brandScale;
        int brandX = (this.width - brandTextWidth) / 2;
        int brandY = this.height / 2 - 110;
        drawScaledText(g, brand, brandX, brandY, brandScale, ACCENT_COLOR);

        String subtitle = register ? "// РЕГИСТРАЦИЯ" : "// АВТОРИЗАЦИЯ";
        int subWidth = font.width(subtitle);
        drawTextWithShadow(g, subtitle, (this.width - subWidth) / 2, brandY + 14 * brandScale + 4, LABEL_COLOR);

        // Centre panel framing the form.
        int panelW = FIELD_WIDTH + 40;
        int fieldCount = register ? 2 : 1;
        int panelH = fieldCount * FIELD_HEIGHT + (fieldCount - 1) * FIELD_GAP + BUTTON_H + 56;
        int panelX = (this.width - panelW) / 2;
        int panelY = (this.height - panelH) / 2 + 12;
        MilitaryButton.drawRoundedRectFilled(g, panelX, panelY, panelW, panelH, PANEL_COLOR);
        MilitaryButton.drawRoundedRectOutline(g, panelX, panelY, panelW, panelH, PANEL_BORDER);

        // Field labels above the fields.
        if (passwordField != null) {
            String label = register ? "Придумай пароль" : "Введи пароль";
            drawTextWithShadow(g, label, passwordField.getX(), passwordField.getY() - 10, LABEL_COLOR);
        }
        if (register && confirmField != null) {
            drawTextWithShadow(g, "Подтверди пароль", confirmField.getX(), confirmField.getY() - 10, LABEL_COLOR);
        }

        // Render fields & button without invoking vanilla Screen#render
        // (which would tile menu_background dirt over our background).
        for (Renderable r : this.renderables) {
            r.render(g, mouseX, mouseY, partialTick);
        }

        // Error message below the button.
        if (!errorMessage.isEmpty() && submitButton != null) {
            int errY = submitButton.getY() + BUTTON_H + 8;
            int errWidth = font.width(errorMessage);
            drawTextWithShadow(g, errorMessage, (this.width - errWidth) / 2, errY, ERROR_COLOR);
        }

        // Hint at the bottom.
        String hint = register
                ? "Минимум " + com.frostlogic.warproject.server.WarLoginHandler.MIN_PASSWORD_LENGTH + " символов. Запомни пароль — без него на сервер не зайти."
                : "Введи пароль, который ты задал при первом входе.";
        int hintWidth = font.width(hint);
        drawTextWithShadow(g, hint, (this.width - hintWidth) / 2, this.height - 24, LABEL_COLOR);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No-op: render() draws our own background.
    }

    private void drawTextWithShadow(GuiGraphics g, String text, int x, int y, int color) {
        g.drawString(font, text, x + 1, y + 1, SHADOW_COLOR, false);
        g.drawString(font, text, x, y, color, false);
    }

    private void drawScaledText(GuiGraphics g, String text, int x, int y, float scale, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0f);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, text, 1, 1, SHADOW_COLOR, false);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    /** Replaces text with bullet glyphs for visual masking; cursor logic stays intact. */
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

    /** Pass-through formatter: renders the password in cleartext for the eye-open state. */
    private static FormattedCharSequence rawFormatter(String input) {
        if (input == null || input.isEmpty()) {
            return FormattedCharSequence.EMPTY;
        }
        return FormattedCharSequence.forward(input, Style.EMPTY);
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
