package com.frostlogic.warproject.persistence.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO for the {@code audit_log} table.
 * <p>
 * Each method accepts a {@link Connection} parameter — no connection state is stored.
 * Pure JDBC, no Minecraft dependencies.
 * <p>
 * Requirements: 18.2, 18.4
 */
public final class AuditLogDao {

    public record AuditEntry(
            long id,
            long tsUtc,
            String actorUuid,
            String actorName,
            String targetUuid,
            String targetName,
            String action,
            String reason,
            String extraJson
    ) {}

    /**
     * Inserts an audit log entry and returns the generated id.
     */
    public long insert(Connection conn, long tsUtc, String actorUuid, String actorName,
                       String targetUuid, String targetName, String action,
                       String reason, String extraJson) {
        String sql = """
                INSERT INTO audit_log (ts_utc, actor_uuid, actor_name, target_uuid, target_name, action, reason, extra_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, tsUtc);
            setNullableString(ps, 2, actorUuid);
            setNullableString(ps, 3, actorName);
            setNullableString(ps, 4, targetUuid);
            setNullableString(ps, 5, targetName);
            ps.setString(6, action);
            setNullableString(ps, 7, reason);
            setNullableString(ps, 8, extraJson);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
                throw new SQLException("No generated key returned for audit_log insert");
            }
        } catch (SQLException e) {
            throw new RuntimeException("AuditLogDao.insert failed", e);
        }
    }

    public Optional<AuditEntry> findById(Connection conn, long id) {
        String sql = "SELECT * FROM audit_log WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("AuditLogDao.findById failed", e);
        }
    }

    public List<AuditEntry> findByTargetUuid(Connection conn, String targetUuid, int limit, int offset) {
        // ORDER BY ts_utc DESC, id DESC — the {@code id} tiebreaker is required
        // because rows inserted within the same millisecond (common on Windows where
        // System.currentTimeMillis() resolution is coarse) would otherwise come back
        // in an arbitrary, SQLite-internal storage order. Since {@code id} is an
        // AUTOINCREMENT primary key, it is monotonically increasing per insert and
        // restores deterministic insertion order on ties.
        String sql = "SELECT * FROM audit_log WHERE target_uuid = ? ORDER BY ts_utc DESC, id DESC LIMIT ? OFFSET ?";
        return queryList(conn, sql, targetUuid, limit, offset);
    }

    public List<AuditEntry> findByActorUuid(Connection conn, String actorUuid, int limit, int offset) {
        // See findByTargetUuid for the rationale on the {@code id DESC} tiebreaker.
        String sql = "SELECT * FROM audit_log WHERE actor_uuid = ? ORDER BY ts_utc DESC, id DESC LIMIT ? OFFSET ?";
        return queryList(conn, sql, actorUuid, limit, offset);
    }

    public void delete(Connection conn, long id) {
        String sql = "DELETE FROM audit_log WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("AuditLogDao.delete failed", e);
        }
    }

    private List<AuditEntry> queryList(Connection conn, String sql, String uuid, int limit, int offset) {
        List<AuditEntry> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("AuditLogDao query failed", e);
        }
        return result;
    }

    private AuditEntry mapRow(ResultSet rs) throws SQLException {
        return new AuditEntry(
                rs.getLong("id"),
                rs.getLong("ts_utc"),
                rs.getString("actor_uuid"),
                rs.getString("actor_name"),
                rs.getString("target_uuid"),
                rs.getString("target_name"),
                rs.getString("action"),
                rs.getString("reason"),
                rs.getString("extra_json")
        );
    }

    private static void setNullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value != null) {
            ps.setString(index, value);
        } else {
            ps.setNull(index, java.sql.Types.VARCHAR);
        }
    }
}
