package com.frostlogic.warproject.client.screen;

import com.frostlogic.warproject.client.widget.PaperButton;
import com.frostlogic.warproject.client.widget.PaperUi;
import com.frostlogic.warproject.network.payload.c2s.TransportChoicePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side screen showing the vehicles the player's rank can claim from a
 * transport NPC.
 * <p>
 * Layout:
 * <ul>
 *   <li>One passport-style card centered on the screen.</li>
 *   <li>Header: "Transport service — {faction}".</li>
 *   <li>Body: 2-column grid of rows; each row shows the vehicle item icon,
 *       the translated display name, and a small "issue" button.</li>
 *   <li>Footer: "Cancel" secondary button.</li>
 * </ul>
 * Picking an entry sends {@link TransportChoicePayload}. The server re-validates
 * rank + proximity and either gives the item (and the NPC arm-waves) or
 * head-shakes.
 */
public class TransportChoiceScreen extends Screen {

    private static final int CARD_W = 320;
    private static final int ROW_H = 28;
    private static final int ROW_GAP = 6;
    private static final int HEADER_AREA = 72;
    private static final int FOOTER_AREA = 56;

    private final String factionId;
    private final int npcEntityId;
    private final List<Entry> entries;

    public TransportChoiceScreen(String factionId, int npcEntityId, List<String> rawEntries) {
        super(Component.translatable("wp.transport.title"));
        this.factionId = factionId;
        this.npcEntityId = npcEntityId;
        this.entries = parseEntries(rawEntries);
    }

    private static List<Entry> parseEntries(List<String> raw) {
        List<Entry> out = new ArrayList<>(raw.size());
        for (String line : raw) {
            int sep = line.indexOf('|');
            if (sep <= 0 || sep == line.length() - 1) continue;
            String itemId = line.substring(0, sep);
            String displayKey = line.substring(sep + 1);
            ResourceLocation rl = ResourceLocation.tryParse(itemId);
            if (rl == null) continue;
            out.add(new Entry(itemId, displayKey, rl));
        }
        return out;
    }

    @Override
    protected void init() {
        super.init();

        int displayed = Math.max(1, entries.size());
        int cardH = HEADER_AREA + (displayed * (ROW_H + ROW_GAP)) + FOOTER_AREA;
        // Clamp so it always fits.
        cardH = Math.min(cardH, this.height - 20);

        int cardX = (this.width - CARD_W) / 2;
        int cardY = (this.height - cardH) / 2;

        int rowX = cardX + 16;
        int rowW = CARD_W - 32;
        int rowY = cardY + HEADER_AREA;

        for (Entry e : entries) {
            final Entry capture = e;
            PaperButton btn = new PaperButton(
                    rowX + rowW - 90,
                    rowY + 3,
                    84, ROW_H - 6,
                    Component.translatable("wp.transport.issue"),
                    PaperButton.Variant.PRIMARY,
                    () -> onIssue(capture)
            );
            this.addRenderableWidget(btn);
            rowY += ROW_H + ROW_GAP;
            if (rowY + ROW_H > cardY + cardH - FOOTER_AREA) break;
        }

        // Footer cancel
        this.addRenderableWidget(new PaperButton(
                cardX + (CARD_W - 100) / 2,
                cardY + cardH - 34,
                100, 22,
                Component.translatable("wp.transport.cancel"),
                PaperButton.Variant.SECONDARY,
                () -> {
                    if (this.minecraft != null) this.minecraft.setScreen(null);
                }
        ));
    }

    private void onIssue(Entry entry) {
        PacketDistributor.sendToServer(new TransportChoicePayload(npcEntityId, entry.itemId));
        if (this.minecraft != null) this.minecraft.setScreen(null);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        PaperUi.drawPaperBackground(g, this.width, this.height);

        int displayed = Math.max(1, entries.size());
        int cardH = HEADER_AREA + (displayed * (ROW_H + ROW_GAP)) + FOOTER_AREA;
        cardH = Math.min(cardH, this.height - 20);
        int cardX = (this.width - CARD_W) / 2;
        int cardY = (this.height - cardH) / 2;

        PaperUi.drawCardFrame(g, cardX, cardY, CARD_W, cardH);

        int titleY = cardY + 16;
        Component title = Component.translatable("wp.transport.title");
        g.drawCenteredString(this.font, title, cardX + CARD_W / 2, titleY, PaperUi.INK);

        String factionKey = "chernogryad".equals(factionId)
                ? "wp.faction.chernogryad"
                : "wp.faction.zarnavia";
        int factionColor = "chernogryad".equals(factionId) ? PaperUi.INK : PaperUi.APPROVED;
        Component sub = Component.translatable("wp.transport.subtitle",
                Component.translatable(factionKey));
        g.drawCenteredString(this.font, sub, cardX + CARD_W / 2, titleY + 14, factionColor);

        PaperUi.drawHeaderRule(g, cardX + 16, titleY + 32, CARD_W - 32);

        int rowX = cardX + 16;
        int rowW = CARD_W - 32;
        int rowY = cardY + HEADER_AREA;

        if (entries.isEmpty()) {
            g.drawCenteredString(this.font,
                    Component.translatable("wp.transport.empty"),
                    cardX + CARD_W / 2, rowY + 8, PaperUi.INK_MUTED);
            // Render widgets manually — calling super.render() would re-invoke
            // Screen#renderBackground which paints the 1.21+ world blur shader
            // on top of our paper background.
            for (Renderable r : this.renderables) {
                r.render(g, mouseX, mouseY, partialTick);
            }
            return;
        }

        for (Entry e : entries) {
            // Row background — alternating faint tint for readability.
            int rowBg = (rowY / (ROW_H + ROW_GAP)) % 2 == 0 ? PaperUi.PAPER_DARK : PaperUi.PAPER_LIGHT;
            g.fill(rowX, rowY, rowX + rowW, rowY + ROW_H, rowBg);

            // Item icon — falls back to the barrier visual if mod isn't installed.
            Item item = BuiltInRegistries.ITEM.get(e.resolved);
            ItemStack stack = new ItemStack(item == Items.AIR ? Items.BARRIER : item);
            g.renderItem(stack, rowX + 4, rowY + 6);

            // Display label
            Component label = Component.translatable(e.displayKey);
            g.drawString(this.font, label, rowX + 26, rowY + 10, PaperUi.INK, false);

            rowY += ROW_H + ROW_GAP;
            if (rowY + ROW_H > cardY + cardH - FOOTER_AREA) break;
        }

        // Render widgets manually — see comment above.
        for (Renderable r : this.renderables) {
            r.render(g, mouseX, mouseY, partialTick);
        }
    }

    /**
     * Disable the 1.21+ world blur + tile dim background. Our paper card
     * sits on top of {@link PaperUi#drawPaperBackground}, which already
     * covers the entire screen, so the vanilla background would only add
     * an unwanted blur over the paper texture.
     */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // intentionally no-op
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Entry(String itemId, String displayKey, ResourceLocation resolved) {
    }
}
