package com.frostlogic.warproject.persistence.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
            boolean trophy,
            long createdAt
    ) {}

    public void insert(Connection conn, Passport passport) {
        String sql = """
                INSERT INTO passports (passport_id, owner_uuid, faction, rp_name, rp_surname,
                    date_of_birth, signature_seed, status, accepted_at, accepted_by,
                    captured_by_uuid, trophy, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
            ps.setInt(12, passport.trophy() ? 1 : 0);
            ps.setLong(13, passport.createdAt());
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
                    accepted_by = ?, captured_by_uuid = ?, trophy = ?, created_at = ?
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
            ps.setInt(11, passport.trophy() ? 1 : 0);
            ps.setLong(12, passport.createdAt());
            ps.setString(13, passport.passportId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("PassportsDao.update failed", e);
        }
    }

    /**
     * Updates the capture state of a passport (sets captured_by_uuid and trophy flag).
     *
     * @param conn            the JDBC connection (caller manages transaction)
     * @param passportId      the passport ID
     * @param capturedByUuid  the UUID of the captor (null to clear capture)
     * @param trophy          whether the passport is marked as a trophy
     */
    public void updateCaptureState(Connection conn, String passportId, String capturedByUuid, boolean trophy) {
        String sql = "UPDATE passports SET captured_by_uuid = ?, trophy = ? WHERE passport_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setNullableString(ps, 1, capturedByUuid);
            ps.setInt(2, trophy ? 1 : 0);
            ps.setString(3, passportId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("PassportsDao.updateCaptureState failed", e);
        }
    }

    /**
     * Updates the acceptance fields of a passport (status, accepted_at, accepted_by).
     * Used by the AcceptCommandHandler's atomic transaction.
     *
     * @param conn        the JDBC connection (caller manages transaction)
     * @param passportId  the passport ID
     * @param status      the new status value (typically {@code PlayerState.ACCEPTED.getSerializedName()})
     * @param acceptedAt  UNIX timestamp (ms) of acceptance
     * @param acceptedBy  display name of the initiator
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

    private Passport mapRow(ResultSet rs) throws SQLException {
        long acceptedAtRaw = rs.getLong("accepted_at");
        Long acceptedAt = rs.wasNull() ? null : acceptedAtRaw;
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
