package com.frostlogic.warproject.persistence.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * DAO for the {@code passport_sequence} table.
 * <p>
 * Used to generate unique sequential passport IDs per faction prefix.
 * Composite key: prefix (e.g. "ZRN-", "CHN-").
 * Each method accepts a {@link Connection} parameter — no connection state is stored.
 * Pure JDBC, no Minecraft dependencies.
 * <p>
 * Requirements: 18.2, 18.4
 * Design: §8.6
 */
public final class PassportSequenceDao {

    public record PassportSequence(
            String prefix,
            int lastN
    ) {}

    /**
     * Initializes a prefix with last_n = 0 if it doesn't exist.
     */
    public void initializePrefix(Connection conn, String prefix) {
        String sql = "INSERT OR IGNORE INTO passport_sequence (prefix, last_n) VALUES (?, 0)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, prefix);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("PassportSequenceDao.initializePrefix failed", e);
        }
    }

    /**
     * Atomically increments the sequence for the given prefix and returns the new value.
     * Must be called within a transaction for correctness.
     */
    public int nextValue(Connection conn, String prefix) {
        // Ensure the row exists
        initializePrefix(conn, prefix);
        // Increment and return
        String updateSql = "UPDATE passport_sequence SET last_n = last_n + 1 WHERE prefix = ?";
        try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setString(1, prefix);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("PassportSequenceDao.nextValue update failed", e);
        }
        // Read back the new value
        String selectSql = "SELECT last_n FROM passport_sequence WHERE prefix = ?";
        try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, prefix);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("last_n");
                }
                throw new SQLException("Prefix not found after increment: " + prefix);
            }
        } catch (SQLException e) {
            throw new RuntimeException("PassportSequenceDao.nextValue select failed", e);
        }
    }

    public Optional<PassportSequence> findByPrefix(Connection conn, String prefix) {
        String sql = "SELECT * FROM passport_sequence WHERE prefix = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, prefix);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new PassportSequence(
                            rs.getString("prefix"),
                            rs.getInt("last_n")
                    ));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("PassportSequenceDao.findByPrefix failed", e);
        }
    }

    public void delete(Connection conn, String prefix) {
        String sql = "DELETE FROM passport_sequence WHERE prefix = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, prefix);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("PassportSequenceDao.delete failed", e);
        }
    }
}
