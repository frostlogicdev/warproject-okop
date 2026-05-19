package com.frostlogic.warproject.persistence.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * DAO for the {@code players} table (game state per player).
 * <p>
 * Each method accepts a {@link Connection} parameter — no connection state is stored.
 * Pure JDBC, no Minecraft dependencies.
 * <p>
 * Requirements: 18.2, 18.4
 */
public final class PlayersDao {

    public record Player(
            String uuid,
            String faction,
            String role,
            String rank,
            String status,
            String rpName,
            String rpSurname,
            boolean collaborator,
            String collabReason,
            boolean captured,
            int enemyRegionTicks,
            long joinedAt,
            Long acceptedAt,
            String acceptedByUuid,
            String acceptedByName,
            Integer subdivisionId
    ) {}

    public void insert(Connection conn, Player player) {
        String sql = """
                INSERT INTO players (uuid, faction, role, rank, status, rp_name, rp_surname,
                    collaborator, collab_reason, captured, enemy_region_ticks, joined_at,
                    accepted_at, accepted_by_uuid, accepted_by_name, subdivision_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, player.uuid());
            setNullableString(ps, 2, player.faction());
            ps.setString(3, player.role());
            setNullableString(ps, 4, player.rank());
            ps.setString(5, player.status());
            setNullableString(ps, 6, player.rpName());
            setNullableString(ps, 7, player.rpSurname());
            ps.setInt(8, player.collaborator() ? 1 : 0);
            setNullableString(ps, 9, player.collabReason());
            ps.setInt(10, player.captured() ? 1 : 0);
            ps.setInt(11, player.enemyRegionTicks());
            ps.setLong(12, player.joinedAt());
            setNullableLong(ps, 13, player.acceptedAt());
            setNullableString(ps, 14, player.acceptedByUuid());
            setNullableString(ps, 15, player.acceptedByName());
            setNullableInt(ps, 16, player.subdivisionId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("PlayersDao.insert failed", e);
        }
    }

    public Optional<Player> findByUuid(Connection conn, String uuid) {
        String sql = "SELECT * FROM players WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("PlayersDao.findByUuid failed", e);
        }
    }

    public void update(Connection conn, Player player) {
        String sql = """
                UPDATE players SET faction = ?, role = ?, rank = ?, status = ?, rp_name = ?,
                    rp_surname = ?, collaborator = ?, collab_reason = ?, captured = ?,
                    enemy_region_ticks = ?, joined_at = ?, accepted_at = ?, accepted_by_uuid = ?,
                    accepted_by_name = ?, subdivision_id = ?
                WHERE uuid = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setNullableString(ps, 1, player.faction());
            ps.setString(2, player.role());
            setNullableString(ps, 3, player.rank());
            ps.setString(4, player.status());
            setNullableString(ps, 5, player.rpName());
            setNullableString(ps, 6, player.rpSurname());
            ps.setInt(7, player.collaborator() ? 1 : 0);
            setNullableString(ps, 8, player.collabReason());
            ps.setInt(9, player.captured() ? 1 : 0);
            ps.setInt(10, player.enemyRegionTicks());
            ps.setLong(11, player.joinedAt());
            setNullableLong(ps, 12, player.acceptedAt());
            setNullableString(ps, 13, player.acceptedByUuid());
            setNullableString(ps, 14, player.acceptedByName());
            setNullableInt(ps, 15, player.subdivisionId());
            ps.setString(16, player.uuid());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("PlayersDao.update failed", e);
        }
    }

    public void delete(Connection conn, String uuid) {
        String sql = "DELETE FROM players WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("PlayersDao.delete failed", e);
        }
    }

    /**
     * Updates only the {@code status} column for the given player.
     *
     * @param conn   the JDBC connection (caller manages transaction)
     * @param uuid   the player UUID
     * @param status the new status value (serialized name of {@code PlayerState})
     */
    public void updateStatus(Connection conn, String uuid, String status) {
        String sql = "UPDATE players SET status = ? WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, uuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("PlayersDao.updateStatus failed", e);
        }
    }

    /**
     * Sets the faction and status for the given player in a single UPDATE.
     * Used during faction choice to atomically assign faction + CANDIDATE status.
     *
     * @param conn    the JDBC connection (caller manages transaction)
     * @param uuid    the player UUID
     * @param faction the faction serialized name (e.g. "ZARNAVIA", "CHERNOGRYAD")
     * @param status  the new status value (e.g. "candidate")
     */
    public void setFactionAndStatus(Connection conn, String uuid, String faction, String status) {
        String sql = "UPDATE players SET faction = ?, status = ? WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, faction);
            ps.setString(2, status);
            ps.setString(3, uuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("PlayersDao.setFactionAndStatus failed", e);
        }
    }

    /**
     * Updates only the {@code captured} column for the given player.
     *
     * @param conn     the JDBC connection (caller manages transaction)
     * @param uuid     the player UUID
     * @param captured whether the player is captured (true = in captivity)
     */
    public void setCaptured(Connection conn, String uuid, boolean captured) {
        String sql = "UPDATE players SET captured = ? WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, captured ? 1 : 0);
            ps.setString(2, uuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("PlayersDao.setCaptured failed", e);
        }
    }

    /**
     * Updates the acceptance fields for the given player in a single UPDATE.
     * Sets {@code status = 'ACCEPTED'} (using the supplied serialized value) plus
     * {@code accepted_at}, {@code accepted_by_uuid}, and {@code accepted_by_name}.
     * Used by the AcceptCommandHandler's atomic transaction.
     *
     * @param conn          the JDBC connection (caller manages transaction)
     * @param uuid          the player UUID being accepted
     * @param status        the new status value (typically {@code PlayerState.ACCEPTED.getSerializedName()})
     * @param acceptedAt    UNIX timestamp (ms) of acceptance
     * @param acceptedByUuid UUID of the initiator
     * @param acceptedByName display name of the initiator
     */
    public void updateAcceptance(Connection conn, String uuid, String status,
                                 long acceptedAt, String acceptedByUuid, String acceptedByName) {
        String sql = "UPDATE players SET status = ?, accepted_at = ?, accepted_by_uuid = ?, accepted_by_name = ? WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setLong(2, acceptedAt);
            ps.setString(3, acceptedByUuid);
            ps.setString(4, acceptedByName);
            ps.setString(5, uuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("PlayersDao.updateAcceptance failed", e);
        }
    }

    /**
     * Sets only the {@code role} column for the given player.
     * <p>
     * Used by the {@code /wp up <name> <role>} command to promote/demote a player's
     * administrative role. Faction and rank are left untouched.
     *
     * @param conn the JDBC connection (caller manages transaction)
     * @param uuid the player UUID
     * @param role the new role serialized name (e.g. {@code Role.COMMANDER.getSerializedName()})
     */
    public void setRole(Connection conn, String uuid, String role) {
        String sql = "UPDATE players SET role = ? WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, role);
            ps.setString(2, uuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("PlayersDao.setRole failed", e);
        }
    }

    /**
     * Sets both {@code role} and {@code faction} for the given player in a single UPDATE.
     * <p>
     * Used by the {@code /wp set comand <player> <faction>} command to atomically
     * appoint a commander for a specific faction.
     *
     * @param conn    the JDBC connection (caller manages transaction)
     * @param uuid    the player UUID
     * @param role    the new role serialized name
     * @param faction the new faction serialized name
     */
    public void setRoleAndFaction(Connection conn, String uuid, String role, String faction) {
        String sql = "UPDATE players SET role = ?, faction = ? WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, role);
            ps.setString(2, faction);
            ps.setString(3, uuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("PlayersDao.setRoleAndFaction failed", e);
        }
    }

    /**
     * Sets the {@code collaborator} flag and {@code collab_reason} for the given player.
     * <p>
     * Used by the {@code /wp collab} and {@code /wp uncollab} commands to mark/unmark
     * a player as a collaborator. When clearing the flag, callers should pass
     * {@code reason = null}.
     *
     * @param conn         the JDBC connection (caller manages transaction)
     * @param uuid         the player UUID
     * @param collaborator true to flag as collaborator, false to clear
     * @param reason       reason text (kept for audit context); may be {@code null}
     */
    public void setCollaborator(Connection conn, String uuid, boolean collaborator, String reason) {
        String sql = "UPDATE players SET collaborator = ?, collab_reason = ? WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, collaborator ? 1 : 0);
            setNullableString(ps, 2, reason);
            ps.setString(3, uuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("PlayersDao.setCollaborator failed", e);
        }
    }

    /**
     * Sets only the {@code subdivision_id} column for the given player.
     * <p>
     * Used by the {@code /wp subdivision invite} and {@code /wp subdivision kick}
     * commands. Pass {@code null} to clear the player's subdivision membership
     * (i.e. on kick or when a subdivision is deleted).
     *
     * @param conn          the JDBC connection (caller manages transaction)
     * @param uuid          the player UUID
     * @param subdivisionId the new subdivision id, or {@code null} to clear
     */
    public void setSubdivisionId(Connection conn, String uuid, Integer subdivisionId) {
        String sql = "UPDATE players SET subdivision_id = ? WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setNullableInt(ps, 1, subdivisionId);
            ps.setString(2, uuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("PlayersDao.setSubdivisionId failed", e);
        }
    }

    /**
     * Clears {@code subdivision_id} for every player whose membership equals the
     * given id. Used when a subdivision is deleted so that the {@code players}
     * row no longer dangles a stale reference (the FK is application-managed —
     * no {@code ON DELETE} action is configured for {@code players.subdivision_id}).
     *
     * @param conn          the JDBC connection (caller manages transaction)
     * @param subdivisionId the subdivision id whose members should be cleared
     */
    public void clearSubdivisionId(Connection conn, int subdivisionId) {
        String sql = "UPDATE players SET subdivision_id = NULL WHERE subdivision_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, subdivisionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("PlayersDao.clearSubdivisionId failed", e);
        }
    }

    /**
     * Sets the {@code rank} and {@code role} columns for the given player.
     * <p>
     * Used by {@code RankService} to atomically promote/demote a player.
     *
     * @param conn the JDBC connection (caller manages transaction)
     * @param uuid the player UUID
     * @param rank the new rank name (e.g. "сержант")
     * @param role the new role (e.g. "SOLDIER", "COMMANDER", "GENERAL")
     */
    public void setRank(Connection conn, String uuid, String rank, String role) {
        String sql = "UPDATE players SET rank = ?, role = ? WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setNullableString(ps, 1, rank);
            ps.setString(2, role);
            ps.setString(3, uuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("PlayersDao.setRank failed", e);
        }
    }

    /**
     * Sets the RP name (first name and surname) for the given player.
     *
     * @param conn    the JDBC connection (caller manages transaction)
     * @param uuid    the player UUID
     * @param name    the RP first name
     * @param surname the RP surname
     */
    public void setRpName(Connection conn, String uuid, String name, String surname) {
        String sql = "UPDATE players SET rp_name = ?, rp_surname = ? WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, surname);
            ps.setString(3, uuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("PlayersDao.setRpName failed", e);
        }
    }

    private Player mapRow(ResultSet rs) throws SQLException {
        long acceptedAtRaw = rs.getLong("accepted_at");
        Long acceptedAt = rs.wasNull() ? null : acceptedAtRaw;
        int subdivIdRaw = rs.getInt("subdivision_id");
        Integer subdivisionId = rs.wasNull() ? null : subdivIdRaw;
        return new Player(
                rs.getString("uuid"),
                rs.getString("faction"),
                rs.getString("role"),
                rs.getString("rank"),
                rs.getString("status"),
                rs.getString("rp_name"),
                rs.getString("rp_surname"),
                rs.getInt("collaborator") != 0,
                rs.getString("collab_reason"),
                rs.getInt("captured") != 0,
                rs.getInt("enemy_region_ticks"),
                rs.getLong("joined_at"),
                acceptedAt,
                rs.getString("accepted_by_uuid"),
                rs.getString("accepted_by_name"),
                subdivisionId
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

    private static void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value != null) {
            ps.setInt(index, value);
        } else {
            ps.setNull(index, java.sql.Types.INTEGER);
        }
    }
}
