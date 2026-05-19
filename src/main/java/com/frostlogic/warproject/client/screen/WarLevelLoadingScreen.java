package com.frostlogic.warproject.client.screen;



import com.frostlogic.warproject.client.widget.WarProgressBar;

import net.minecraft.client.gui.GuiGraphics;

import net.minecraft.client.gui.components.Renderable;

import net.minecraft.client.gui.screens.LevelLoadingScreen;

import net.minecraft.network.chat.Component;

import net.minecraft.server.level.progress.StoringChunkProgressListener;

import net.minecraft.util.Mth;



/**

 * War-Project styled replacement for vanilla {@link LevelLoadingScreen}.

 * Hides the colorful chunk-status grid, shows our themed background, a

 * status line, a percentage and a thin horizontal progress bar.

 *

 * The vanilla {@link StoringChunkProgressListener} is reused so progress

 * accuracy is identical — only the visualisation differs.

 */

public class WarLevelLoadingScreen extends LevelLoadingScreen {

    private static final int LABEL  = 0xFFE8E4C9;

    private static final int ACCENT = 0xFFCBD9A1;

    private static final int SHADOW = 0xC0000000;



    private static final int BAR_WIDTH  = 360;

    private static final int BAR_HEIGHT = 10;



    private final StoringChunkProgressListener listener;



    public WarLevelLoadingScreen(StoringChunkProgressListener listener) {

        super(listener);

        this.listener = listener;

    }



    @Override

    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {

        // Skip vanilla blur shader + dirt tiles; we paint our own backdrop.

    }



    @Override

    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {

        // 1. Animated alternative background (same as Play / Server / Settings)

        WarMenuBackground.render(g, this.width, this.height, WarMenuBackground.ALTERNATIVE);



        int cx = this.width / 2;

        int cy = this.height / 2;



        // 2. Status line above the progress bar

        Component status = Component.translatable("menu.loadingLevel");

        int statusX = cx - font.width(status) / 2;

        int statusY = cy - 30;

        drawTextWithShadow(g, status.getString(), statusX, statusY, ACCENT);



        // 3. Percentage label

        int progress = Mth.clamp(listener.getProgress(), 0, 100);

        String pct = progress + " %";

        int pctX = cx - font.width(pct) / 2;

        drawTextWithShadow(g, pct, pctX, cy - 14, LABEL);



        // 4. Horizontal progress bar

        int barX = cx - BAR_WIDTH / 2;

        int barY = cy + 4;

        WarProgressBar.renderDeterminate(g, barX, barY, BAR_WIDTH, BAR_HEIGHT, progress / 100f);



        // 5. Any registered renderables (e.g. NeoForge mod-loader bars) — for safety,

        //    we still loop them, but vanilla doesn't add any here.

        for (Renderable r : this.renderables) {

            r.render(g, mouseX, mouseY, partialTick);

        }

    }



    private void drawTextWithShadow(GuiGraphics g, String text, int x, int y, int color) {

        g.drawString(font, text, x + 1, y + 1, SHADOW, false);

        g.drawString(font, text, x, y, color, false);

    }

}

