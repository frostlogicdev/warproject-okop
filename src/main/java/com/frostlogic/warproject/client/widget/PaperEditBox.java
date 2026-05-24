package com.frostlogic.warproject.client.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Paper-passport themed {@link EditBox}: borderless, drawn instead with a
 * 1 px sepia under-rule that turns crimson when focused (the line under the
 * field of a paper form).
 *
 * <p>The text colour is forced to ink-dark and the hint to muted sepia so the
 * widget reads as ink-on-paper. All other behaviour (cursor, selection,
 * formatter, IME) is inherited from vanilla — we only re-skin.
 */
public class PaperEditBox extends EditBox {

    public PaperEditBox(Font font, int x, int y, int width, int height, Component msg) {
        super(font, x, y, width, height, msg);
        // Disable the vanilla dark-grey box border — we draw our own under-rule.
        setBordered(false);
        // Inkstone-black text on cream paper.
        setTextColor(PaperUi.INK);
        setTextColorUneditable(PaperUi.INK_FADED);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        // Faint paper-darker stripe behind the input so the eye sees a "field" line.
        g.fill(x - 2, y, x + w + 2, y + h, PaperUi.PAPER_DARK);
        // Under-rule: 1 px sepia, swap to 2 px crimson while focused.
        if (isFocused()) {
            g.fill(x - 2, y + h,     x + w + 2, y + h + 1, PaperUi.SEAL);
            g.fill(x - 2, y + h + 1, x + w + 2, y + h + 2, PaperUi.SEAL);
        } else {
            g.fill(x - 2, y + h, x + w + 2, y + h + 1, PaperUi.INK);
        }
        // Now let vanilla render the text, cursor, and selection.
        super.renderWidget(g, mouseX, mouseY, partialTick);
    }
}
