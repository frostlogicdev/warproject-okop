package com.frostlogic.warproject.server.event;

import com.frostlogic.warproject.WpConfig;
import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import com.frostlogic.warproject.persistence.Database;
import com.frostlogic.warproject.persistence.dao.AuditLogDao;
import com.frostlogic.warproject.persistence.dao.EventParticipantsDao;
import com.frostlogic.warproject.persistence.dao.EventsDao;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;

/**
 * Service managing the event lifecycle: creation, spawn configuration,
 * activation, participant tracking, completion, and deletion.
 * <p>
 * State transitions: PENDING → ACTIVE → COMPLETED.
 * Only one event may be ACTIVE at a time (tracked in-memory via {@link #activeEvent}).
 * <p>
 * Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7, 8.8, 8.9,
 *               9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 9.7
 * Design: §3 Event System — EventService
 */
public final class EventService {

    private static final Logger LOGGER = LogUtils.getLogger();

    // ── Audit action keys ──────────────────────────────────────────────────
    private static final String ACTION_EVENT_CREATE = "EVENT_CREATE";
    private static final String ACTION_EVENT_START  = "EVENT_START";
    private static final String ACTION_EVENT_STOP   = "EVENT_STOP";
    private static final String ACTION_EVENT_DELETE = "EVENT_DELETE";

    // ── Error keys ─────────────────────────────────────────────────────────
    public static final String ERR_NAME_TOO_SHORT    = "wp.event.error.name_too_short";
    public static final String ERR_NAME_TOO_LONG     = "wp.event.error.name_too_long";
    public static final String ERR_NAME_EXISTS       = "wp.event.error.name_exists";
    public static final String ERR_NO_PENDING_EVENT  = "wp.event.error.no_pending_event";
    public static final String ERR_NO_ACTIVE_EVENT   = "wp.event.error.no_active_event";
    public static final String ERR_SPAWN_NOT_SET     = "wp.event.error.spawn_not_set";
    public static final String ERR_NO_PENDING_START  = "wp.event.error.no_pending_start";
    public static final String ERR_EVENT_NOT_FOUND   = "wp.event.error.not_found";
    public static final String ERR_CANNOT_DELETE     = "wp.event.error.cannot_delete";
    public static final String ERR_INTERNAL          = "wp.event.error.internal";

    private final Database database;
    private final EventsDao eventsDao;
    private final EventParticipantsDao participantsDao;
    private final AuditLogDao auditLogDao;

    /** In-memory active event state; null when no event is ACTIVE. */
    private volatile @Nullable EventData activeEvent;

    public EventService(Database database,
                        EventsDao eventsDao,
                        EventParticipantsDao participantsDao,
                        AuditLogDao auditLogDao) {
        this.database = Objects.requireNonNull(database, "database");
        this.eventsDao = Objects.requireNonNull(eventsDao, "eventsDao");
        this.participantsDao = Objects.requireNonNull(participantsDao, "participantsDao");
        this.auditLogDao = Objects.requireNonNull(auditLogDao, "auditLogDao");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Creates a new event in PENDING state.
     * <p>
     * Validates name length (3–64 chars) and checks no duplicate PENDING/ACTIVE name exists.
     * Persists the event to the database and logs the creation.
     *
     * @param name        the event name
     * @param creatorUuid UUID of the admin creating the event
     * @return success with the created EventData, or failure with an error key
     */
    public Result<EventData> create(String name, UUID creatorUuid) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(creatorUuid, "creatorUuid");

        // Validate name length
        if (name.length() < 3) {
            return Result.failure(ERR_NAME_TOO_SHORT);
        }
        if (name.length() > 64) {
            return Result.failure(ERR_NAME_TOO_LONG);
        }

        long now = System.currentTimeMillis();
        String creatorStr = creatorUuid.toString();

        try {
            return database.inTx(conn -> {
                // Check for duplicate name in PENDING or ACTIVE status
                List<EventsDao.Event> pending = eventsDao.findByStatus(conn, EventState.PENDING.name());
                List<EventsDao.Event> active = eventsDao.findByStatus(conn, EventState.ACTIVE.name());

                boolean duplicateExists = pending.stream().anyMatch(e -> e.name().equals(name))
                        || active.stream().anyMatch(e -> e.name().equals(name));
                if (duplicateExists) {
                    return Result.<EventData>failure(ERR_NAME_EXISTS);
                }

                // Persist
                EventsDao.Event daoEvent = new EventsDao.Event(
                        0, name, EventState.PENDING.name(), creatorStr,
                        null, null, null, null,
                        now, null, 0
                );
                int id = eventsDao.insert(conn, daoEvent);

                // Audit log
                auditLogDao.insert(conn, now, creatorStr, null,
                        null, null, ACTION_EVENT_CREATE, null,
                        "{\"eventId\":" + id + ",\"name\":\"" + escapeJson(name) + "\"}");

                EventData eventData = EventData.createPending(id, name, creatorUuid, now);
                return Result.<EventData>success(eventData);
            });
        } catch (RuntimeException e) {
            LOGGER.error("[WP EventService.create] Transaction failed for name='{}' creator={}: {}",
                    name, creatorStr, e.getMessage(), e);
            return Result.failure(ERR_INTERNAL);
        }
    }

    /**
     * Sets the spawn point for the PENDING event owned by the given admin.
     * <p>
     * Finds the PENDING event created by the admin and updates its spawn coordinates.
     *
     * @param adminUuid UUID of the admin setting the spawn
     * @param pos       the block position for the event spawn
     * @param dimension the dimension/level key for the spawn
     * @return success or failure with an error key
     */
    public Result<Void> setSpawn(UUID adminUuid, BlockPos pos, ResourceKey<Level> dimension) {
        Objects.requireNonNull(adminUuid, "adminUuid");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(dimension, "dimension");

        String adminStr = adminUuid.toString();

        try {
            return database.inTx(conn -> {
                List<EventsDao.Event> pendingByAdmin =
                        eventsDao.findByCreatorAndStatus(conn, adminStr, EventState.PENDING.name());

                if (pendingByAdmin.isEmpty()) {
                    return Result.<Void>failure(ERR_NO_PENDING_EVENT);
                }

                // Use the first (most recent) pending event by this admin
                EventsDao.Event event = pendingByAdmin.get(0);
                eventsDao.updateSpawn(conn, event.id(),
                        pos.getX(), pos.getY(), pos.getZ(),
                        dimension.location().toString());

                return Result.<Void>success(null);
            });
        } catch (RuntimeException e) {
            LOGGER.error("[WP EventService.setSpawn] Transaction failed for admin={}: {}",
                    adminStr, e.getMessage(), e);
            return Result.failure(ERR_INTERNAL);
        }
    }

    /**
     * Starts a PENDING event, transitioning it to ACTIVE.
     * <p>
     * Finds the PENDING event created by the admin, validates spawn is set,
     * changes status to ACTIVE, and stores it as the in-memory active event.
     * The caller is responsible for broadcasting the announcement to all players.
     *
     * @param adminUuid UUID of the admin starting the event
     * @return success (with the event name accessible via getActiveEvent()) or failure
     */
    public Result<Void> start(UUID adminUuid) {
        Objects.requireNonNull(adminUuid, "adminUuid");

        String adminStr = adminUuid.toString();

        try {
            return database.inTx(conn -> {
                List<EventsDao.Event> pendingByAdmin =
                        eventsDao.findByCreatorAndStatus(conn, adminStr, EventState.PENDING.name());

                if (pendingByAdmin.isEmpty()) {
                    return Result.<Void>failure(ERR_NO_PENDING_START);
                }

                EventsDao.Event event = pendingByAdmin.get(0);

                // Validate spawn is configured
                if (event.spawnX() == null || event.spawnY() == null || event.spawnZ() == null
                        || event.spawnDimension() == null) {
                    return Result.<Void>failure(ERR_SPAWN_NOT_SET);
                }

                // Transition to ACTIVE
                eventsDao.updateStatus(conn, event.id(), EventState.ACTIVE.name());

                // Audit log
                long now = System.currentTimeMillis();
                auditLogDao.insert(conn, now, adminStr, null,
                        null, null, ACTION_EVENT_START, null,
                        "{\"eventId\":" + event.id() + ",\"name\":\"" + escapeJson(event.name()) + "\"}");

                // Build in-memory active event
                BlockPos spawnPos = new BlockPos(event.spawnX(), event.spawnY(), event.spawnZ());
                ResourceKey<Level> dim = parseDimension(event.spawnDimension());

                activeEvent = new EventData(
                        event.id(), event.name(), EventState.ACTIVE, UUID.fromString(event.creatorUuid()),
                        spawnPos, dim, event.createdAt(), null, new HashSet<>()
                );

                return Result.<Void>success(null);
            });
        } catch (RuntimeException e) {
            LOGGER.error("[WP EventService.start] Transaction failed for admin={}: {}",
                    adminStr, e.getMessage(), e);
            return Result.failure(ERR_INTERNAL);
        }
    }

    /**
     * Stops the currently ACTIVE event, transitioning it to COMPLETED.
     * <p>
     * Teleports all online participants back to their faction spawns,
     * persists completion data (timestamp + participant count) in a single transaction.
     *
     * @param adminUuid UUID of the admin stopping the event
     * @param server    the MinecraftServer instance for player lookups and teleportation
     * @return success or failure with an error key
     */
    public Result<Void> stop(UUID adminUuid, MinecraftServer server) {
        Objects.requireNonNull(adminUuid, "adminUuid");
        Objects.requireNonNull(server, "server");

        EventData current = activeEvent;
        if (current == null || current.status() != EventState.ACTIVE) {
            return Result.failure(ERR_NO_ACTIVE_EVENT);
        }

        String adminStr = adminUuid.toString();
        long now = System.currentTimeMillis();
        Set<UUID> participants = current.participants();
        int participantCount = participants.size();

        try {
            // Persist completion in a single transaction
            database.transaction(conn -> {
                eventsDao.updateStatus(conn, current.id(), EventState.COMPLETED.name());
                eventsDao.updateCompletion(conn, current.id(), now, participantCount);

                // Persist all participants
                for (UUID participantUuid : participants) {
                    participantsDao.insert(conn, new EventParticipantsDao.EventParticipant(
                            current.id(), participantUuid.toString(), now
                    ));
                }

                // Audit log
                auditLogDao.insert(conn, now, adminStr, null,
                        null, null, ACTION_EVENT_STOP, null,
                        "{\"eventId\":" + current.id()
                                + ",\"name\":\"" + escapeJson(current.name())
                                + "\",\"participants\":" + participantCount + "}");
            });

            // Teleport online participants to faction spawns
            teleportParticipantsToFactionSpawns(server, participants);

            // Clear in-memory active event
            activeEvent = null;

            return Result.success(null);
        } catch (RuntimeException e) {
            LOGGER.error("[WP EventService.stop] Transaction failed for event='{}' admin={}: {}",
                    current.name(), adminStr, e.getMessage(), e);
            return Result.failure(ERR_INTERNAL);
        }
    }

    /**
     * Joins the currently ACTIVE event. Teleports the player to the event spawn
     * and adds them to the participant set (idempotent — no duplicate).
     *
     * @param player the player joining the event
     * @return success or failure with an error key
     */
    public Result<Void> join(ServerPlayer player) {
        Objects.requireNonNull(player, "player");

        EventData current = activeEvent;
        if (current == null || current.status() != EventState.ACTIVE) {
            return Result.failure(ERR_NO_ACTIVE_EVENT);
        }

        if (current.spawnPos() == null || current.dimension() == null) {
            return Result.failure(ERR_SPAWN_NOT_SET);
        }

        // Add to participants (Set guarantees idempotence)
        current.participants().add(player.getUUID());

        // Teleport to event spawn
        teleportToEventSpawn(player, current);

        return Result.success(null);
    }

    /**
     * Deletes an event by name. Only COMPLETED or PENDING events can be deleted.
     *
     * @param name the event name to delete
     * @return success or failure with an error key
     */
    public Result<Void> delete(String name) {
        Objects.requireNonNull(name, "name");

        try {
            return database.inTx(conn -> {
                // Find event by name across all statuses
                List<EventsDao.Event> pending = eventsDao.findByStatus(conn, EventState.PENDING.name());
                List<EventsDao.Event> completed = eventsDao.findByStatus(conn, EventState.COMPLETED.name());
                List<EventsDao.Event> active = eventsDao.findByStatus(conn, EventState.ACTIVE.name());

                Optional<EventsDao.Event> target = pending.stream()
                        .filter(e -> e.name().equals(name))
                        .findFirst();

                if (target.isEmpty()) {
                    target = completed.stream()
                            .filter(e -> e.name().equals(name))
                            .findFirst();
                }

                if (target.isEmpty()) {
                    // Check if it exists in ACTIVE — cannot delete
                    boolean existsActive = active.stream().anyMatch(e -> e.name().equals(name));
                    if (existsActive) {
                        return Result.<Void>failure(ERR_CANNOT_DELETE);
                    }
                    return Result.<Void>failure(ERR_EVENT_NOT_FOUND);
                }

                EventsDao.Event event = target.get();

                // Delete from database
                eventsDao.delete(conn, event.id());

                // Audit log
                long now = System.currentTimeMillis();
                auditLogDao.insert(conn, now, null, null,
                        null, null, ACTION_EVENT_DELETE, null,
                        "{\"eventId\":" + event.id() + ",\"name\":\"" + escapeJson(event.name()) + "\"}");

                return Result.<Void>success(null);
            });
        } catch (RuntimeException e) {
            LOGGER.error("[WP EventService.delete] Transaction failed for name='{}': {}",
                    name, e.getMessage(), e);
            return Result.failure(ERR_INTERNAL);
        }
    }

    /**
     * Returns the currently active event, or null if no event is active.
     */
    public @Nullable EventData getActiveEvent() {
        return activeEvent;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Internal helpers
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Teleports a player to the event spawn point.
     */
    private void teleportToEventSpawn(ServerPlayer player, EventData event) {
        BlockPos spawn = event.spawnPos();
        ResourceKey<Level> dim = event.dimension();
        if (spawn == null || dim == null) return;

        ServerLevel level = player.getServer() != null
                ? player.getServer().getLevel(dim)
                : null;
        if (level == null) {
            LOGGER.warn("[WP EventService] Cannot teleport player {} — dimension {} not found",
                    player.getGameProfile().getName(), dim.location());
            return;
        }

        double x = spawn.getX() + 0.5;
        double y = spawn.getY();
        double z = spawn.getZ() + 0.5;
        player.teleportTo(level, x, y, z, player.getYRot(), player.getXRot());
        player.setDeltaMovement(0.0, 0.0, 0.0);
        player.fallDistance = 0.0F;
    }

    /**
     * Teleports all online participants back to their faction spawn points.
     * Players without a valid faction are skipped.
     */
    private void teleportParticipantsToFactionSpawns(MinecraftServer server, Set<UUID> participants) {
        for (UUID uuid : participants) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) continue; // offline

            Optional<FactionId> factionOpt = player.getData(WpAttachmentTypes.FACTION.get());
            if (factionOpt.isEmpty()) continue; // no faction — skip

            FactionId faction = factionOpt.get();
            List<? extends Integer> coords = switch (faction) {
                case ZARNAVIA -> WpConfig.FACTIONS_ZARNAVIA_SPAWN.get();
                case CHERNOGRYAD -> WpConfig.FACTIONS_CHERNOGRYAD_SPAWN.get();
            };

            if (coords.size() < 3) {
                LOGGER.warn("[WP EventService] Faction spawn not configured for {}; cannot teleport player {}",
                        faction.getSerializedName(), player.getGameProfile().getName());
                continue;
            }

            double x = coords.get(0).doubleValue() + 0.5;
            double y = coords.get(1).doubleValue();
            double z = coords.get(2).doubleValue() + 0.5;
            player.teleportTo(player.serverLevel(), x, y, z, player.getYRot(), player.getXRot());
            player.setDeltaMovement(0.0, 0.0, 0.0);
            player.fallDistance = 0.0F;
        }
    }

    /**
     * Parses a dimension string (e.g. "minecraft:overworld") into a ResourceKey.
     */
    private static ResourceKey<Level> parseDimension(String dimensionStr) {
        if (dimensionStr == null || dimensionStr.isEmpty()) {
            return Level.OVERWORLD;
        }
        return ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.ResourceLocation.parse(dimensionStr)
        );
    }

    /**
     * Escapes a string for safe inclusion in JSON values.
     */
    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Result sealed interface (same pattern as SubdivisionService)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Result of an EventService operation — either success with a value or
     * failure with a translatable error key.
     */
    public sealed interface Result<T> {

        /** @return {@code true} iff this is a {@link Success}. */
        default boolean isSuccess() {
            return this instanceof Success<T>;
        }

        /**
         * Convenience accessor: returns the success value, or throws
         * {@link IllegalStateException} on a failure.
         */
        @SuppressWarnings("unchecked")
        default T orThrow() {
            if (this instanceof Success<?> s) {
                return (T) s.value();
            }
            throw new IllegalStateException("EventService.Result was a Failure: "
                    + ((Failure<?>) this).errorKey());
        }

        static <T> Result<T> success(T value) {
            return new Success<>(value);
        }

        static <T> Result<T> failure(String errorKey) {
            return new Failure<>(errorKey);
        }

        record Success<T>(T value) implements Result<T> {}

        record Failure<T>(String errorKey) implements Result<T> {}
    }
}
