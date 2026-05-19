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
 * DAO for the {@code subdivisions} and {@code subdivision_members} tables.
 * <p>
 * Each method accepts a {@link Connection} parameter — no connection state is stored.
 * Pure JDBC, no Minecraft dependencies.
 * <p>
 * Requirements: 18.2, 18.4
 */
public final class SubdivisionsDao {

    public record Subdivision(
            int id,
            String faction,
            String name,
            String createdBy,
            long createdAt
    ) {}

    public record SubdivisionMember(
            int subdivisionId,
            String playerUuid,
            long joinedAt
    ) {}

    /**
     * Inserts a new subdivision and returns the generated id.
     */
    public int insert(Connection conn, String faction, String name, String createdBy, long createdAt) {
        String sql = "INSERT INTO subdivisions (faction, name, created_by, created_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, faction);
            ps.setString(2, name);
            ps.setString(3, createdBy);
            ps.setLong(4, createdAt);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
                throw new SQLException("No generated key returned for subdivision insert");
            }
        } catch (SQLException e) {
            throw new RuntimeException("SubdivisionsDao.insert failed", e);
        }
    }

    public Optional<Subdivision> findById(Connection conn, int id) {
        String sql = "SELECT * FROM subdivisions WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapSubdivision(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("SubdivisionsDao.findById failed", e);
        }
    }

    public Optional<Subdivision> findByFactionAndName(Connection conn, String faction, String name) {
        String sql = "SELECT * FROM subdivisions WHERE faction = ? AND name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, faction);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapSubdivision(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("SubdivisionsDao.findByFactionAndName failed", e);
        }
    }

    public List<Subdivision> findByFaction(Connection conn, String faction) {
        String sql = "SELECT * FROM subdivisions WHERE faction = ?";
        List<Subdivision> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, faction);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapSubdivision(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("SubdivisionsDao.findByFaction failed", e);
        }
        return result;
    }

    public void delete(Connection conn, int id) {
        String sql = "DELETE FROM subdivisions WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("SubdivisionsDao.delete failed", e);
        }
    }

    // --- Subdivision Members ---

    public void addMember(Connection conn, int subdivisionId, String playerUuid, long joinedAt) {
        String sql = "INSERT INTO subdivision_members (subdivision_id, player_uuid, joined_at) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, subdivisionId);
            ps.setString(2, playerUuid);
            ps.setLong(3, joinedAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("SubdivisionsDao.addMember failed", e);
        }
    }

    public void removeMember(Connection conn, int subdivisionId, String playerUuid) {
        String sql = "DELETE FROM subdivision_members WHERE subdivision_id = ? AND player_uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, subdivisionId);
            ps.setString(2, playerUuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("SubdivisionsDao.removeMember failed", e);
        }
    }

    public List<SubdivisionMember> findMembers(Connection conn, int subdivisionId) {
        String sql = "SELECT * FROM subdivision_members WHERE subdivision_id = ?";
        List<SubdivisionMember> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, subdivisionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new SubdivisionMember(
                            rs.getInt("subdivision_id"),
                            rs.getString("player_uuid"),
                            rs.getLong("joined_at")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("SubdivisionsDao.findMembers failed", e);
        }
        return result;
    }

    private Subdivision mapSubdivision(ResultSet rs) throws SQLException {
        return new Subdivision(
                rs.getInt("id"),
                rs.getString("faction"),
                rs.getString("name"),
                rs.getString("created_by"),
                rs.getLong("created_at")
        );
    }
}
