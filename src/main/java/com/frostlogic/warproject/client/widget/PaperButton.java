package com.frostlogic.warproject.client.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Paper-passport themed button — dark-ink body with sepia double-ruled border
 * and an uppercase sepia label. Hover swaps the body to crimson so the action
 * "stamps" the page.
 *
 * <p>Three variants:
 * <ul>
 *   <li>{@link Variant#PRIMARY} — solid ink body, sepia text, double border. Used for the dominant action.</li>
 *   <li>{@link Variant#SECONDARY} — transparent body, single ink border, ink text. Used for ancillary actions.</li>
 *   <li>{@link Variant#DANGER} — crimson body, paper text. Reserved for irreversible actions (e.g. faction confirm).</li>
 * </ul>
 */
public class PaperButton extends AbstractButton {

    public enum Variant { PRIMARY, SECONDARY, DANGER }

    private final Runnable onPress;
    private final Variant variant;

    public PaperButton(int x, int y, int w, int h, Component msg, Variant variant, Runnable onPress) {
        super(x, y, w, h, msg);
        this.variant = variant;
        this.onPress = onPress;
    }

    public PaperButton(int x, int y, int w, int h, Component msg, Runnable onPress) {
        this(x, y, w, h, msg, Variant.PRIMARY, onPress);
    }

    @Override
    public void onPress() {
        if (onPress != null) onPress.run();
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hovered = isHoveredOrFocused();
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();

        int body;
        int border;
        int text;
        switch (variant) {
            case PRIMARY -> {
                body   = hovered ? PaperUi.SEAL : PaperUi.INK;
                border = hovered ? PaperUi.SEAL_HOVER : PaperUi.INK;
                text   = PaperUi.PAPER;
            }
            case DANGER -> {
                body   = hovered ? PaperUi.SEAL_HOVER : PaperUi.SEAL;
                border = PaperUi.INK;
                text   = PaperUi.PAPER;
            }
            default /* SECONDARY */ -> {
                body   = hovered ? 0x16000000 : 0x00000000;
                border = hovered ? PaperUi.INK : PaperUi.INK_FADED;
                text   = hovered ? PaperUi.INK : PaperUi.INK_FADED;
            }
        }

        // Body
        if ((body >>> 24) != 0) {
            g.fill(x, y, x + w, y + h, body);
        }

        // Border: PRIMARY/DANGER get a double-rule for that engraved-plate look
        if (variant == Variant.PRIMARY || variant == Variant.DANGER) {
            PaperUi.drawRect(g, x, y, w, h, border);
            PaperUi.drawRect(g, x + 2, y + 2, w - 4, h - 4, border);
        } else {
            PaperUi.drawRect(g, x, y, w, h, border);
        }

        // Label — uppercase, 1 px letter-spacing.
        Font font = Minecraft.getInstance().font;
        String label = getMessage().getString();
        int textY = y + (h - font.lineHeight) / 2;
        PaperUi.drawSpacedCentered(g, font, label, x + w / 2, textY, text);

        // Hover marker — small triangle on the right margin (PRIMARY only).
        if (hovered && variant == Variant.PRIMARY) {
            int dx = x + w - 8;
            int cy = y + h / 2;
            g.fill(dx,     cy - 2, dx + 1, cy - 1, PaperUi.PAPER);
            g.fill(dx,     cy + 1, dx + 1, cy + 2, PaperUi.PAPER);
            g.fill(dx + 1, cy - 1, dx + 2, cy + 1, PaperUi.PAPER);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
