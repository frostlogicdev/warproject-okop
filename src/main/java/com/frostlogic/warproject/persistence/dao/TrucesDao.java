package com.frostlogic.warproject.persistence.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO for the {@code truces} table.
 * <p>
 * Each method accepts a {@link Connection} parameter — no connection state is stored.
 * Pure JDBC, no Minecraft dependencies.
 * <p>
 * Requirements: 17.3, 10.6
 */
public final class TrucesDao {

    public record Truce(
            int id,
            String proposingFaction,
            String targetFaction,
            int durationMinutes,
            long startedAt,
            Long endedAt,
            String status
    ) {}

    /**
     * Inserts a new truce and returns the generated ID.
     */
    public int insert(Connection conn, Truce truce) {
        String sql = """
                INSERT INTO truces (proposing_faction, target_faction, duration_minutes, started_at, ended_at, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, truce.proposingFaction());
            ps.setString(2, truce.targetFaction());
            ps.setInt(3, truce.durationMinutes());
            ps.setLong(4, truce.startedAt());
            setNullableLong(ps, 5, truce.endedAt());
            ps.setString(6, truce.status());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
                throw new SQLException("Insert did not return generated key");
            }
        } catch (SQLException e) {
            throw new RuntimeException("TrucesDao.insert failed", e);
        }
    }

    /**
     * Finds a truce by its ID.
     */
    public Optional<Truce> findById(Connection conn, int id) {
        String sql = "SELECT * FROM truces WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("TrucesDao.findById failed", e);
        }
    }

    /**
     * Finds all truces with status 'ACTIVE'.
     */
    public List<Truce> findActive(Connection conn) {
        String sql = "SELECT * FROM truces WHERE status = 'ACTIVE'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                List<Truce> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
                return results;
            }
        } catch (SQLException e) {
            throw new RuntimeException("TrucesDao.findActive failed", e);
        }
    }

    /**
     * Updates the status and ended_at timestamp of a truce.
     *
     * @param conn    the JDBC connection (caller manages transaction)
     * @param id      the truce ID
     * @param status  the new status value
     * @param endedAt the end timestamp (nullable, set when truce ends)
     */
    public void updateStatus(Connection conn, int id, String status, Long endedAt) {
        String sql = "UPDATE truces SET status = ?, ended_at = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            setNullableLong(ps, 2, endedAt);
            ps.setInt(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("TrucesDao.updateStatus failed", e);
        }
    }

    private Truce mapRow(ResultSet rs) throws SQLException {
        long endedAtRaw = rs.getLong("ended_at");
        Long endedAt = rs.wasNull() ? null : endedAtRaw;
        return new Truce(
                rs.getInt("id"),
                rs.getString("proposing_faction"),
                rs.getString("target_faction"),
                rs.getInt("duration_minutes"),
                rs.getLong("started_at"),
                endedAt,
                rs.getString("status")
        );
    }

    private static void setNullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value != null) {
            ps.setLong(index, value);
        } else {
            ps.setNull(index, Types.BIGINT);
        }
    }
}
