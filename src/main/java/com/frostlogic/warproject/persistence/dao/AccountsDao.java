package com.frostlogic.warproject.persistence.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * DAO for the {@code accounts} table (WGuard credentials).
 * <p>
 * Each method accepts a {@link Connection} parameter — no connection state is stored.
 * Pure JDBC, no Minecraft dependencies.
 * <p>
 * Requirements: 18.2, 18.4
 */
public final class AccountsDao {

    public record Account(
            String uuid,
            String passwordHash,
            long registeredAt,
            Long lastLoginAt,
            int failedLoginCnt,
            long cooldownUntil
    ) {}

    public void insert(Connection conn, Account account) {
        String sql = "INSERT INTO accounts (uuid, password_hash, registered_at, last_login_at, failed_login_cnt, cooldown_until) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, account.uuid());
            ps.setString(2, account.passwordHash());
            ps.setLong(3, account.registeredAt());
            if (account.lastLoginAt() != null) {
                ps.setLong(4, account.lastLoginAt());
            } else {
                ps.setNull(4, java.sql.Types.BIGINT);
            }
            ps.setInt(5, account.failedLoginCnt());
            ps.setLong(6, account.cooldownUntil());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("AccountsDao.insert failed", e);
        }
    }

    /**
     * Idempotently ensures an {@code accounts} row exists for the given UUID.
     * Uses {@code INSERT OR IGNORE}, so an existing account is left untouched.
     * <p>
     * The placeholder password hash is deliberately invalid (does not match
     * any BCrypt prefix), so it cannot accidentally authenticate. Defensive
     * shim for paths that can reach persistence without going through the
     * normal auth pipeline (e.g. operators bypassing registration).
     *
     * @return {@code true} if a new row was actually inserted, {@code false} otherwise.
     */
    public boolean ensureExists(Connection conn, String uuid, long now) {
        String sql = "INSERT OR IGNORE INTO accounts (uuid, password_hash, registered_at, failed_login_cnt, cooldown_until) VALUES (?, 'OP_NO_PASSWORD', ?, 0, 0)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setLong(2, now);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("AccountsDao.ensureExists failed", e);
        }
    }

    public Optional<Account> findByUuid(Connection conn, String uuid) {
        String sql = "SELECT uuid, password_hash, registered_at, last_login_at, failed_login_cnt, cooldown_until FROM accounts WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("AccountsDao.findByUuid failed", e);
        }
    }

    public void update(Connection conn, Account account) {
        String sql = "UPDATE accounts SET password_hash = ?, registered_at = ?, last_login_at = ?, failed_login_cnt = ?, cooldown_until = ? WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, account.passwordHash());
            ps.setLong(2, account.registeredAt());
            if (account.lastLoginAt() != null) {
                ps.setLong(3, account.lastLoginAt());
            } else {
                ps.setNull(3, java.sql.Types.BIGINT);
            }
            ps.setInt(4, account.failedLoginCnt());
            ps.setLong(5, account.cooldownUntil());
            ps.setString(6, account.uuid());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("AccountsDao.update failed", e);
        }
    }

    public void delete(Connection conn, String uuid) {
        String sql = "DELETE FROM accounts WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("AccountsDao.delete failed", e);
        }
    }

    private Account mapRow(ResultSet rs) throws SQLException {
        long lastLogin = rs.getLong("last_login_at");
        Long lastLoginAt = rs.wasNull() ? null : lastLogin;
        return new Account(
                rs.getString("uuid"),
                rs.getString("password_hash"),
                rs.getLong("registered_at"),
                lastLoginAt,
                rs.getInt("failed_login_cnt"),
                rs.getLong("cooldown_until")
        );
    }
}
