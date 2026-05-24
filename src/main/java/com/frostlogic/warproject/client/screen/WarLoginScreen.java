package com.frostlogic.warproject.client.screen;

import com.frostlogic.warproject.client.widget.PaperButton;
import com.frostlogic.warproject.client.widget.PaperEditBox;
import com.frostlogic.warproject.client.widget.PaperEyeToggle;
import com.frostlogic.warproject.client.widget.PaperUi;
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
 * Full-screen login / registration overlay shown to a player on connect.
 *
 * <p>Paper-passport visual direction (selected by the project owner 2026-05-24):
 * cream paper background, double-rule frame, crimson under-rule, rubber-stamp
 * accent (ВХОД for login, НОВЫЙ ПРИЗЫВ for register), ink-on-paper text,
 * underlined edit boxes (no vanilla borders), ink primary button.
 *
 * <p>Behavioural contract preserved from the previous implementation:
 * <ul>
 *   <li>Refuses to close on ESC ({@link #shouldCloseOnEsc()} returns false).</li>
 *   <li>Does not pause the singleplayer integrated server.</li>
 *   <li>Masks password inputs visually via an {@link EditBox} formatter.</li>
 *   <li>Constructor signature {@code WarLoginScreen(boolean register)} unchanged.</li>
 *   <li>{@link #onLoginFailure(String)} unchanged.</li>
 * </ul>
 */
public class WarLoginScreen extends Screen {

    /* Card geometry — width is the same in both modes, height grows for register
     * mode (two fields instead of one). */
    private static final int CARD_W           = 284;
    private static final int CARD_H_LOGIN     = 196;
    private static final int CARD_H_REGISTER  = 244;

    private static final int FIELD_WIDTH      = 210;
    private static final int FIELD_HEIGHT     = 16;
    private static final int FIELD_GAP        = 26;
    private static final int BUTTON_W         = 232;
    private static final int BUTTON_H         = 22;

    private static final int EYE_SIZE         = 16;
    private static final int EYE_GAP          = 6;

    /** Replaces every character with this glyph when rendering masked input. */
    private static final char MASK_GLYPH = '\u2022'; // bullet •

    private final boolean register;
    private PaperEditBox passwordField;
    private PaperEditBox confirmField; // only when register == true
    private PaperButton submitButton;
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
        int cardH = register ? CARD_H_REGISTER : CARD_H_LOGIN;
        int cardX = (this.width  - CARD_W) / 2;
        int cardY = (this.height - cardH) / 2;

        int centerX = this.width / 2;
        int fieldX  = centerX - FIELD_WIDTH / 2;
        int firstFieldY = cardY + (register ? 96 : 84);

        passwordField = new PaperEditBox(
                this.font, fieldX, firstFieldY, FIELD_WIDTH, FIELD_HEIGHT,
                Component.literal(register ? "Пароль" : "Пароль"));
        passwordField.setMaxLength(SubmitPasswordPayload.MAX_PASSWORD_LENGTH);
        passwordField.setHint(Component.literal(register ? "придумай пароль" : "введи пароль")
                .withStyle(s -> s.withColor(PaperUi.INK_MUTED)));
        passwordField.setFormatter(this::formatPassword);
        addRenderableWidget(passwordField);
        setInitialFocus(passwordField);

        int eyeY1 = firstFieldY + (FIELD_HEIGHT - EYE_SIZE) / 2 - 1;
        int eyeX  = fieldX + FIELD_WIDTH + EYE_GAP;
        addRenderableWidget(new PaperEyeToggle(eyeX, eyeY1, passwordVisible, v -> passwordVisible = v));

        int submitY;
        if (register) {
            int confirmY = firstFieldY + FIELD_GAP;
            confirmField = new PaperEditBox(
                    this.font, fieldX, confirmY, FIELD_WIDTH, FIELD_HEIGHT,
                    Component.literal("Подтвердите пароль"));
            confirmField.setMaxLength(SubmitPasswordPayload.MAX_PASSWORD_LENGTH);
            confirmField.setHint(Component.literal("повтори пароль")
                    .withStyle(s -> s.withColor(PaperUi.INK_MUTED)));
            confirmField.setFormatter(this::formatConfirm);
            addRenderableWidget(confirmField);

            int eyeY2 = confirmY + (FIELD_HEIGHT - EYE_SIZE) / 2 - 1;
            addRenderableWidget(new PaperEyeToggle(eyeX, eyeY2, confirmVisible, v -> confirmVisible = v));

            submitY = confirmY + FIELD_HEIGHT + 22;
        } else {
            submitY = firstFieldY + FIELD_HEIGHT + 22;
        }

        Component buttonLabel = Component.literal(register ? "Зачислить" : "Войти");
        submitButton = new PaperButton(
                centerX - BUTTON_W / 2, submitY, BUTTON_W, BUTTON_H,
                buttonLabel, PaperButton.Variant.PRIMARY, this::onSubmit);
        addRenderableWidget(submitButton);
    }

    /* ----------------------- formatters & callbacks ----------------------- */

    private FormattedCharSequence formatPassword(String input, int cursorPosUnused) {
        return passwordVisible ? rawFormatter(input) : maskFormatter(input);
    }

    private FormattedCharSequence formatConfirm(String input, int cursorPosUnused) {
        return confirmVisible ? rawFormatter(input) : maskFormatter(input);
    }

    private void onSubmit() {
        if (passwordField == null) return;
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

    /* -------------------------------- render ------------------------------ */

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cardH = register ? CARD_H_REGISTER : CARD_H_LOGIN;
        int cardX = (this.width  - CARD_W) / 2;
        int cardY = (this.height - cardH) / 2;

        // 1) Full-window paper background.
        PaperUi.drawPaperBackground(g, this.width, this.height);

        // 2) Card frame (double rule).
        PaperUi.drawCardFrame(g, cardX, cardY, CARD_W, cardH);

        // 3) Stamp accent — top-right for login, bottom-right for register.
        if (register) {
            PaperUi.drawSeal(g, cardX + CARD_W - 34, cardY + cardH - 30, 22,
                    Component.literal("НОВЫЙ"), Component.literal("ПРИЗЫВ"));
        } else {
            PaperUi.drawSeal(g, cardX + CARD_W - 30, cardY + 30, 20,
                    Component.literal("ВХОД"), null);
        }

        // 4) Brand line at top of card (small, letter-spaced, sepia).
        PaperUi.drawSpacedCentered(g, font, "ВОЕННЫЙ КОММИССАРИАТ",
                cardX + CARD_W / 2, cardY + 16, PaperUi.INK_MUTED);

        // 5) Crimson rule under the brand line.
        int ruleY = cardY + 30;
        g.fill(cardX + 22, ruleY, cardX + CARD_W - 22, ruleY + 1, PaperUi.SEAL);

        // 6) Mode title — large, ink, centred.
        String title = register ? "ПРИЗЫВНОЙ ЛИСТ" : "АВТОРИЗАЦИЯ";
        PaperUi.drawSpacedCentered(g, font, title,
                cardX + CARD_W / 2, cardY + 40, PaperUi.INK);

        // 7) Sub-line (form code).
        String sub = register ? "форма А-1 / новобранец" : "контрольный пункт / вход";
        int subW = font.width(sub);
        g.drawString(font, sub, cardX + CARD_W / 2 - subW / 2, cardY + 54,
                PaperUi.INK_FADED, false);

        // 8) Dashed sepia separator above the form.
        PaperUi.drawDashedRule(g, cardX + 22, cardY + 66, CARD_W - 44, PaperUi.INK_MUTED);

        // 9) Field labels — sepia, above each field.
        if (passwordField != null) {
            String label = register ? "Пароль:" : "Введи пароль:";
            g.drawString(font, label, passwordField.getX(), passwordField.getY() - 11,
                    PaperUi.INK_FADED, false);
        }
        if (register && confirmField != null) {
            g.drawString(font, "Повтори:", confirmField.getX(), confirmField.getY() - 11,
                    PaperUi.INK_FADED, false);
        }

        // 10) Live match indicator (register mode).
        if (register && passwordField != null && confirmField != null) {
            String p = passwordField.getValue();
            String c = confirmField.getValue();
            if (!p.isEmpty() && !c.isEmpty()) {
                if (p.equals(c)) {
                    g.drawString(font, "✓ пароли совпадают",
                            confirmField.getX(), confirmField.getY() + FIELD_HEIGHT + 2,
                            PaperUi.APPROVED, false);
                } else {
                    g.drawString(font, "✗ не совпадают",
                            confirmField.getX(), confirmField.getY() + FIELD_HEIGHT + 2,
                            PaperUi.SEAL, false);
                }
            }
        }

        // 11) Render fields & button without invoking vanilla Screen#render.
        for (Renderable r : this.renderables) {
            r.render(g, mouseX, mouseY, partialTick);
        }

        // 12) Error message under the button (crimson).
        if (!errorMessage.isEmpty() && submitButton != null) {
            int errY = submitButton.getY() + BUTTON_H + 4;
            int errWidth = font.width(errorMessage);
            g.drawString(font, errorMessage,
                    cardX + CARD_W / 2 - errWidth / 2, errY, PaperUi.SEAL, false);
        }

        // 13) Footer hint at the bottom of the card.
        String hint = register
                ? "Минимум " + com.frostlogic.warproject.server.WarLoginHandler.MIN_PASSWORD_LENGTH
                  + " символов. Запиши и не теряй."
                : "Введи пароль, который ты задал при первом входе.";
        int hintW = font.width(hint);
        g.drawString(font, hint, cardX + CARD_W / 2 - hintW / 2,
                cardY + cardH - 14, PaperUi.INK_MUTED, false);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No-op: render() draws the paper background itself.
    }

    /* ------------------------ password mask helpers ----------------------- */

    /** Replaces text with bullet glyphs for visual masking; cursor logic intact. */
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

    /** Pass-through formatter: renders the password in cleartext for eye-open. */
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
