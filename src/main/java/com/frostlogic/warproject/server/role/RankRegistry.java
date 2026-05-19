package com.frostlogic.warproject.server.role;

import com.frostlogic.warproject.WpConfig;
import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.persistence.Database;
import com.frostlogic.warproject.persistence.dao.RanksDao;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.sql.Connection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Registry that loads faction ranks from configuration into the {@code ranks} database table
 * and provides lookup methods.
 * <p>
 * Rank format in config: {@code "ROLE:rankName"} where ROLE is one of SOLDIER, COMMANDER, GENERAL.
 * Ranks are ordered by their position in the config list (0-based ordinal).
 * <p>
 * On {@link #reload()}, all existing ranks for each faction are deleted and re-inserted
 * from the current config values. This ensures the DB always reflects the latest config.
 * <p>
 * Requirements: 10.x, 22.1, 22.2
 * Design: §3, §16
 */
public final class RankRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final Database database;
    private final RanksDao ranksDao;

    public RankRegistry(Database database, RanksDao ranksDao) {
        this.database = database;
        this.ranksDao = ranksDao;
    }

    /**
     * Reloads ranks for both factions from config into the database.
     * Deletes existing ranks and re-inserts from {@link WpConfig#RANKS_ZARNAVIA}
     * and {@link WpConfig#RANKS_CHERNOGRYAD}.
     */
    public void reload() {
        database.transaction(conn -> {
            loadFactionRanks(conn, FactionId.ZARNAVIA, WpConfig.RANKS_ZARNAVIA.get());
            loadFactionRanks(conn, FactionId.CHERNOGRYAD, WpConfig.RANKS_CHERNOGRYAD.get());
        });
        LOGGER.info("RankRegistry reloaded ranks for both factions from config");
    }

    /**
     * Returns all ranks for the given faction, ordered by ordinal (lowest to highest).
     *
     * @param factionId the faction to query
     * @return unmodifiable list of ranks, may be empty
     */
    public List<RanksDao.Rank> getRanks(FactionId factionId) {
        return database.inTx(conn ->
                Collections.unmodifiableList(ranksDao.findByFaction(conn, factionId.getSerializedName()))
        );
    }

    /**
     * Looks up a specific rank by faction and rank name.
     *
     * @param factionId the faction
     * @param rankName  the rank name (e.g. "сержант")
     * @return the rank if found, empty otherwise
     */
    public Optional<RanksDao.Rank> getRank(FactionId factionId, String rankName) {
        return database.inTx(conn ->
                ranksDao.findByKey(conn, factionId.getSerializedName(), rankName)
        );
    }

    /**
     * Parses config entries and inserts them into the ranks table for a single faction.
     * Existing ranks for the faction are deleted first.
     *
     * @param conn    the transactional connection
     * @param faction the faction being loaded
     * @param entries config entries in format "ROLE:rankName"
     */
    private void loadFactionRanks(Connection conn, FactionId faction, List<? extends String> entries) {
        String factionName = faction.getSerializedName();
        ranksDao.deleteByFaction(conn, factionName);

        for (int i = 0; i < entries.size(); i++) {
            String entry = entries.get(i);
            int colonIdx = entry.indexOf(':');
            if (colonIdx <= 0 || colonIdx >= entry.length() - 1) {
                LOGGER.warn("RankRegistry: skipping malformed rank entry '{}' for faction {}", entry, factionName);
                continue;
            }

            String rolePart = entry.substring(0, colonIdx).trim().toUpperCase();
            String rankName = entry.substring(colonIdx + 1).trim();

            if (rankName.isEmpty()) {
                LOGGER.warn("RankRegistry: skipping entry with empty rank name '{}' for faction {}", entry, factionName);
                continue;
            }

            // Validate role part
            if (!isValidRoleForRank(rolePart)) {
                LOGGER.warn("RankRegistry: skipping entry with invalid role '{}' in '{}' for faction {}",
                        rolePart, entry, factionName);
                continue;
            }

            RanksDao.Rank rank = new RanksDao.Rank(factionName, rolePart, rankName, i);
            ranksDao.insert(conn, rank);
        }

        LOGGER.debug("RankRegistry: loaded {} ranks for faction {}", entries.size(), factionName);
    }

    /**
     * Validates that the role part of a rank entry is one of the allowed values.
     * Only SOLDIER, COMMANDER, and GENERAL can have ranks assigned.
     */
    private static boolean isValidRoleForRank(String role) {
        return "SOLDIER".equals(role) || "COMMANDER".equals(role) || "GENERAL".equals(role);
    }
}
