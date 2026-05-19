package com.frostlogic.warproject.client;

import com.frostlogic.warproject.WarProject;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;

@EventBusSubscriber(modid = WarProject.MOD_ID, value = Dist.CLIENT)
public final class WarClientNameRenderer {
    private static final String TEAM_PREFIX = "wp_";
    private static final String ADMIN_TEAM = "wp_admin";
    private static final String COLLAB_TEAM = "wp_collab";
    private static final String DEFAULT_TEAM = "wp_default";

    private WarClientNameRenderer() {
    }

    @SubscribeEvent
    public static void onRenderName(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer target)) {
            return;
        }
        LocalPlayer viewer = Minecraft.getInstance().player;
        if (viewer == null || target == viewer) {
            return;
        }

        TeamInfo targetInfo = readTeam(target.getTeam());
        if (targetInfo.isNeutral()) {
            return;
        }

        TeamInfo viewerInfo = readTeam(viewer.getTeam());

        ChatFormatting color;
        if (targetInfo.isAdmin) {
            color = ChatFormatting.GOLD;
        } else if (targetInfo.isCollab) {
            color = ChatFormatting.DARK_RED;
        } else if (viewerInfo.factionId != null && viewerInfo.factionId.equals(targetInfo.factionId)) {
            color = ChatFormatting.BLUE;
        } else if (viewerInfo.factionId != null && targetInfo.factionId != null) {
            color = ChatFormatting.RED;
        } else {
            return;
        }

        Component content = event.getContent();
        if (content == null) {
            return;
        }
        MutableComponent recolored = content.copy().withStyle(Style.EMPTY.withColor(color));
        event.setContent(recolored);
    }

    private static TeamInfo readTeam(PlayerTeam team) {
        if (team == null) {
            return TeamInfo.NEUTRAL;
        }
        String name = team.getName();
        if (ADMIN_TEAM.equals(name)) {
            return TeamInfo.admin();
        }
        if (COLLAB_TEAM.equals(name)) {
            return TeamInfo.collab();
        }
        if (DEFAULT_TEAM.equals(name)) {
            return TeamInfo.NEUTRAL;
        }
        if (name.startsWith(TEAM_PREFIX)) {
            String rest = name.substring(TEAM_PREFIX.length());
            int underscore = rest.indexOf('_');
            String faction = underscore > 0 ? rest.substring(0, underscore) : rest;
            return TeamInfo.faction(faction);
        }
        return TeamInfo.NEUTRAL;
    }

    private record TeamInfo(boolean isAdmin, boolean isCollab, String factionId) {
        static final TeamInfo NEUTRAL = new TeamInfo(false, false, null);

        static TeamInfo admin() {
            return new TeamInfo(true, false, null);
        }

        static TeamInfo collab() {
            return new TeamInfo(false, true, null);
        }

        static TeamInfo faction(String factionId) {
            return new TeamInfo(false, false, factionId);
        }

        boolean isNeutral() {
            return !isAdmin && !isCollab && factionId == null;
        }
    }
}
