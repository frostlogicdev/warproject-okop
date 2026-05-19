package com.frostlogic.warproject.persistence.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for the {@code player_awards} table.
 * <p>
 * Each method accepts a {@link Connection} parameter — no connection state is stored.
 * Pure JDBC, no Minecraft dependencies.
 * <p>
 * Requirements: 17.5, 14.1
 */
public final class PlayerAwardsDao {

    public record PlayerAwardRow(
            int id,
            String playerUuid,
            int awardId,
            String grantedByUuid,
            long grantedAt
    ) {}

    public int insert(Connection conn, PlayerAwardRow row) {
        String sql = """
                INSERT INTO player_awards (player_uuid, award_id, granted_by_uuid, granted_at)
                VALUES (?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, row.playerUuid());
            ps.setInt(2, row.awardId());
            ps.setString(3, row.grantedByUuid());
            ps.setLong(4, row.grantedAt());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
                return -1;
            }
        } catch (SQLException e) {
            throw new RuntimeException("PlayerAwardsDao.insert failed", e);
        }
    }

    /**
     * Returns all awards granted to a specific player, ordered by grant date descending (most recent first).
     */
    public List<PlayerAwardRow> findByPlayer(Connection conn, String playerUuid) {
        String sql = "SELECT * FROM player_awards WHERE player_uuid = ? ORDER BY granted_at DESC";
        List<PlayerAwardRow> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("PlayerAwardsDao.findByPlayer failed", e);
        }
        return result;
    }

    /**
     * Checks whether a specific player already holds a specific award.
     */
    public boolean existsByPlayerAndAward(Connection conn, String playerUuid, int awardId) {
        String sql = "SELECT 1 FROM player_awards WHERE player_uuid = ? AND award_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            ps.setInt(2, awardId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("PlayerAwardsDao.existsByPlayerAndAward failed", e);
        }
    }

    /**
     * Returns the number of players who hold a specific award.
     */
    public int countByAward(Connection conn, int awardId) {
        String sql = "SELECT COUNT(*) FROM player_awards WHERE award_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, awardId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("PlayerAwardsDao.countByAward failed", e);
        }
    }

    private PlayerAwardRow mapRow(ResultSet rs) throws SQLException {
        return new PlayerAwardRow(
                rs.getInt("id"),
                rs.getString("player_uuid"),
                rs.getInt("award_id"),
                rs.getString("granted_by_uuid"),
                rs.getLong("granted_at")
        );
    }
}
