package com.frostlogic.warproject.client;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.network.payload.s2c.PlayerPublicViewPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;

import java.util.UUID;

/**
 * Client-side renderer that prepends the {@code [КОЛЛАБОРАНТ]} tag (red) to a
 * player's name tag and TAB display name when the cached
 * {@link PlayerPublicViewPayload} reports {@code collaborator = true}.
 * <p>
 * Inputs come from {@link PlayerPublicViewCache}, populated by
 * {@link com.frostlogic.warproject.network.ClientPayloadHandler#onPlayerPublicView}
 * from S2C {@code PlayerPublicViewPayload} packets.
 * <p>
 * Two integration points:
 * <ul>
 *   <li>{@link RenderNameTagEvent} — prepends the tag to the floating overhead
 *       name on every render frame.</li>
 *   <li>{@link ClientTickEvent.Post} — every tick, refreshes the TAB-list
 *       display name on the matching {@link PlayerInfo} via
 *       {@link PlayerInfo#setTabListDisplayName(Component)} so the same tag
 *       appears next to the nick in the player list.</li>
 * </ul>
 * <p>
 * The cache is cleared on disconnect ({@link ClientPlayerNetworkEvent.LoggingOut})
 * to avoid leaking state into the next session.
 * <p>
 * Requirements: 11.2
 * Design: §9.4
 */
@EventBusSubscriber(modid = WarProject.MOD_ID, value = Dist.CLIENT)
public final class WarClientCollabRenderer {

    private static final String COLLAB_TAG_KEY = "wp.collab.tag";

    /** Default tab refresh interval (ticks) — 20 ticks ≈ 1 s, plenty for a tab marker. */
    private static final int TAB_REFRESH_INTERVAL_TICKS = 20;

    private static int tickCounter;

    private WarClientCollabRenderer() {
        // static event subscriber — no instantiation
    }

    // ── Overhead name tag ────────────────────────────────────────────────────

    /**
     * Prepends the collaborator tag to the rendered name when the cached public
     * view reports {@code collaborator = true}. Runs after
     * {@link WarClientNameRenderer} (team-based coloring) and operates on the
     * possibly-recolored content, preserving the team color while still flagging
     * the collaborator status with a red prefix.
     */
    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player)) {
            return;
        }
        UUID uuid = player.getUUID();
        if (!PlayerPublicViewCache.isCollaborator(uuid)) {
            return;
        }
        Component current = event.getContent();
        if (current == null) {
            return;
        }
        event.setContent(prependCollabTag(current));
    }

    // ── TAB list ────────────────────────────────────────────────────────────

    /**
     * Periodically refreshes the TAB-list display name for cached players so
     * the collaborator tag follows changes in the cache. Runs at most once per
     * {@value #TAB_REFRESH_INTERVAL_TICKS} ticks to keep the cost negligible.
     */
    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        tickCounter++;
        if (tickCounter < TAB_REFRESH_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            return;
        }

        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            UUID uuid = info.getProfile().getId();
            PlayerPublicViewPayload view = PlayerPublicViewCache.get(uuid);
            if (view == null) {
                continue;
            }
            Component current = info.getTabListDisplayName();
            if (view.collaborator()) {
                if (current == null) {
                    current = Component.literal(info.getProfile().getName());
                }
                if (!alreadyPrefixed(current)) {
                    info.setTabListDisplayName(prependCollabTag(current));
                }
            } else if (current != null && alreadyPrefixed(current)) {
                // Drop the prefix when the flag is cleared. Restoring the
                // server-provided display name is not always possible from
                // the client; using the raw nick is the safe fallback.
                info.setTabListDisplayName(Component.literal(info.getProfile().getName()));
            }
        }
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        PlayerPublicViewCache.clear();
        tickCounter = 0;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static MutableComponent prependCollabTag(Component nameContent) {
        MutableComponent tag = Component.translatable(COLLAB_TAG_KEY).withStyle(ChatFormatting.RED);
        return Component.empty().append(tag).append(Component.literal(" ")).append(nameContent);
    }

    /**
     * Detects whether the given component already begins with the localized
     * {@code wp.collab.tag} translation key. Avoids stacking the prefix on
     * repeated tab refreshes.
     */
    private static boolean alreadyPrefixed(Component component) {
        if (component.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
                && COLLAB_TAG_KEY.equals(tc.getKey())) {
            return true;
        }
        for (Component sibling : component.getSiblings()) {
            if (sibling.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
                    && COLLAB_TAG_KEY.equals(tc.getKey())) {
                return true;
            }
        }
        return false;
    }
}
