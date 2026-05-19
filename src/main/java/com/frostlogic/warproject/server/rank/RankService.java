package com.frostlogic.warproject.server.rank;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.attachment.Role;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import com.frostlogic.warproject.persistence.Database;
import com.frostlogic.warproject.persistence.dao.AuditLogDao;
import com.frostlogic.warproject.persistence.dao.PlayersDao;
import com.frostlogic.warproject.persistence.dao.RanksDao;
import com.frostlogic.warproject.server.role.RankRegistry;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Service for promoting and demoting players within their faction's rank hierarchy.
 * <p>
 * Each operation atomically updates the {@code players.rank} and {@code players.role}
 * columns in the database, writes an audit log entry, and refreshes the player's
 * in-memory attachments.
 * <p>
 * Requirements: 22.1, 22.2
 * Design: §3, §16
 */
public final class RankService {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile RankService current;

    private final Database database;
    private final PlayersDao playersDao;
    private final AuditLogDao auditLogDao;
    private final RankRegistry rankRegistry;

    public RankService(Database database, PlayersDao playersDao,
                       AuditLogDao auditLogDao, RankRegistry rankRegistry) {
        this.database = Objects.requireNonNull(database);
        this.playersDao = Objects.requireNonNull(playersDao);
        this.auditLogDao = Objects.requireNonNull(auditLogDao);
        this.rankRegistry = Objects.requireNonNull(rankRegistry);
    }

    public static void install(RankService service) {
        current = Objects.requireNonNull(service);
    }

    public static void uninstall() {
        current = null;
    }

    @Nullable
    public static RankService current() {
        return current;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Result type
    // ═══════════════════════════════════════════════════════════════════════

    public sealed interface RankResult {
        record Success(String newRank, String newRole) implements RankResult {}
        record Failure(String errorKey) implements RankResult {}
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Promotes the target player to the next rank in their faction's hierarchy.
     *
     * @param initiator the player performing the promotion (must be COMMANDER+)
     * @param target    the player being promoted
     * @return result indicating success or failure
     */
    public RankResult promote(ServerPlayer initiator, ServerPlayer target) {
        return changeRank(initiator, target, +1, "PROMOTE");
    }

    /**
     * Demotes the target player to the previous rank in their faction's hierarchy.
     *
     * @param initiator the player performing the demotion (must be COMMANDER+)
     * @param target    the player being demoted
     * @return result indicating success or failure
     */
    public RankResult demote(ServerPlayer initiator, ServerPlayer target) {
        return changeRank(initiator, target, -1, "DEMOTE");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Internal
    // ═══════════════════════════════════════════════════════════════════════

    private RankResult changeRank(ServerPlayer initiator, ServerPlayer target,
                                  int direction, String auditAction) {
        // Validate target has a faction
        Optional<FactionId> targetFaction = target.getData(WpAttachmentTypes.FACTION.get());
        if (targetFaction.isEmpty()) {
            return new RankResult.Failure("wp.rank.error.no_faction");
        }

        FactionId faction = targetFaction.get();

        // Get the ordered rank list for this faction
        List<RanksDao.Rank> ranks = rankRegistry.getRanks(faction);
        if (ranks.isEmpty()) {
            return new RankResult.Failure("wp.rank.error.no_ranks_configured");
        }

        // Find the target's current rank
        String currentRankName = target.getData(WpAttachmentTypes.RANK.get());
        int currentIndex = -1;
        if (currentRankName != null && !currentRankName.isEmpty()) {
            for (int i = 0; i < ranks.size(); i++) {
                if (ranks.get(i).rank().equals(currentRankName)) {
                    currentIndex = i;
                    break;
                }
            }
        }

        // If no current rank, start at -1 (promote will go to 0)
        int newIndex = currentIndex + direction;

        if (newIndex < 0) {
            return new RankResult.Failure("wp.rank.error.already_lowest");
        }
        if (newIndex >= ranks.size()) {
            return new RankResult.Failure("wp.rank.error.already_highest");
        }

        // Check that initiator's rank is higher than the new rank (unless OP)
        Role initiatorRole = initiator.getData(WpAttachmentTypes.ROLE.get());
        if (initiatorRole != Role.OP) {
            String initiatorRankName = initiator.getData(WpAttachmentTypes.RANK.get());
            int initiatorIndex = -1;
            if (initiatorRankName != null && !initiatorRankName.isEmpty()) {
                for (int i = 0; i < ranks.size(); i++) {
                    if (ranks.get(i).rank().equals(initiatorRankName)) {
                        initiatorIndex = i;
                        break;
                    }
                }
            }
            if (initiatorIndex <= newIndex) {
                return new RankResult.Failure("wp.rank.error.insufficient_rank");
            }
        }

        RanksDao.Rank newRank = ranks.get(newIndex);
        String targetUuid = target.getStringUUID();
        String targetName = target.getGameProfile().getName();
        String actorUuid = initiator.getStringUUID();
        String actorName = initiator.getGameProfile().getName();
        long now = System.currentTimeMillis();

        try {
            database.transaction(conn -> {
                playersDao.setRank(conn, targetUuid, newRank.rank(), newRank.role());
                auditLogDao.insert(conn, now, actorUuid, actorName,
                        targetUuid, targetName, auditAction, null,
                        "{\"rank\":\"" + newRank.rank() + "\",\"role\":\"" + newRank.role() + "\"}");
            });
        } catch (RuntimeException e) {
            LOGGER.error("[WP RankService] {} failed for target={}: {}",
                    auditAction, targetName, e.getMessage(), e);
            return new RankResult.Failure("wp.error.internal");
        }

        // Update attachments
        target.setData(WpAttachmentTypes.RANK.get(), newRank.rank());
        Role newRole = mapRoleFromString(newRank.role());
        target.setData(WpAttachmentTypes.ROLE.get(), newRole);

        // Update Military ID card with new rank (Req. 5.1)
        updateMilitaryIdRank(target, newRank.rank());

        // Legacy mirror
        try {
            com.frostlogic.warproject.server.WarPlayerProfile legacy =
                    com.frostlogic.warproject.server.WarPlayerDataStore.get().getOrCreate(target);
            com.frostlogic.warproject.server.Rank legacyRank =
                    com.frostlogic.warproject.server.Rank.fromInput(newRank.rank()).orElse(null);
            if (legacyRank != null) {
                legacy.setRank(legacyRank);
            }
            com.frostlogic.warproject.server.WarPlayerDataStore.get().save();
            com.frostlogic.warproject.server.WarPrefixManager.refresh(target);
        } catch (Exception e) {
            LOGGER.warn("[WP RankService] Legacy mirror failed for {}: {}", targetName, e.getMessage());
        }

        LOGGER.info("[WP RankService] {} {} → rank '{}' (role {}), by {}",
                auditAction, targetName, newRank.rank(), newRank.role(), actorName);

        return new RankResult.Success(newRank.rank(), newRank.role());
    }

    private static Role mapRoleFromString(String roleStr) {
        return switch (roleStr.toUpperCase()) {
            case "GENERAL" -> Role.GENERAL;
            case "COMMANDER" -> Role.COMMANDER;
            case "SOLDIER" -> Role.SOLDIER;
            default -> Role.CANDIDATE;
        };
    }

    /**
     * Updates the Military ID card rank field after a successful rank change.
     * <p>
     * Failures are logged but do not affect the rank change result.
     * <p>
     * Requirements: 5.1
     */
    private static void updateMilitaryIdRank(ServerPlayer target, String newRankName) {
        com.frostlogic.warproject.server.militaryid.MilitaryIdService militaryIdService =
                com.frostlogic.warproject.network.ServiceRegistry.militaryId();
        if (militaryIdService == null) {
            LOGGER.debug("[WP RankService] MilitaryIdService not available — skipping card rank update for {}",
                    target.getGameProfile().getName());
            return;
        }

        try {
            militaryIdService.updateRank(target, newRankName);
        } catch (Exception e) {
            LOGGER.error("[WP RankService] Failed to update Military ID rank for {}: {}",
                    target.getGameProfile().getName(), e.getMessage(), e);
        }
    }
}
