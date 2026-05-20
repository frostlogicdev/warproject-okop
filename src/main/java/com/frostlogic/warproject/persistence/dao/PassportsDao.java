package com.frostlogic.warproject.persistence.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO for the {@code passports} table.
 * <p>
 * Each method accepts a {@link Connection} parameter — no connection state is stored.
 * Pure JDBC, no Minecraft dependencies.
 * <p>
 * Requirements: 18.2, 18.4
 */
public final class PassportsDao {

    public record Passport(
            String passportId,
            String ownerUuid,
            String faction,
            String rpName,
            String rpSurname,
            String dateOfBirth,
            long signatureSeed,
            String status,
            Long acceptedAt,
            String acceptedBy,
            String capturedByUuid,
            Long capturedAt,
            boolean trophy,
            long createdAt
    ) {}

    public void insert(Connection conn, Passport passport) {
        String sql = """
                INSERT INTO passports (passport_id, owner_uuid, faction, rp_name, rp_surname,
                    date_of_birth, signature_seed, status, accepted_at, accepted_by,
                    captured_by_uuid, captured_at, trophy, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, passport.passportId());
            ps.setString(2, passport.ownerUuid());
            ps.setString(3, passport.faction());
            ps.setString(4, passport.rpName());
            ps.setString(5, passport.rpSurname());
            ps.setString(6, passport.dateOfBirth());
            ps.setLong(7, passport.signatureSeed());
            ps.setString(8, passport.status());
            setNullableLong(ps, 9, passport.acceptedAt());
            setNullableString(ps, 10, passport.acceptedBy());
            setNullableString(ps, 11, passport.capturedByUuid());
            setNullableLong(ps, 12, passport.capturedAt());
            ps.setInt(13, passport.trophy() ? 1 : 0);
            ps.setLong(14, passport.createdAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("PassportsDao.insert failed", e);
        }
    }

    public Optional<Passport> findById(Connection conn, String passportId) {
        String sql = "SELECT * FROM passports WHERE passport_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, passportId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("PassportsDao.findById failed", e);
        }
    }

    public Optional<Passport> findByOwnerUuid(Connection conn, String ownerUuid) {
        String sql = "SELECT * FROM passports WHERE owner_uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ownerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("PassportsDao.findByOwnerUuid failed", e);
        }
    }

    public boolean exists(Connection conn, String passportId) {
        String sql = "SELECT 1 FROM passports WHERE passport_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, passportId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("PassportsDao.exists failed", e);
        }
    }

    public void update(Connection conn, Passport passport) {
        String sql = """
                UPDATE passports SET owner_uuid = ?, faction = ?, rp_name = ?, rp_surname = ?,
                    date_of_birth = ?, signature_seed = ?, status = ?, accepted_at = ?,
                    accepted_by = ?, captured_by_uuid = ?, captured_at = ?, trophy = ?, created_at = ?
                WHERE passport_id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, passport.ownerUuid());
            ps.setString(2, passport.faction());
            ps.setString(3, passport.rpName());
            ps.setString(4, passport.rpSurname());
            ps.setString(5, passport.dateOfBirth());
            ps.setLong(6, passport.signatureSeed());
            ps.setString(7, passport.status());
            setNullableLong(ps, 8, passport.acceptedAt());
            setNullableString(ps, 9, passport.acceptedBy());
            setNullableString(ps, 10, passport.capturedByUuid());
            setNullableLong(ps, 11, passport.capturedAt());
            ps.setInt(12, passport.trophy() ? 1 : 0);
            ps.setLong(13, passport.createdAt());
            ps.setString(14, passport.passportId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("PassportsDao.update failed", e);
        }
    }

    /**
     * Updates the capture state of a passport (clears or sets capture).
     * <p>
     * Legacy 3-field signature. Delegates to {@link #updateCaptureState(Connection, String, String, boolean, Long)}
     * with {@code capturedAt = null} which is correct for the ransom/clear path. Use the
     * 4-field variant when applying a fresh capture so the 30-min timeout has a reference point.
     */
    public void updateCaptureState(Connection conn, String passportId, String capturedByUuid, boolean trophy) {
        updateCaptureState(conn, passportId, capturedByUuid, trophy, null);
    }

    /**
     * Updates the full capture state of a passport including the captured_at timestamp.
     *
     * @param conn            JDBC connection (caller manages transaction)
     * @param passportId      passport ID
     * @param capturedByUuid  captor UUID, or {@code null} to clear
     * @param trophy          trophy flag
     * @param capturedAt      UNIX-ms moment of capture, or {@code null} to clear
     */
    public void updateCaptureState(Connection conn, String passportId, String capturedByUuid,
                                   boolean trophy, Long capturedAt) {
        String sql = "UPDATE passports SET captured_by_uuid = ?, captured_at = ?, trophy = ? WHERE passport_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setNullableString(ps, 1, capturedByUuid);
            setNullableLong(ps, 2, capturedAt);
            ps.setInt(3, trophy ? 1 : 0);
            ps.setString(4, passportId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("PassportsDao.updateCaptureState failed", e);
        }
    }

    /**
     * Updates the acceptance fields of a passport (status, accepted_at, accepted_by).
     * Used by the AcceptCommandHandler's atomic transaction.
     */
    public void updateAcceptance(Connection conn, String passportId, String status,
                                 long acceptedAt, String acceptedBy) {
        String sql = "UPDATE passports SET status = ?, accepted_at = ?, accepted_by = ? WHERE passport_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setLong(2, acceptedAt);
            ps.setString(3, acceptedBy);
            ps.setString(4, passportId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("PassportsDao.updateAcceptance failed", e);
        }
    }

    public void delete(Connection conn, String passportId) {
        String sql = "DELETE FROM passports WHERE passport_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, passportId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("PassportsDao.delete failed", e);
        }
    }

    /**
     * Returns every passport currently marked as captured (captured_by_uuid IS NOT NULL).
     * Used by CaptivityTimeoutService to iterate all live captures each tick.
     */
    public List<Passport> findAllCaptured(Connection conn) {
        String sql = "SELECT * FROM passports WHERE captured_by_uuid IS NOT NULL";
        List<Passport> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("PassportsDao.findAllCaptured failed", e);
        }
        return out;
    }

    /**
     * Returns every passport whose capture is older than the given threshold (captured_at &lt; thresholdMs).
     * Used by CaptivityTimeoutService for the 30-min auto-release sweep.
     *
     * Rows with captured_at IS NULL are excluded — either they were never captured, or they are
     * captures from before the V3 migration that we intentionally don't auto-release without
     * a recorded start time (the next capture will populate captured_at correctly).
     */
    public List<Passport> findCapturedBefore(Connection conn, long thresholdMs) {
        String sql = "SELECT * FROM passports WHERE captured_by_uuid IS NOT NULL AND captured_at IS NOT NULL AND captured_at < ?";
        List<Passport> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, thresholdMs);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("PassportsDao.findCapturedBefore failed", e);
        }
        return out;
    }

    private Passport mapRow(ResultSet rs) throws SQLException {
        long acceptedAtRaw = rs.getLong("accepted_at");
        Long acceptedAt = rs.wasNull() ? null : acceptedAtRaw;
        long capturedAtRaw = rs.getLong("captured_at");
        Long capturedAt = rs.wasNull() ? null : capturedAtRaw;
        return new Passport(
                rs.getString("passport_id"),
                rs.getString("owner_uuid"),
                rs.getString("faction"),
                rs.getString("rp_name"),
                rs.getString("rp_surname"),
                rs.getString("date_of_birth"),
                rs.getLong("signature_seed"),
                rs.getString("status"),
                acceptedAt,
                rs.getString("accepted_by"),
                rs.getString("captured_by_uuid"),
                capturedAt,
                rs.getInt("trophy") != 0,
                rs.getLong("created_at")
        );
    }

    private static void setNullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value != null) {
            ps.setString(index, value);
        } else {
            ps.setNull(index, java.sql.Types.VARCHAR);
        }
    }

    private static void setNullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value != null) {
            ps.setLong(index, value);
        } else {
            ps.setNull(index, java.sql.Types.BIGINT);
        }
    }
}
