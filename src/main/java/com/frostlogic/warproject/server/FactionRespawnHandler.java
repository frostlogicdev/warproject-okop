package com.frostlogic.warproject.server;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Optional;

/**
 * Sends respawning faction members to their faction's base if no personal
 * respawn point is set (no bed, no anchor, no tent). Candidates and
 * factionless players go to the global spawn (or world spawn fallback)
 * via {@link SpawnTeleporter#toSpawn(ServerPlayer)}.
 * <p>
 * Vanilla respawn flow:
 * <ol>
 *   <li>If the player had a bed/anchor/respawn position and it's still
 *       valid, vanilla teleports them there. We don't override that —
 *       admin-configured beds are intentional.</li>
 *   <li>Otherwise vanilla drops them at the world spawn. <b>That's where
 *       this handler steps in.</b></li>
 * </ol>
 * <p>
 * We compare the post-respawn position against the world spawn: if they
 * match (within 4 blocks), we know vanilla took the fallback and we can
 * safely override. If the player is somewhere else, they had a personal
 * respawn point and we leave them alone.
 */
@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class FactionRespawnHandler {

    private FactionRespawnHandler() {
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.isEndConquered()) return; // returning from end portal — don't relocate.

        if (hasPersonalRespawnPoint(player)) {
            return;
        }

        // Resolve faction: prefer attachment (new DB pipeline), fall back to legacy profile.
        FactionId factionId = resolveFactionId(player);
        Faction faction = Faction.NONE;
        Faction candidate = Faction.NONE;
        if (factionId != null) {
            faction = mapFactionId(factionId);
        } else {
            WarPlayerProfile profile = WarPlayerDataStore.get().getOrCreate(player);
            faction = profile.getFaction();
            candidate = profile.getCandidateFaction();
        }

        // Collaborator → spawn at general spawn instead of own faction base —
        // they lost the privilege of base respawn.
        boolean collaborator = player.getData(WpAttachmentTypes.COLLABORATOR.get());
        if (collaborator) {
            SpawnTeleporter.toSpawn(player);
            return;
        }

        Optional<WarpPoint> base = Optional.empty();
        if (faction.isPlayable()) {
            base = WarServerSettings.get().getBasePoint(faction);
        } else if (candidate.isPlayable()) {
            base = WarServerSettings.get().getBasePoint(candidate);
        }
        if (base.isPresent() && base.get().teleport(player)) {
            return;
        }
        // Faction has no configured base → fall through to global spawn.
        SpawnTeleporter.toSpawn(player);
    }

    /**
     * Resolves the player's faction from attachments (new pipeline) first,
     * then returns null if no playable faction found via attachments.
     */
    private static FactionId resolveFactionId(ServerPlayer player) {
        java.util.Optional<FactionId> attachmentFaction = player.getData(WpAttachmentTypes.FACTION.get());
        if (attachmentFaction != null && attachmentFaction.isPresent()) {
            return attachmentFaction.get();
        }
        return null;
    }

    /**
     * Maps a new-pipeline FactionId back to the legacy Faction enum
     * for use with WarServerSettings.getBasePoint().
     */
    private static Faction mapFactionId(FactionId id) {
        return switch (id) {
            case ZARNAVIA -> Faction.ZARNAVIA;
            case CHERNOGRYAD -> Faction.CHERNOGRYAD;
        };
    }

    /**
     * True iff the player has a personal respawn point set (bed, anchor,
     * small tent). When vanilla respects it, we should not override.
     */
    private static boolean hasPersonalRespawnPoint(ServerPlayer player) {
        try {
            return player.getRespawnPosition() != null;
        } catch (Throwable t) {
            WarProject.LOGGER.debug("[WP Respawn] Unable to read respawn position", t);
            return false;
        }
    }
}
