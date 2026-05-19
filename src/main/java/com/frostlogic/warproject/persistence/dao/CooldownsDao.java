package com.frostlogic.warproject.persistence.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * DAO for the {@code cooldowns} table.
 * <p>
 * Composite primary key: (uuid, cd_type).
 * Each method accepts a {@link Connection} parameter — no connection state is stored.
 * Pure JDBC, no Minecraft dependencies.
 * <p>
 * Requirements: 18.2, 18.4
 */
public final class CooldownsDao {

    public record Cooldown(
            String uuid,
            String cdType,
            long expiresAt
    ) {}

    /**
     * Inserts or replaces a cooldown (upsert semantics).
     */
    public void upsert(Connection conn, String uuid, String cdType, long expiresAt) {
        String sql = """
                INSERT INTO cooldowns (uuid, cd_type, expires_at)
                VALUES (?, ?, ?)
                ON CONFLICT(uuid, cd_type) DO UPDATE SET expires_at = excluded.expires_at
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, cdType);
            ps.setLong(3, expiresAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("CooldownsDao.upsert failed", e);
        }
    }

    public Optional<Cooldown> findByKey(Connection conn, String uuid, String cdType) {
        String sql = "SELECT * FROM cooldowns WHERE uuid = ? AND cd_type = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, cdType);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("CooldownsDao.findByKey failed", e);
        }
    }

    /**
     * Checks if a cooldown is currently active (not expired).
     */
    public boolean isActive(Connection conn, String uuid, String cdType, long nowEpochMillis) {
        String sql = "SELECT 1 FROM cooldowns WHERE uuid = ? AND cd_type = ? AND expires_at > ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, cdType);
            ps.setLong(3, nowEpochMillis);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("CooldownsDao.isActive failed", e);
        }
    }

    public void delete(Connection conn, String uuid, String cdType) {
        String sql = "DELETE FROM cooldowns WHERE uuid = ? AND cd_type = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, cdType);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("CooldownsDao.delete failed", e);
        }
    }

    /**
     * Deletes all expired cooldowns for cleanup.
     */
    public int deleteExpired(Connection conn, long nowEpochMillis) {
        String sql = "DELETE FROM cooldowns WHERE expires_at <= ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, nowEpochMillis);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("CooldownsDao.deleteExpired failed", e);
        }
    }

    private Cooldown mapRow(ResultSet rs) throws SQLException {
        return new Cooldown(
                rs.getString("uuid"),
                rs.getString("cd_type"),
                rs.getLong("expires_at")
        );
    }
}
