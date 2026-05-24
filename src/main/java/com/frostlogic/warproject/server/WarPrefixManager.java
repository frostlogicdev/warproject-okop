package com.frostlogic.warproject.server;

import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.attachment.PlayerState;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

import java.util.Optional;

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

        // ─── Attachment-first path ─────────────────────────────────────────
        // The new (DB-backed) pipeline writes faction / state / rank to
        // attachments and does NOT touch the legacy WarPlayerProfile, so
        // reading the profile alone would leave new-pipeline players with no
        // prefix at all. We prefer attachments when they're populated and fall
        // back to the legacy profile for accounts still on the old flow.
        Optional<FactionId> attFaction = player.getData(WpAttachmentTypes.FACTION.get());
        PlayerState attState = player.getData(WpAttachmentTypes.PLAYER_STATE.get());
        if (attFaction.isPresent()) {
            FactionId fid = attFaction.get();
            // ACCEPTED + a real rank → full faction/rank tag.
            if (attState == PlayerState.ACCEPTED) {
                String rawRank = player.getData(WpAttachmentTypes.RANK.get());
                Rank rank = (rawRank == null || rawRank.isBlank())
                        ? Rank.NONE
                        : Rank.fromInput(rawRank).orElse(Rank.NONE);
                Faction f = mapFactionIdToFaction(fid);
                if (rank == Rank.NONE) {
                    // Joined a faction but no rank yet → still a civilian on paper.
                    return civilianSpec(f);
                }
                String commanderMark = rank.isCommander() ? "КОМ " : "";
                String label = "[" + f.shortName() + " | " + commanderMark + rank.shortName() + "] ";
                String teamName = TEAM_PREFIX + f.id() + "_" + rank.id();
                return new TeamSpec(teamName,
                        Component.literal(label).withStyle(f.color()),
                        f.color());
            }
            // CANDIDATE (chose faction, not yet accepted) → [Гражданин] tag.
            // Same for any other non-ACCEPTED state where a faction is set.
            return civilianSpec(mapFactionIdToFaction(fid));
        }

        // ─── Legacy fallback ───────────────────────────────────────────────
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
            return civilianSpec(profile.getCandidateFaction());
        }

        return new TeamSpec(TEAM_PREFIX + "default",
                Component.empty(),
                ChatFormatting.WHITE);
    }

    /** "[Гражданин]" tag painted in the faction colour so allies can spot each other. */
    private static TeamSpec civilianSpec(Faction faction) {
        return new TeamSpec(TEAM_PREFIX + "candidate_" + faction.id(),
                Component.literal("[Гражданин] ").withStyle(faction.color()),
                faction.color());
    }

    private static Faction mapFactionIdToFaction(FactionId fid) {
        return switch (fid) {
            case ZARNAVIA -> Faction.ZARNAVIA;
            case CHERNOGRYAD -> Faction.CHERNOGRYAD;
        };
    }

    private record TeamSpec(String name, Component prefix, ChatFormatting color) {
    }
}
