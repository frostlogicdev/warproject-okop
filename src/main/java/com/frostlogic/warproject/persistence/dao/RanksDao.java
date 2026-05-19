package com.frostlogic.warproject.persistence.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO for the {@code ranks} table (reference table for faction ranks).
 * <p>
 * Composite primary key: (faction, rank).
 * Each method accepts a {@link Connection} parameter — no connection state is stored.
 * Pure JDBC, no Minecraft dependencies.
 * <p>
 * Requirements: 18.2, 18.4
 */
public final class RanksDao {

    public record Rank(
            String faction,
            String role,
            String rank,
            int ord
    ) {}

    public void insert(Connection conn, Rank rank) {
        String sql = "INSERT INTO ranks (faction, role, rank, ord) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rank.faction());
            ps.setString(2, rank.role());
            ps.setString(3, rank.rank());
            ps.setInt(4, rank.ord());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("RanksDao.insert failed", e);
        }
    }

    /**
     * Inserts or replaces a rank (upsert semantics).
     */
    public void upsert(Connection conn, Rank rank) {
        String sql = """
                INSERT INTO ranks (faction, role, rank, ord)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(faction, rank) DO UPDATE SET role = excluded.role, ord = excluded.ord
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rank.faction());
            ps.setString(2, rank.role());
            ps.setString(3, rank.rank());
            ps.setInt(4, rank.ord());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("RanksDao.upsert failed", e);
        }
    }

    public Optional<Rank> findByKey(Connection conn, String faction, String rank) {
        String sql = "SELECT * FROM ranks WHERE faction = ? AND rank = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, faction);
            ps.setString(2, rank);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("RanksDao.findByKey failed", e);
        }
    }

    public List<Rank> findByFaction(Connection conn, String faction) {
        String sql = "SELECT * FROM ranks WHERE faction = ? ORDER BY ord ASC";
        List<Rank> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, faction);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("RanksDao.findByFaction failed", e);
        }
        return result;
    }

    public List<Rank> findAll(Connection conn) {
        String sql = "SELECT * FROM ranks ORDER BY faction, ord ASC";
        List<Rank> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("RanksDao.findAll failed", e);
        }
        return result;
    }

    public void delete(Connection conn, String faction, String rank) {
        String sql = "DELETE FROM ranks WHERE faction = ? AND rank = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, faction);
            ps.setString(2, rank);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("RanksDao.delete failed", e);
        }
    }

    /**
     * Deletes all ranks for a given faction (useful for reload from config).
     */
    public void deleteByFaction(Connection conn, String faction) {
        String sql = "DELETE FROM ranks WHERE faction = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, faction);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("RanksDao.deleteByFaction failed", e);
        }
    }

    private Rank mapRow(ResultSet rs) throws SQLException {
        return new Rank(
                rs.getString("faction"),
                rs.getString("role"),
                rs.getString("rank"),
                rs.getInt("ord")
        );
    }
}
