package com.frostlogic.warproject.client.screen;

import com.frostlogic.warproject.client.widget.MilitaryButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;

/**
 * Custom main menu replacing vanilla TitleScreen.
 * <p>
 * IMPORTANT: We do NOT call {@code super.render()}. Vanilla {@code Screen#render}
 * calls {@code renderBackground} which on 1.21+ runs a global blur shader + tiles
 * the {@code menu_background} dirt texture over everything. That's why text and
 * the bg image were "blurred" — they were getting overdrawn by vanilla. We render
 * the renderables (buttons) ourselves to keep full control of layer order.
 */
public class WarProjectTitleScreen extends Screen {
    // Larger panel + denser layout — the new MilitaryButton has an index
    // column ("01 │") which needs a touch more horizontal room to breathe.
    private static final int BUTTON_WIDTH = 248;
    private static final int BUTTON_HEIGHT = 38;
    private static final int BUTTON_SPACING = 12;
    private static final int LEFT_MARGIN = 48;

    private static final int LABEL_COLOR  = 0xFFE8E4C9;
    private static final int ACCENT_COLOR = 0xFFCBD9A1;
    private static final int SHADOW_COLOR = 0xC0000000;

    public WarProjectTitleScreen() {
        super(Component.translatable("narrator.screen.title"));
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        int total = 4 * BUTTON_HEIGHT + 3 * BUTTON_SPACING;
        int startY = (this.height - total) / 2 + 8;

        addRenderableWidget(new MilitaryButton(LEFT_MARGIN, startY,
                BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.literal("Играть"),
                () -> mc.setScreen(new SelectWorldScreen(this))).setIndex(0));

        addRenderableWidget(new MilitaryButton(LEFT_MARGIN, startY + (BUTTON_HEIGHT + BUTTON_SPACING),
                BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.literal("Сервер"),
                () -> mc.setScreen(new JoinMultiplayerScreen(this))).setIndex(1));

        addRenderableWidget(new MilitaryButton(LEFT_MARGIN, startY + 2 * (BUTTON_HEIGHT + BUTTON_SPACING),
                BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.literal("Настройки"),
                () -> mc.setScreen(new OptionsScreen(this, mc.options))).setIndex(2));

        addRenderableWidget(new MilitaryButton(LEFT_MARGIN, startY + 3 * (BUTTON_HEIGHT + BUTTON_SPACING),
                BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.literal("О моде"),
                () -> mc.setScreen(new AboutScreen(this))).setIndex(3));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 1. Animated, pre-blurred cover background (NO vanilla blur shader / dirt tiles)
        WarMenuBackground.render(g, this.width, this.height, mouseX, mouseY);

        // 2. Crisp UI labels on top
        String brand = "WAR PROJECT";
        int brandWidth = font.width(brand);
        int brandX = this.width - brandWidth - 24;
        int brandY = 22;
        drawTextWithShadow(g, brand, brandX, brandY, ACCENT_COLOR);
        g.fill(brandX, brandY + 12, brandX + brandWidth, brandY + 13, ACCENT_COLOR);

        String tagline = "Tactical Fortifications Mod";
        drawTextWithShadow(g, tagline, this.width - font.width(tagline) - 24, brandY + 18, LABEL_COLOR);

        String menuLabel = "// ГЛАВНОЕ МЕНЮ";
        int menuLabelY = (this.height - (4 * BUTTON_HEIGHT + 3 * BUTTON_SPACING)) / 2 - 16;
        drawTextWithShadow(g, menuLabel, LEFT_MARGIN, menuLabelY, ACCENT_COLOR);

        String version = "v3.0  |  31 блок";
        drawTextWithShadow(g, version, 12, this.height - 14, LABEL_COLOR);

        // 3. Buttons (manual loop, intentionally bypassing super.render to avoid vanilla bg)
        for (Renderable r : this.renderables) {
            r.render(g, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No-op: we render our background inside render() above. This override prevents
        // vanilla from running the menu blur shader and the dirt-tile menu_background.
    }

    private void drawTextWithShadow(GuiGraphics g, String text, int x, int y, int color) {
        g.drawString(font, text, x + 1, y + 1, SHADOW_COLOR, false);
        g.drawString(font, text, x, y, color, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
