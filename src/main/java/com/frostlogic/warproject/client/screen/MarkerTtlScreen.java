package com.frostlogic.warproject.client.screen;

import com.frostlogic.warproject.network.payload.c2s.SetFactionMarkerPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Squad-style marker-TTL chooser shown when the player presses {@code Z}.
 * <p>
 * Offers four preset durations — 1, 3, 5, 10 minutes — and a {@code Cancel}
 * button. On selection the screen sends a {@link SetFactionMarkerPayload} with
 * the chosen TTL and the captured world position, then closes itself.
 * <p>
 * Server enforces the actual permission check (faction marker for COMMANDER+,
 * subdivision marker for SOLDIER members of a subdivision) and clamps the TTL
 * to {@link SetFactionMarkerPayload#MAX_TTL_SECONDS}.
 */
public class MarkerTtlScreen extends Screen {

    private static final int[] TTL_PRESETS_SECONDS = { 60, 180, 300, 600 };
    private static final String[] TTL_LABELS = {
            "wp.marker.ttl.1min",
            "wp.marker.ttl.3min",
            "wp.marker.ttl.5min",
            "wp.marker.ttl.10min"
    };

    private final BlockPos pos;

    public MarkerTtlScreen(BlockPos pos) {
        super(Component.translatable("wp.marker.title"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        super.init();
        int btnWidth = 160;
        int btnHeight = 20;
        int gap = 4;
        int totalH = TTL_PRESETS_SECONDS.length * btnHeight + (TTL_PRESETS_SECONDS.length - 1) * gap + 30 + btnHeight;
        int yStart = (this.height - totalH) / 2;
        int xCenter = (this.width - btnWidth) / 2;

        for (int i = 0; i < TTL_PRESETS_SECONDS.length; i++) {
            final int ttl = TTL_PRESETS_SECONDS[i];
            this.addRenderableWidget(
                    Button.builder(Component.translatable(TTL_LABELS[i]), b -> sendAndClose(ttl))
                            .bounds(xCenter, yStart + i * (btnHeight + gap), btnWidth, btnHeight)
                            .build()
            );
        }

        this.addRenderableWidget(
                Button.builder(Component.translatable("gui.cancel"), b -> this.onClose())
                        .bounds(xCenter, yStart + TTL_PRESETS_SECONDS.length * (btnHeight + gap) + 20, btnWidth, btnHeight)
                        .build()
        );
    }

    private void sendAndClose(int ttlSeconds) {
        PacketDistributor.sendToServer(new SetFactionMarkerPayload(pos.getX(), pos.getY(), pos.getZ(), ttlSeconds));
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        Component title = this.title;
        int titleWidth = this.font.width(title);
        g.drawString(this.font, title, (this.width - titleWidth) / 2,
                (this.height - 200) / 2 + 4, 0xFFFFFFFF);

        Component sub = Component.translatable("wp.marker.subtitle", pos.getX(), pos.getY(), pos.getZ());
        int subW = this.font.width(sub);
        g.drawString(this.font, sub, (this.width - subW) / 2,
                (this.height - 200) / 2 + 18, 0xFFAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
