package com.frostlogic.warproject.server;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

public final class WarPrefixManager {
    private static final String TEAM_PREFIX = "wp_";

    private WarPrefixManager() {
    }

    public static void refreshAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            refresh(player);
        }
    }

    public static void refresh(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return;
        }

        Scoreboard scoreboard = player.getServer().getScoreboard();
        WarPlayerProfile profile = WarPlayerDataStore.get().getOrCreate(player);
        TeamSpec spec = computeTeamSpec(player, profile);
        PlayerTeam team = scoreboard.getPlayerTeam(spec.name);
        boolean isNew = team == null;
        if (isNew) {
            team = scoreboard.addPlayerTeam(spec.name);
        }

        if (isNew || !sameStyle(team, spec)) {
            team.setPlayerPrefix(spec.prefix);
            team.setColor(spec.color);
            team.setCollisionRule(Team.CollisionRule.NEVER);
        }

        Team current = scoreboard.getPlayersTeam(player.getScoreboardName());
        if (current != team) {
            scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
        }
        player.refreshTabListName();
    }

    private static boolean sameStyle(PlayerTeam team, TeamSpec spec) {
        return team.getPlayerPrefix().getString().equals(spec.prefix.getString())
                && team.getColor() == spec.color;
    }

    private static TeamSpec computeTeamSpec(ServerPlayer player, WarPlayerProfile profile) {
        boolean admin = player.hasPermissions(2);

        if (admin) {
            return new TeamSpec(TEAM_PREFIX + "admin",
                    Component.literal("[АДМИН] ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                    ChatFormatting.RED);
        }

        // Captive players get their own team so the [ПЛЕН] prefix overrides
        // faction/rank chrome — being a prisoner is the dominant status to
        // signal to the lobby. Re-shown over the previously-cached faction
        // team because refresh() re-evaluates this on every call.
        if (profile.isCaptive()) {
            return new TeamSpec(TEAM_PREFIX + "captive",
                    Component.literal("[ПЛЕН] ").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC),
                    ChatFormatting.GRAY);
        }

        if (profile.isCollaborator()) {
            return new TeamSpec(TEAM_PREFIX + "collab",
                    Component.literal("[КОЛЛАБ] ").withStyle(ChatFormatting.DARK_RED),
                    ChatFormatting.DARK_RED);
        }

        Faction faction = profile.getFaction();
        if (faction.isPlayable()) {
            Rank rank = profile.getRank();
            String commanderMark = rank.isCommander() ? "КОМ " : "";
            String label = "[" + faction.shortName() + " | " + commanderMark + rank.shortName() + "] ";
            String teamName = TEAM_PREFIX + faction.id() + "_" + (rank == Rank.NONE ? "none" : rank.id());
            return new TeamSpec(teamName,
                    Component.literal(label).withStyle(faction.color()),
                    faction.color());
        }

        if (profile.getCandidateFaction().isPlayable()) {
            return new TeamSpec(TEAM_PREFIX + "candidate_" + profile.getCandidateFaction().id(),
                    Component.literal("[Гражданин] ").withStyle(ChatFormatting.GRAY),
                    ChatFormatting.GRAY);
        }

        return new TeamSpec(TEAM_PREFIX + "default",
                Component.empty(),
                ChatFormatting.WHITE);
    }

    private record TeamSpec(String name, Component prefix, ChatFormatting color) {
    }
}
