package com.frostlogic.warproject.client.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * 16×16 password-visibility toggle.
 * <p>
 * Renders the eye icon procedurally (a flat-pixel oval lens + circular pupil)
 * via {@link GuiGraphics#fill(int, int, int, int, int)} so no PNG sprite is
 * needed in the resource pack — the widget Just Works regardless of the user's
 * texture pack. In the "hidden" state a diagonal slash is drawn over the eye.
 * <p>
 * State conventions: {@code visible == true} means the password is shown in
 * cleartext; {@code visible == false} means it is masked. The icon always
 * mirrors the current state, so a slashed eye means "currently hidden, click
 * to reveal".
 */
public class EyeToggleButton extends AbstractButton {
    private static final int ICON_NORMAL = 0xFFCBD9A1;
    private static final int ICON_HOVER  = 0xFFFFFFFF;
    private static final int BG_NORMAL   = 0x40000000;
    private static final int BG_HOVER    = 0x80000000;

    private boolean visible;
    private final Consumer<Boolean> onToggle;

    public EyeToggleButton(int x, int y, boolean initiallyVisible, Consumer<Boolean> onToggle) {
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
        int color = hovered ? ICON_HOVER : ICON_NORMAL;

        // background plate
        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), hovered ? BG_HOVER : BG_NORMAL);

        // eye outline + pupil
        drawEye(g, getX(), getY(), color);

        if (!visible) {
            drawSlash(g, getX(), getY(), color);
        }
    }

    private static void drawEye(GuiGraphics g, int x, int y, int color) {
        // top arc (left → right)
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
        // pupil (2×2 in centre)
        g.fill(x + 7, y + 7, x + 9, y + 9, color);
    }

    /** Diagonal slash from top-right to bottom-left across the eye. */
    private static void drawSlash(GuiGraphics g, int x, int y, int color) {
        for (int i = 0; i < 12; i++) {
            g.fill(x + 13 - i, y + 2 + i, x + 14 - i, y + 3 + i, color);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
