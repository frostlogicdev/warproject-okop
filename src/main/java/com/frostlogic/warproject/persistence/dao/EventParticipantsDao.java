package com.frostlogic.warproject.persistence.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for the {@code event_participants} table.
 * <p>
 * Each method accepts a {@link Connection} parameter — no connection state is stored.
 * Pure JDBC, no Minecraft dependencies.
 * <p>
 * Requirements: 17.1, 17.2, 9.5
 */
public final class EventParticipantsDao {

    public record EventParticipant(
            int eventId,
            String playerUuid,
            long joinedAt
    ) {}

    /**
     * Inserts a participant record for an event.
     */
    public void insert(Connection conn, EventParticipant participant) {
        String sql = "INSERT INTO event_participants (event_id, player_uuid, joined_at) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, participant.eventId());
            ps.setString(2, participant.playerUuid());
            ps.setLong(3, participant.joinedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("EventParticipantsDao.insert failed", e);
        }
    }

    /**
     * Finds all participants for a given event.
     */
    public List<EventParticipant> findByEventId(Connection conn, int eventId) {
        String sql = "SELECT * FROM event_participants WHERE event_id = ?";
        List<EventParticipant> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("EventParticipantsDao.findByEventId failed", e);
        }
        return result;
    }

    /**
     * Counts the number of participants for a given event.
     */
    public int countByEventId(Connection conn, int eventId) {
        String sql = "SELECT COUNT(*) FROM event_participants WHERE event_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("EventParticipantsDao.countByEventId failed", e);
        }
    }

    private EventParticipant mapRow(ResultSet rs) throws SQLException {
        return new EventParticipant(
                rs.getInt("event_id"),
                rs.getString("player_uuid"),
                rs.getLong("joined_at")
        );
    }
}
