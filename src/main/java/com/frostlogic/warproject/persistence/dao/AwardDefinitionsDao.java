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
 * DAO for the {@code award_definitions} table.
 * <p>
 * Each method accepts a {@link Connection} parameter — no connection state is stored.
 * Pure JDBC, no Minecraft dependencies.
 * <p>
 * Requirements: 17.4, 13.5, 13.6
 */
public final class AwardDefinitionsDao {

    public record AwardDefinitionRow(
            int id,
            String name,
            String description,
            String iconId,
            long createdAt
    ) {}

    public int insert(Connection conn, AwardDefinitionRow row) {
        String sql = """
                INSERT INTO award_definitions (name, description, icon_id, created_at)
                VALUES (?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, row.name());
            ps.setString(2, row.description());
            ps.setString(3, row.iconId());
            ps.setLong(4, row.createdAt());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
                return -1;
            }
        } catch (SQLException e) {
            throw new RuntimeException("AwardDefinitionsDao.insert failed", e);
        }
    }

    /**
     * Finds an award definition by name (case-insensitive comparison).
     */
    public Optional<AwardDefinitionRow> findByName(Connection conn, String name) {
        String sql = "SELECT * FROM award_definitions WHERE LOWER(name) = LOWER(?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("AwardDefinitionsDao.findByName failed", e);
        }
    }

    public Optional<AwardDefinitionRow> findById(Connection conn, int id) {
        String sql = "SELECT * FROM award_definitions WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("AwardDefinitionsDao.findById failed", e);
        }
    }

    public List<AwardDefinitionRow> findAll(Connection conn) {
        String sql = "SELECT * FROM award_definitions ORDER BY created_at ASC";
        List<AwardDefinitionRow> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("AwardDefinitionsDao.findAll failed", e);
        }
        return result;
    }

    public void delete(Connection conn, int id) {
        String sql = "DELETE FROM award_definitions WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("AwardDefinitionsDao.delete failed", e);
        }
    }

    private AwardDefinitionRow mapRow(ResultSet rs) throws SQLException {
        return new AwardDefinitionRow(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("icon_id"),
                rs.getLong("created_at")
        );
    }
}
