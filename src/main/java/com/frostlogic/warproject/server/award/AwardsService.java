package com.frostlogic.warproject.server.award;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.persistence.Database;
import com.frostlogic.warproject.persistence.dao.AuditLogDao;
import com.frostlogic.warproject.persistence.dao.AwardDefinitionsDao;
import com.frostlogic.warproject.persistence.dao.AwardDefinitionsDao.AwardDefinitionRow;
import com.frostlogic.warproject.persistence.dao.PlayerAwardsDao;
import com.frostlogic.warproject.persistence.dao.PlayerAwardsDao.PlayerAwardRow;
import com.frostlogic.warproject.persistence.dao.PlayersDao;
import com.frostlogic.warproject.server.Faction;
import com.frostlogic.warproject.server.WarPlayerDataStore;
import com.frostlogic.warproject.server.WarPlayerProfile;
import com.frostlogic.warproject.server.militaryid.MilitaryIdService;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing award definitions, manual/automatic granting, and display data.
 * <p>
 * Each state-changing operation runs inside a single {@link Database#inTx} call so that
 * the primary effect and the corresponding audit log row are committed atomically.
 * Failures are returned as {@link Result.Failure} — never a partially-applied transaction.
 * <p>
 * Requirements: 13.1–13.9, 14.1–14.7, 15.1–15.6, 16.1
 * Design: §5 Awards System — AwardsService
 */
public final class AwardsService {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Maximum number of awards displayed on the Military ID card. */
    public static final int MAX_DISPLAY_AWARDS = 6;

    // ── Validation constants ───────────────────────────────────────────────
    public static final int MIN_NAME_LENGTH = 3;
    public static final int MAX_NAME_LENGTH = 48;
    public static final int MIN_DESCRIPTION_LENGTH = 1;
    public static final int MAX_DESCRIPTION_LENGTH = 256;

    // ── Audit action keys ──────────────────────────────────────────────────
    private static final String ACTION_AWARD_CREATE = "AWARD_CREATE";
    private static final String ACTION_AWARD_DELETE = "AWARD_DELETE";
    private static final String ACTION_AWARD_GRANT = "AWARD_GRANT";
    private static final String ACTION_AWARD_AUTO_GRANT = "AWARD_AUTO_GRANT";

    // ── Error keys ─────────────────────────────────────────────────────────
    public static final String ERR_INVALID_NAME = "wp.award.invalid_name";
    public static final String ERR_INVALID_DESCRIPTION = "wp.award.invalid_description";
    public static final String ERR_NAME_EXISTS = "wp.award.name_exists";
    public static final String ERR_NOT_FOUND = "wp.award.not_found";
    public static final String ERR_IN_USE = "wp.award.in_use";
    public static final String ERR_PLAYER_NOT_FOUND = "wp.award.player_not_found";
    public static final String ERR_FACTION_MISMATCH = "wp.award.faction_mismatch";
    public static final String ERR_ALREADY_GRANTED = "wp.award.already_granted";
    public static final String ERR_INTERNAL = "wp.command.error.internal";

    // ── Dependencies ───────────────────────────────────────────────────────
    private final Database database;
    private final AwardDefinitionsDao definitionsDao;
    private final PlayerAwardsDao playerAwardsDao;
    private final AuditLogDao auditLogDao;
    private final PlayersDao playersDao;
    private volatile MilitaryIdService militaryIdService;

    public AwardsService(Database database,
                         AwardDefinitionsDao definitionsDao,
                         PlayerAwardsDao playerAwardsDao,
                         AuditLogDao auditLogDao,
                         PlayersDao playersDao) {
        this.database = Objects.requireNonNull(database, "database");
        this.definitionsDao = Objects.requireNonNull(definitionsDao, "definitionsDao");
        this.playerAwardsDao = Objects.requireNonNull(playerAwardsDao, "playerAwardsDao");
        this.auditLogDao = Objects.requireNonNull(auditLogDao, "auditLogDao");
        this.playersDao = Objects.requireNonNull(playersDao, "playersDao");
    }

    /**
     * Sets the MilitaryIdService reference for card synchronization.
     * Called during initialization wiring (task 14.2) once MilitaryIdService is available.
     */
    public void setMilitaryIdService(MilitaryIdService militaryIdService) {
        this.militaryIdService = militaryIdService;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Admin operations
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Creates a new award definition (Req. 13.1–13.5, 13.9).
     * <p>
     * Validates name (3–48 chars), description (1–256 chars), and case-insensitive
     * name uniqueness. Persists the definition and logs to audit in a single transaction.
     *
     * @param name        the award name
     * @param description the award description
     * @return success with the created {@link AwardDefinition}, or failure with error key
     */
    public Result<AwardDefinition> createAward(String name, String description) {
        if (name == null || name.length() < MIN_NAME_LENGTH || name.length() > MAX_NAME_LENGTH) {
            return Result.failure(ERR_INVALID_NAME);
        }
        if (description == null || description.length() < MIN_DESCRIPTION_LENGTH || description.length() > MAX_DESCRIPTION_LENGTH) {
            return Result.failure(ERR_INVALID_DESCRIPTION);
        }

        long now = System.currentTimeMillis();

        try {
            return database.inTx(conn -> {
                // Case-insensitive duplicate check
                Optional<AwardDefinitionRow> existing = definitionsDao.findByName(conn, name);
                if (existing.isPresent()) {
                    return Result.<AwardDefinition>failure(ERR_NAME_EXISTS);
                }

                AwardDefinitionRow row = new AwardDefinitionRow(0, name, description, "default_medal", now);
                int id = definitionsDao.insert(conn, row);

                auditLogDao.insert(conn, now, null, null,
                        null, null, ACTION_AWARD_CREATE, null,
                        "{\"name\":\"" + escapeJson(name) + "\","
                                + "\"description\":\"" + escapeJson(truncate(description, 100)) + "\","
                                + "\"id\":" + id + "}");

                return Result.<AwardDefinition>success(
                        new AwardDefinition(id, name, description, "default_medal", now));
            });
        } catch (RuntimeException e) {
            LOGGER.error("[WP AwardsService.createAward] Transaction failed for name='{}': {}",
                    name, e.getMessage(), e);
            return Result.failure(ERR_INTERNAL);
        }
    }

    /**
     * Deletes an award definition (Req. 13.6, 13.7, 13.9).
     * <p>
     * Rejects deletion if any players currently hold the award. Deletes the definition
     * and logs to audit in a single transaction.
     *
     * @param name the award name to delete
     * @return success or failure with error key
     */
    public Result<Void> deleteAward(String name) {
        if (name == null || name.isBlank()) {
            return Result.failure(ERR_NOT_FOUND);
        }

        long now = System.currentTimeMillis();

        try {
            return database.inTx(conn -> {
                Optional<AwardDefinitionRow> existing = definitionsDao.findByName(conn, name);
                if (existing.isEmpty()) {
                    return Result.<Void>failure(ERR_NOT_FOUND);
                }

                AwardDefinitionRow award = existing.get();
                int holders = playerAwardsDao.countByAward(conn, award.id());
                if (holders > 0) {
                    return Result.<Void>failure(ERR_IN_USE);
                }

                definitionsDao.delete(conn, award.id());

                auditLogDao.insert(conn, now, null, null,
                        null, null, ACTION_AWARD_DELETE, null,
                        "{\"name\":\"" + escapeJson(award.name()) + "\","
                                + "\"id\":" + award.id() + "}");

                return Result.<Void>success(null);
            });
        } catch (RuntimeException e) {
            LOGGER.error("[WP AwardsService.deleteAward] Transaction failed for name='{}': {}",
                    name, e.getMessage(), e);
            return Result.failure(ERR_INTERNAL);
        }
    }

    /**
     * Lists all defined awards (Req. 13.8).
     *
     * @return list of all award definitions ordered by creation date
     */
    public List<AwardDefinition> listAwards() {
        try {
            return database.inTx(conn -> {
                List<AwardDefinitionRow> rows = definitionsDao.findAll(conn);
                List<AwardDefinition> result = new ArrayList<>(rows.size());
                for (AwardDefinitionRow row : rows) {
                    result.add(new AwardDefinition(row.id(), row.name(), row.description(), row.iconId(), row.createdAt()));
                }
                return result;
            });
        } catch (RuntimeException e) {
            LOGGER.error("[WP AwardsService.listAwards] Failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Granting
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Grants an award to a target player (Req. 14.1–14.7).
     * <p>
     * Validates: award exists, target has a profile, same faction check, duplicate check.
     * Persists the grant and audit log in a single transaction. Broadcasts to faction
     * and appends to Military ID card.
     *
     * @param awardName      the name of the award to grant
     * @param targetUuid     the UUID of the target player
     * @param grantedByUuid  the UUID of the granting officer
     * @param grantingFaction the faction of the granting officer
     * @return success or failure with error key
     */
    public Result<Void> grantAward(String awardName, UUID targetUuid, UUID grantedByUuid, FactionId grantingFaction) {
        Objects.requireNonNull(targetUuid, "targetUuid");
        Objects.requireNonNull(grantedByUuid, "grantedByUuid");
        Objects.requireNonNull(grantingFaction, "grantingFaction");

        if (awardName == null || awardName.isBlank()) {
            return Result.failure(ERR_NOT_FOUND);
        }

        // Check target player has a profile and is in the same faction
        Optional<WarPlayerProfile> targetProfile = WarPlayerDataStore.get().findByUuid(targetUuid);
        if (targetProfile.isEmpty() || !targetProfile.get().isFactionMember()) {
            return Result.failure(ERR_PLAYER_NOT_FOUND);
        }

        WarPlayerProfile profile = targetProfile.get();
        FactionId targetFaction = factionIdFromProfile(profile);
        if (targetFaction == null || targetFaction != grantingFaction) {
            return Result.failure(ERR_FACTION_MISMATCH);
        }

        long now = System.currentTimeMillis();
        String targetUuidStr = targetUuid.toString();
        String grantedByUuidStr = grantedByUuid.toString();

        try {
            return database.inTx(conn -> {
                // Validate award exists
                Optional<AwardDefinitionRow> awardOpt = definitionsDao.findByName(conn, awardName);
                if (awardOpt.isEmpty()) {
                    return Result.<Void>failure(ERR_NOT_FOUND);
                }
                AwardDefinitionRow award = awardOpt.get();

                // Duplicate check
                if (playerAwardsDao.existsByPlayerAndAward(conn, targetUuidStr, award.id())) {
                    return Result.<Void>failure(ERR_ALREADY_GRANTED);
                }

                // Persist grant
                PlayerAwardRow grantRow = new PlayerAwardRow(0, targetUuidStr, award.id(), grantedByUuidStr, now);
                playerAwardsDao.insert(conn, grantRow);

                // Audit log
                auditLogDao.insert(conn, now, grantedByUuidStr, null,
                        targetUuidStr, profile.getLastKnownName(), ACTION_AWARD_GRANT, null,
                        "{\"award\":\"" + escapeJson(award.name()) + "\","
                                + "\"award_id\":" + award.id() + "}");

                return Result.<Void>success(null);
            });
        } catch (RuntimeException e) {
            LOGGER.error("[WP AwardsService.grantAward] Transaction failed for award='{}' target={}: {}",
                    awardName, targetUuidStr, e.getMessage(), e);
            return Result.failure(ERR_INTERNAL);
        }
    }

    /**
     * Called after a successful grant to perform side effects that require a MinecraftServer context:
     * broadcasting to faction and appending to Military ID card.
     * <p>
     * This is separated from the transactional grant to avoid holding a DB transaction
     * while performing network I/O.
     *
     * @param server         the Minecraft server instance
     * @param awardName      the name of the granted award
     * @param targetUuid     the UUID of the target player
     * @param grantingFaction the faction to broadcast to
     */
    public void onGrantSuccess(MinecraftServer server, String awardName, UUID targetUuid, FactionId grantingFaction) {
        // Broadcast to faction (Req. 14.6)
        String targetName = WarPlayerDataStore.get().findByUuid(targetUuid)
                .map(WarPlayerProfile::getLastKnownName)
                .orElse("???");

        Component message = Component.literal(targetName + " получил награду '" + awardName + "'!")
                .withStyle(ChatFormatting.GOLD);

        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            WarPlayerProfile onlineProfile = WarPlayerDataStore.get().getOrCreate(online);
            if (onlineProfile.isFactionMember() && factionIdFromProfile(onlineProfile) == grantingFaction) {
                online.sendSystemMessage(message);
            }
        }

        // Append to Military ID card (Req. 5.4, integration with MilitaryIdService)
        MilitaryIdService midService = this.militaryIdService;
        if (midService != null) {
            ServerPlayer targetPlayer = server.getPlayerList().getPlayer(targetUuid);
            if (targetPlayer != null) {
                midService.appendAward(targetPlayer, awardName);
            }
        }
    }

    /**
     * Returns the player's awards, ordered by most recent first (Req. 16.1).
     * <p>
     * For Military ID display, returns at most {@link #MAX_DISPLAY_AWARDS} entries.
     *
     * @param playerUuid the player's UUID
     * @return list of player awards (max 6 most recent)
     */
    public List<PlayerAward> getPlayerAwards(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        String uuidStr = playerUuid.toString();

        try {
            return database.inTx(conn -> {
                List<PlayerAwardRow> rows = playerAwardsDao.findByPlayer(conn, uuidStr);
                List<PlayerAward> result = new ArrayList<>(Math.min(rows.size(), MAX_DISPLAY_AWARDS));
                int limit = Math.min(rows.size(), MAX_DISPLAY_AWARDS);
                for (int i = 0; i < limit; i++) {
                    PlayerAwardRow row = rows.get(i);
                    // Resolve award name from definition
                    Optional<AwardDefinitionRow> def = definitionsDao.findById(conn, row.awardId());
                    String name = def.map(AwardDefinitionRow::name).orElse("???");
                    String description = def.map(AwardDefinitionRow::description).orElse("");
                    String iconId = def.map(AwardDefinitionRow::iconId).orElse("default_medal");
                    result.add(new PlayerAward(name, description, iconId, row.grantedAt()));
                }
                return result;
            });
        } catch (RuntimeException e) {
            LOGGER.error("[WP AwardsService.getPlayerAwards] Failed for player={}: {}",
                    uuidStr, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Returns ALL of a player's awards (no limit), for the /wp awards command display.
     *
     * @param playerUuid the player's UUID
     * @return list of all player awards ordered by most recent first
     */
    public List<PlayerAward> getAllPlayerAwards(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        String uuidStr = playerUuid.toString();

        try {
            return database.inTx(conn -> {
                List<PlayerAwardRow> rows = playerAwardsDao.findByPlayer(conn, uuidStr);
                List<PlayerAward> result = new ArrayList<>(rows.size());
                for (PlayerAwardRow row : rows) {
                    Optional<AwardDefinitionRow> def = definitionsDao.findById(conn, row.awardId());
                    String name = def.map(AwardDefinitionRow::name).orElse("???");
                    String description = def.map(AwardDefinitionRow::description).orElse("");
                    String iconId = def.map(AwardDefinitionRow::iconId).orElse("default_medal");
                    result.add(new PlayerAward(name, description, iconId, row.grantedAt()));
                }
                return result;
            });
        } catch (RuntimeException e) {
            LOGGER.error("[WP AwardsService.getAllPlayerAwards] Failed for player={}: {}",
                    uuidStr, e.getMessage(), e);
            return List.of();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Automatic triggers
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Evaluates automatic award triggers for the given trigger type (Req. 15.1–15.6).
     * <p>
     * Loads triggers from config, finds matching trigger, and grants the award
     * if the player doesn't already have it. Displays an action bar notification
     * on success.
     *
     * @param triggerType the trigger type identifier (e.g. "CAPTURE_SUCCESS")
     * @param player      the player who triggered the event
     */
    public void evaluateTrigger(String triggerType, ServerPlayer player) {
        if (triggerType == null || player == null) {
            return;
        }

        // Only evaluate for faction members (Req. 15.4)
        WarPlayerProfile profile = WarPlayerDataStore.get().getOrCreate(player);
        if (!profile.isFactionMember()) {
            return;
        }

        // Find matching trigger from config
        Optional<AwardTrigger> triggerOpt = AwardTrigger.findByType(triggerType);
        if (triggerOpt.isEmpty()) {
            return;
        }

        AwardTrigger trigger = triggerOpt.get();
        String awardName = trigger.awardName();
        String playerUuidStr = player.getUUID().toString();

        try {
            boolean granted = database.inTx(conn -> {
                // Validate award exists
                Optional<AwardDefinitionRow> awardOpt = definitionsDao.findByName(conn, awardName);
                if (awardOpt.isEmpty()) {
                    LOGGER.warn("[WP AwardsService.evaluateTrigger] Award '{}' not found for trigger '{}'",
                            awardName, triggerType);
                    return false;
                }
                AwardDefinitionRow award = awardOpt.get();

                // Duplicate check — skip if player already has it (Req. 15.4)
                if (playerAwardsDao.existsByPlayerAndAward(conn, playerUuidStr, award.id())) {
                    return false;
                }

                // Persist grant
                long now = System.currentTimeMillis();
                PlayerAwardRow grantRow = new PlayerAwardRow(0, playerUuidStr, award.id(), "SYSTEM", now);
                playerAwardsDao.insert(conn, grantRow);

                // Audit log
                auditLogDao.insert(conn, now, "SYSTEM", "SYSTEM",
                        playerUuidStr, player.getGameProfile().getName(), ACTION_AWARD_AUTO_GRANT, null,
                        "{\"award\":\"" + escapeJson(award.name()) + "\","
                                + "\"trigger\":\"" + escapeJson(triggerType) + "\","
                                + "\"award_id\":" + award.id() + "}");

                return true;
            });

            if (granted) {
                // Action bar notification (Req. 15.3)
                player.displayClientMessage(
                        Component.literal("Вы получили награду: '" + awardName + "'")
                                .withStyle(ChatFormatting.GOLD),
                        true);

                // Append to Military ID card
                MilitaryIdService midService = this.militaryIdService;
                if (midService != null) {
                    midService.appendAward(player, awardName);
                }
            }
        } catch (RuntimeException e) {
            // Req. 15.6: log failure, suppress notification
            LOGGER.error("[WP AwardsService.evaluateTrigger] Failed for trigger='{}' player={}: {}",
                    triggerType, player.getGameProfile().getName(), e.getMessage(), e);
        }
    }

    /**
     * Called when a player successfully captures an enemy's passport (Req. 15.1).
     * Delegates to {@link #evaluateTrigger} with "CAPTURE_SUCCESS" type.
     *
     * @param captor the player who performed the capture
     */
    public void onCaptureSuccess(ServerPlayer captor) {
        evaluateTrigger(AwardTrigger.CAPTURE_SUCCESS, captor);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Maps a WarPlayerProfile's faction to a FactionId enum value.
     * Returns null if the profile has no playable faction.
     */
    private static FactionId factionIdFromProfile(WarPlayerProfile profile) {
        Faction faction = profile.getFaction();
        if (faction == Faction.ZARNAVIA) {
            return FactionId.ZARNAVIA;
        } else if (faction == Faction.CHERNOGRYAD) {
            return FactionId.CHERNOGRYAD;
        }
        return null;
    }

    /** Minimal JSON-string escape for audit_log.extra_json. */
    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 2);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    /** Truncates a string to the given max length. */
    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Result type
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Sealed result type for service operations. Either a {@link Success}
     * carrying the operation's value, or a {@link Failure} carrying a
     * translation key suitable for {@code Component.translatable(errorKey, ...)}
     * in the calling Brigadier handler.
     */
    public sealed interface Result<T> {

        /** @return {@code true} iff this is a {@link Success}. */
        default boolean isSuccess() {
            return this instanceof Success<T>;
        }

        /**
         * Convenience accessor: returns the success value, or throws
         * {@link IllegalStateException} on a failure.
         */
        @SuppressWarnings("unchecked")
        default T orThrow() {
            if (this instanceof Success<?> s) {
                return (T) s.value();
            }
            throw new IllegalStateException("AwardsService.Result was a Failure: "
                    + ((Failure<?>) this).errorKey());
        }

        static <T> Result<T> success(T value) {
            return new Success<>(value);
        }

        static <T> Result<T> failure(String errorKey) {
            return new Failure<>(errorKey);
        }

        record Success<T>(T value) implements Result<T> {}

        record Failure<T>(String errorKey) implements Result<T> {}
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PlayerAward record (display data)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Display-oriented record for a player's award, containing resolved name,
     * description, icon reference, and grant timestamp.
     */
    public record PlayerAward(
            String name,
            String description,
            String iconId,
            long grantedAt
    ) {}
}
