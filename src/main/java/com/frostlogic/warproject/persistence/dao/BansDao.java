package com.frostlogic.warproject.persistence.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * DAO for the {@code bans} table.
 * <p>
 * Each method accepts a {@link Connection} parameter — no connection state is stored.
 * Pure JDBC, no Minecraft dependencies.
 * <p>
 * Requirements: 18.2, 18.4
 */
public final class BansDao {

    public record Ban(
            String uuid,
            String reason,
            String bannedBy,
            long bannedAt,
            long expiresAt
    ) {}

    /**
     * Inserts or replaces a ban (upsert semantics).
     * If a ban already exists for this UUID, it is replaced.
     */
    public void upsert(Connection conn, Ban ban) {
        String sql = """
                INSERT INTO bans (uuid, reason, banned_by, banned_at, expires_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET reason = excluded.reason,
                    banned_by = excluded.banned_by, banned_at = excluded.banned_at,
                    expires_at = excluded.expires_at
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ban.uuid());
            ps.setString(2, ban.reason());
            ps.setString(3, ban.bannedBy());
            ps.setLong(4, ban.bannedAt());
            ps.setLong(5, ban.expiresAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("BansDao.upsert failed", e);
        }
    }

    public Optional<Ban> findByUuid(Connection conn, String uuid) {
        String sql = "SELECT * FROM bans WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("BansDao.findByUuid failed", e);
        }
    }

    /**
     * Finds an active (non-expired) ban for the given UUID.
     */
    public Optional<Ban> findActive(Connection conn, String uuid, long nowEpochMillis) {
        String sql = "SELECT * FROM bans WHERE uuid = ? AND expires_at > ?";
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
            throw new RuntimeException("BansDao.findActive failed", e);
        }
    }

    public void delete(Connection conn, String uuid) {
        String sql = "DELETE FROM bans WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("BansDao.delete failed", e);
        }
    }

    private Ban mapRow(ResultSet rs) throws SQLException {
        return new Ban(
                rs.getString("uuid"),
                rs.getString("reason"),
                rs.getString("banned_by"),
                rs.getLong("banned_at"),
                rs.getLong("expires_at")
        );
    }
}
