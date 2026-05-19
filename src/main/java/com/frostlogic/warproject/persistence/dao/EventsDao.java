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
 * DAO for the {@code events} table.
 * <p>
 * Each method accepts a {@link Connection} parameter — no connection state is stored.
 * Pure JDBC, no Minecraft dependencies.
 * <p>
 * Requirements: 17.1, 17.2, 9.5
 */
public final class EventsDao {

    public record Event(
            int id,
            String name,
            String status,
            String creatorUuid,
            Integer spawnX,
            Integer spawnY,
            Integer spawnZ,
            String spawnDimension,
            long createdAt,
            Long completedAt,
            int participantCount
    ) {}

    /**
     * Inserts a new event and returns the generated id.
     */
    public int insert(Connection conn, Event event) {
        String sql = """
                INSERT INTO events (name, status, creator_uuid, spawn_x, spawn_y, spawn_z,
                    spawn_dimension, created_at, completed_at, participant_count)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, event.name());
            ps.setString(2, event.status());
            ps.setString(3, event.creatorUuid());
            setNullableInt(ps, 4, event.spawnX());
            setNullableInt(ps, 5, event.spawnY());
            setNullableInt(ps, 6, event.spawnZ());
            setNullableString(ps, 7, event.spawnDimension());
            ps.setLong(8, event.createdAt());
            setNullableLong(ps, 9, event.completedAt());
            ps.setInt(10, event.participantCount());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
                throw new SQLException("No generated key returned for event insert");
            }
        } catch (SQLException e) {
            throw new RuntimeException("EventsDao.insert failed", e);
        }
    }

    public Optional<Event> findById(Connection conn, int id) {
        String sql = "SELECT * FROM events WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("EventsDao.findById failed", e);
        }
    }

    public List<Event> findByStatus(Connection conn, String status) {
        String sql = "SELECT * FROM events WHERE status = ?";
        List<Event> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("EventsDao.findByStatus failed", e);
        }
        return result;
    }

    public List<Event> findByCreatorAndStatus(Connection conn, String creatorUuid, String status) {
        String sql = "SELECT * FROM events WHERE creator_uuid = ? AND status = ?";
        List<Event> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, creatorUuid);
            ps.setString(2, status);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("EventsDao.findByCreatorAndStatus failed", e);
        }
        return result;
    }

    public void updateStatus(Connection conn, int id, String status) {
        String sql = "UPDATE events SET status = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("EventsDao.updateStatus failed", e);
        }
    }

    public void updateSpawn(Connection conn, int id, int spawnX, int spawnY, int spawnZ, String spawnDimension) {
        String sql = "UPDATE events SET spawn_x = ?, spawn_y = ?, spawn_z = ?, spawn_dimension = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, spawnX);
            ps.setInt(2, spawnY);
            ps.setInt(3, spawnZ);
            ps.setString(4, spawnDimension);
            ps.setInt(5, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("EventsDao.updateSpawn failed", e);
        }
    }

    public void updateCompletion(Connection conn, int id, long completedAt, int participantCount) {
        String sql = "UPDATE events SET completed_at = ?, participant_count = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, completedAt);
            ps.setInt(2, participantCount);
            ps.setInt(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("EventsDao.updateCompletion failed", e);
        }
    }

    public void delete(Connection conn, int id) {
        String sql = "DELETE FROM events WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("EventsDao.delete failed", e);
        }
    }

    private Event mapRow(ResultSet rs) throws SQLException {
        int spawnXRaw = rs.getInt("spawn_x");
        Integer spawnX = rs.wasNull() ? null : spawnXRaw;
        int spawnYRaw = rs.getInt("spawn_y");
        Integer spawnY = rs.wasNull() ? null : spawnYRaw;
        int spawnZRaw = rs.getInt("spawn_z");
        Integer spawnZ = rs.wasNull() ? null : spawnZRaw;
        long completedAtRaw = rs.getLong("completed_at");
        Long completedAt = rs.wasNull() ? null : completedAtRaw;
        return new Event(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("status"),
                rs.getString("creator_uuid"),
                spawnX,
                spawnY,
                spawnZ,
                rs.getString("spawn_dimension"),
                rs.getLong("created_at"),
                completedAt,
                rs.getInt("participant_count")
        );
    }

    private static void setNullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value != null) {
            ps.setString(index, value);
        } else {
            ps.setNull(index, java.sql.Types.VARCHAR);
        }
    }

    private static void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value != null) {
            ps.setInt(index, value);
        } else {
            ps.setNull(index, java.sql.Types.INTEGER);
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
