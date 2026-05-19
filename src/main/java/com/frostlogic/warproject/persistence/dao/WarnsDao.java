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
 * DAO for the {@code warns} table.
 * <p>
 * Each method accepts a {@link Connection} parameter — no connection state is stored.
 * Pure JDBC, no Minecraft dependencies.
 * <p>
 * Requirements: 18.2, 18.4
 */
public final class WarnsDao {

    public record Warn(
            int id,
            String uuid,
            String reason,
            String issuedBy,
            long issuedAt
    ) {}

    /**
     * Inserts a new warn and returns the generated id.
     */
    public int insert(Connection conn, String uuid, String reason, String issuedBy, long issuedAt) {
        String sql = "INSERT INTO warns (uuid, reason, issued_by, issued_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, uuid);
            ps.setString(2, reason);
            ps.setString(3, issuedBy);
            ps.setLong(4, issuedAt);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
                throw new SQLException("No generated key returned for warn insert");
            }
        } catch (SQLException e) {
            throw new RuntimeException("WarnsDao.insert failed", e);
        }
    }

    public Optional<Warn> findById(Connection conn, int id) {
        String sql = "SELECT * FROM warns WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("WarnsDao.findById failed", e);
        }
    }

    public List<Warn> findByUuid(Connection conn, String uuid) {
        String sql = "SELECT * FROM warns WHERE uuid = ? ORDER BY issued_at DESC";
        List<Warn> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("WarnsDao.findByUuid failed", e);
        }
        return result;
    }

    public int countByUuid(Connection conn, String uuid) {
        String sql = "SELECT COUNT(*) FROM warns WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("WarnsDao.countByUuid failed", e);
        }
    }

    public void delete(Connection conn, int id) {
        String sql = "DELETE FROM warns WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("WarnsDao.delete failed", e);
        }
    }

    private Warn mapRow(ResultSet rs) throws SQLException {
        return new Warn(
                rs.getInt("id"),
                rs.getString("uuid"),
                rs.getString("reason"),
                rs.getString("issued_by"),
                rs.getLong("issued_at")
        );
    }
}
