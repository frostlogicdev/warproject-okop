package com.frostlogic.warproject.client.screen;



import com.frostlogic.warproject.client.widget.MilitaryButton;

import net.minecraft.client.Minecraft;

import net.minecraft.client.gui.GuiGraphics;

import net.minecraft.client.gui.components.Renderable;

import net.minecraft.client.gui.screens.Screen;

import net.minecraft.network.chat.Component;



public class AboutScreen extends Screen {

    private static final int ACCENT  = 0xFFCBD9A1;

    private static final int LABEL   = 0xFFE8E4C9;

    private static final int MUTED   = 0xFFB8B5A0;

    private static final int SHADOW  = 0xC0000000;



    private static final int PANEL_BG     = 0x99100D08;

    private static final int PANEL_BORDER = 0xCCCBD9A1;



    private final Screen parent;



    public AboutScreen(Screen parent) {

        super(Component.literal("О моде"));

        this.parent = parent;

    }



    @Override

    protected void init() {

        addRenderableWidget(new MilitaryButton(

                this.width / 2 - 100, this.height - 56,

                200, 36,

                Component.literal("Назад"),

                () -> Minecraft.getInstance().setScreen(parent)));

    }



    @Override

    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {

        // 1. Same animated, blurred bg as the title — keeps visual continuity.

        WarMenuBackground.render(g, this.width, this.height);



        // 2. Centred frosted-glass panel for the about content

        int panelW = 380;

        int panelH = 220;

        int px = (this.width - panelW) / 2;

        int py = (this.height - panelH) / 2 - 18;

        MilitaryButton.drawRoundedRectFilled(g, px, py, panelW, panelH, PANEL_BG);

        MilitaryButton.drawRoundedRectOutline(g, px, py, panelW, panelH, PANEL_BORDER);



        // 3. Crisp text on top of panel

        int cx = this.width / 2;



        String title = "О МОДЕ";

        int titleX = cx - font.width(title) / 2;

        drawTextWithShadow(g, title, titleX, py + 18, ACCENT);

        g.fill(titleX - 6, py + 30, titleX + font.width(title) + 6, py + 31, ACCENT);



        String name = "WAR PROJECT v3.0";

        drawTextWithShadow(g, name, cx - font.width(name) / 2, py + 44, LABEL);



        String[] lines = {

                "Полный военный мод-пак для Minecraft.",

                "31 уникальный блок в трёх категориях:",

                "",

                "  - Окоп (фортификации)",

                "  - Полевой лагерь",

                "  - Военная база",

                "",

                "Автор:  FrostLogicDev    Лицензия: MIT",

                "Minecraft 1.21.1  |  NeoForge 21.1+"

        };

        int y = py + 66;

        for (String line : lines) {

            int color = line.startsWith("  -") ? ACCENT

                    : line.startsWith("Автор") || line.startsWith("Minecraft") ? MUTED

                    : LABEL;

            drawTextWithShadow(g, line, cx - font.width(line) / 2, y, color);

            y += 12;

        }



        // 4. Buttons (manual render, bypassing vanilla bg)

        for (Renderable r : this.renderables) {

            r.render(g, mouseX, mouseY, partialTick);

        }

    }



    @Override

    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {

        // No-op (handled in render)

    }



    private void drawTextWithShadow(GuiGraphics g, String text, int x, int y, int color) {

        g.drawString(font, text, x + 1, y + 1, SHADOW, false);

        g.drawString(font, text, x, y, color, false);

    }

}

