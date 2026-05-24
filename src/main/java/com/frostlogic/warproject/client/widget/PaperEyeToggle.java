package com.frostlogic.warproject.client.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Paper-passport-styled 16×16 password-visibility toggle.
 *
 * <p>Pixel-drawn ink eye on a transparent paper background. Hover swaps the ink
 * colour to crimson (matches PaperButton hover semantics). When the password
 * is hidden, a 45° crimson slash is drawn through the eye.
 *
 * <p>Mirrors the API of {@link EyeToggleButton} so it can be a drop-in
 * replacement on paper-styled screens.
 */
public class PaperEyeToggle extends AbstractButton {

    private boolean visible;
    private final Consumer<Boolean> onToggle;

    public PaperEyeToggle(int x, int y, boolean initiallyVisible, Consumer<Boolean> onToggle) {
        super(x, y, 16, 16, Component.literal(initiallyVisible ? "Скрыть пароль" : "Показать пароль"));
        this.visible = initiallyVisible;
        this.onToggle = onToggle;
    }

    public boolean isVisibleMode() {
        return visible;
    }

    @Override
    public void onPress() {
        visible = !visible;
        setMessage(Component.literal(visible ? "Скрыть пароль" : "Показать пароль"));
        if (onToggle != null) {
            onToggle.accept(visible);
        }
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hovered = isHoveredOrFocused();
        int ink = hovered ? PaperUi.SEAL : PaperUi.INK_FADED;

        // No background plate — let the underlying paper show through.
        drawEye(g, getX(), getY(), ink);
        if (!visible) {
            drawSlash(g, getX(), getY(), PaperUi.SEAL);
        }
    }

    /** Pixel-drawn ink-line eye, fits in a 16×16 box (16-px square). */
    private static void drawEye(GuiGraphics g, int x, int y, int color) {
        // top arc
        g.fill(x + 5, y + 4, x + 11, y + 5, color);
        g.fill(x + 3, y + 5, x + 5, y + 6, color);
        g.fill(x + 11, y + 5, x + 13, y + 6, color);
        g.fill(x + 2, y + 6, x + 3, y + 7, color);
        g.fill(x + 13, y + 6, x + 14, y + 7, color);
        // sides
        g.fill(x + 1, y + 7, x + 2, y + 9, color);
        g.fill(x + 14, y + 7, x + 15, y + 9, color);
        // bottom arc
        g.fill(x + 2, y + 9, x + 3, y + 10, color);
        g.fill(x + 13, y + 9, x + 14, y + 10, color);
        g.fill(x + 3, y + 10, x + 5, y + 11, color);
        g.fill(x + 11, y + 10, x + 13, y + 11, color);
        g.fill(x + 5, y + 11, x + 11, y + 12, color);
        // pupil (filled square)
        g.fill(x + 7, y + 7, x + 9, y + 9, color);
    }

    /** 45° crimson slash from bottom-left to top-right. */
    private static void drawSlash(GuiGraphics g, int x, int y, int color) {
        for (int i = 0; i < 14; i++) {
            int dx = x + 1 + i;
            int dy = y + 14 - i;
            g.fill(dx, dy, dx + 1, dy + 1, color);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
