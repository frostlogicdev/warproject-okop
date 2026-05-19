package com.frostlogic.warproject.persistence.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * DAO for the {@code mutes} table.
 * <p>
 * Each method accepts a {@link Connection} parameter — no connection state is stored.
 * Pure JDBC, no Minecraft dependencies.
 * <p>
 * Requirements: 18.2, 18.4
 */
public final class MutesDao {

    public record Mute(
            String uuid,
            String reason,
            String mutedBy,
            long mutedAt,
            long expiresAt
    ) {}

    /**
     * Inserts or replaces a mute (upsert semantics).
     * If a mute already exists for this UUID, it is replaced.
     */
    public void upsert(Connection conn, Mute mute) {
        String sql = """
                INSERT INTO mutes (uuid, reason, muted_by, muted_at, expires_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET reason = excluded.reason,
                    muted_by = excluded.muted_by, muted_at = excluded.muted_at,
                    expires_at = excluded.expires_at
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, mute.uuid());
            ps.setString(2, mute.reason());
            ps.setString(3, mute.mutedBy());
            ps.setLong(4, mute.mutedAt());
            ps.setLong(5, mute.expiresAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("MutesDao.upsert failed", e);
        }
    }

    public Optional<Mute> findByUuid(Connection conn, String uuid) {
        String sql = "SELECT * FROM mutes WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("MutesDao.findByUuid failed", e);
        }
    }

    /**
     * Finds an active (non-expired) mute for the given UUID.
     */
    public Optional<Mute> findActive(Connection conn, String uuid, long nowEpochMillis) {
        String sql = "SELECT * FROM mutes WHERE uuid = ? AND expires_at > ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setLong(2, nowEpochMillis);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("MutesDao.findActive failed", e);
        }
    }

    public void delete(Connection conn, String uuid) {
        String sql = "DELETE FROM mutes WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("MutesDao.delete failed", e);
        }
    }

    private Mute mapRow(ResultSet rs) throws SQLException {
        return new Mute(
                rs.getString("uuid"),
                rs.getString("reason"),
                rs.getString("muted_by"),
                rs.getLong("muted_at"),
                rs.getLong("expires_at")
        );
    }
}
