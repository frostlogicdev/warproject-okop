package com.frostlogic.warproject.client.screen;



import com.frostlogic.warproject.client.widget.WarProgressBar;

import net.minecraft.client.gui.GuiGraphics;

import net.minecraft.client.gui.components.Renderable;

import net.minecraft.client.gui.screens.ReceivingLevelScreen;

import net.minecraft.network.chat.Component;



import java.util.function.BooleanSupplier;



/**

 * War-Project styled replacement for vanilla {@link ReceivingLevelScreen}

 * with reason {@code OTHER} (the post-chunk-gen "Downloading terrain" wait,

 * shown after {@link WarLevelLoadingScreen}).

 *

 * <p>Reason exists in the parent screen so transient state (createdAt timer,

 * levelReceived supplier) is reused unmodified — only the visual is replaced.

 * Nether-portal and end-portal transitions are intentionally <i>not</i>

 * intercepted; their portal-themed background is part of the gameplay feel.

 */

public class WarReceivingLevelScreen extends ReceivingLevelScreen {

    private static final Component DOWNLOADING = Component.translatable("multiplayer.downloadingTerrain");



    private static final int LABEL  = 0xFFE8E4C9;

    private static final int ACCENT = 0xFFCBD9A1;

    private static final int SHADOW = 0xC0000000;



    private static final int BAR_WIDTH  = 360;

    private static final int BAR_HEIGHT = 8;



    public WarReceivingLevelScreen(BooleanSupplier levelReceived, ReceivingLevelScreen.Reason reason) {

        super(levelReceived, reason);

    }



    @Override

    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {

        // Skip vanilla panorama / blur / dirt tiles — we paint our own backdrop in render().

    }



    @Override

    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {

        // 1. Animated alternative background

        WarMenuBackground.render(g, this.width, this.height, WarMenuBackground.ALTERNATIVE);



        int cx = this.width / 2;

        int cy = this.height / 2;



        // 2. Status text

        String text = DOWNLOADING.getString();

        int tx = cx - font.width(text) / 2;

        int ty = cy - 30;

        g.drawString(font, text, tx + 1, ty + 1, SHADOW, false);

        g.drawString(font, text, tx, ty, ACCENT, false);



        // 3. Indeterminate horizontal progress bar (no % available here)

        int bx = cx - BAR_WIDTH / 2;

        int by = cy - 5;

        WarProgressBar.renderIndeterminate(g, bx, by, BAR_WIDTH, BAR_HEIGHT);



        // 4. Renderables, if any (vanilla doesn't add any here in 1.21.1, but be safe)

        for (Renderable r : this.renderables) {

            r.render(g, mouseX, mouseY, partialTick);

        }

    }

}

