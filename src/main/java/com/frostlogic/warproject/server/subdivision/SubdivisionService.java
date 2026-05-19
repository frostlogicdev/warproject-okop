package com.frostlogic.warproject.server.subdivision;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.persistence.Database;
import com.frostlogic.warproject.persistence.dao.AuditLogDao;
import com.frostlogic.warproject.persistence.dao.PlayersDao;
import com.frostlogic.warproject.persistence.dao.SubdivisionsDao;
import com.frostlogic.warproject.persistence.dao.SubdivisionsDao.Subdivision;
import com.frostlogic.warproject.persistence.dao.SubdivisionsDao.SubdivisionMember;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Service that consolidates subdivision lifecycle and membership operations
 * (Req. 16.1–16.5).
 * <p>
 * Each state-changing operation runs inside a single
 * {@link Database#transaction(java.util.function.Consumer)} so that the
 * primary effect (insert / delete / member-add / member-remove plus the
 * matching {@code players.subdivision_id} update) and the corresponding
 * {@code audit_log} row are committed atomically. Failures bubble up as a
 * {@link Result.Failure} — never a partially-applied transaction.
 * <p>
 * <strong>Uniqueness.</strong> The {@code idx_subdiv_faction_name UNIQUE}
 * index on {@code subdivisions(faction, name)} guarantees name uniqueness
 * within a faction (Req. 16.2). The service also performs an explicit
 * pre-check to surface a localized error key before the JDBC layer would
 * raise a constraint-violation exception — both checks are inside the same
 * transaction, so concurrent inserts that race past the pre-check still hit
 * the unique index and roll back.
 * <p>
 * <strong>Faction casing.</strong> The {@code subdivisions.faction} column
 * stores the upper-cased serialized name of {@link FactionId} (e.g.
 * {@code "ZARNAVIA"}) for compatibility with the existing rows produced by
 * {@code FactionChoiceHandler} and {@code SubdivisionCommands}.
 * <p>
 * <strong>Replaces.</strong> The legacy {@code server.SubdivisionStore} /
 * {@code server.Subdivision} JSON-backed pair is superseded by this service +
 * {@link SubdivisionsDao}. Future tasks will migrate the remaining call sites
 * (currently {@code SubdivisionCommands}, {@code WarProjectCommands},
 * {@code item.PassportData}) to use this service directly.
 * <p>
 * Requirements: 16.1, 16.2, 16.3, 16.4, 16.5
 * Design: §3
 */
public final class SubdivisionService {

    /** Maximum length of a subdivision name (matches {@code subdivisions.name VARCHAR(32)}). */
    public static final int MAX_NAME_LENGTH = 32;

    // ── Audit action keys (mirrors AuditAction enum used elsewhere) ────────
    private static final String ACTION_CREATE = "SUBDIV_CREATE";
    private static final String ACTION_DELETE = "SUBDIV_DELETE";
    private static final String ACTION_INVITE = "SUBDIV_INVITE";
    private static final String ACTION_KICK   = "SUBDIV_KICK";

    // ── Localized error keys returned via Result.Failure ───────────────────
    public static final String ERR_INVALID_NAME       = "wp.command.subdivision.invalid_name";
    public static final String ERR_NAME_EXISTS        = "wp.command.subdivision.create.already_exists";
    public static final String ERR_NOT_FOUND          = "wp.command.subdivision.not_found";
    public static final String ERR_FACTION_MISMATCH   = "wp.command.subdivision.faction_mismatch";
    public static final String ERR_TARGET_ALREADY_IN  = "wp.command.subdivision.invite.already_member";
    public static final String ERR_TARGET_NOT_MEMBER  = "wp.command.subdivision.kick.not_member";
    public static final String ERR_INTERNAL           = "wp.command.error.internal";

    private final Database database;
    private final SubdivisionsDao subdivisionsDao;
    private final PlayersDao playersDao;
    private final AuditLogDao auditLogDao;

    public SubdivisionService(Database database,
                              SubdivisionsDao subdivisionsDao,
                              PlayersDao playersDao,
                              AuditLogDao auditLogDao) {
        this.database = Objects.requireNonNull(database, "database");
        this.subdivisionsDao = Objects.requireNonNull(subdivisionsDao, "subdivisionsDao");
        this.playersDao = Objects.requireNonNull(playersDao, "playersDao");
        this.auditLogDao = Objects.requireNonNull(auditLogDao, "auditLogDao");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Write operations
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Creates a new subdivision in the given faction (Req. 16.1, 16.2).
     * <p>
     * Atomic effects on success:
     * <ol>
     *   <li>Insert into {@code subdivisions(faction, name, created_by, created_at)}.</li>
     *   <li>Insert a {@code SUBDIV_CREATE} row in {@code audit_log}.</li>
     * </ol>
     * Both happen in one transaction. The returned {@link Subdivision} carries
     * the database-generated id.
     * <p>
     * Returns:
     * <ul>
     *   <li>{@link Result.Failure} with {@link #ERR_INVALID_NAME} if the
     *       trimmed name is empty or longer than {@link #MAX_NAME_LENGTH}.</li>
     *   <li>{@link Result.Failure} with {@link #ERR_NAME_EXISTS} if a
     *       subdivision with the same {@code (faction, name)} already exists.</li>
     *   <li>{@link Result.Failure} with {@link #ERR_INTERNAL} on any other
     *       persistence failure.</li>
     * </ul>
     */
    public Result<Subdivision> create(FactionId faction, String name,
                                       String creatorUuid, String creatorName) {
        Objects.requireNonNull(faction, "faction");
        Objects.requireNonNull(creatorUuid, "creatorUuid");

        String normalized = normalizeName(name);
        if (!isValidName(normalized)) {
            return Result.failure(ERR_INVALID_NAME);
        }

        String factionName = factionKey(faction);
        long now = System.currentTimeMillis();

        try {
            return database.inTx(conn -> {
                if (subdivisionsDao.findByFactionAndName(conn, factionName, normalized).isPresent()) {
                    return Result.<Subdivision>failure(ERR_NAME_EXISTS);
                }
                int id = subdivisionsDao.insert(conn, factionName, normalized, creatorUuid, now);
                auditLogDao.insert(conn, now, creatorUuid, creatorName,
                        null, null, ACTION_CREATE, null,
                        "{\"faction\":\"" + factionName + "\","
                                + "\"name\":\"" + escapeJson(normalized) + "\","
                                + "\"id\":" + id + "}");
                return Result.<Subdivision>success(
                        new Subdivision(id, factionName, normalized, creatorUuid, now));
            });
        } catch (RuntimeException e) {
            WarProject.LOGGER.error("[WP SubdivisionService.create] Transaction failed for faction={} name='{}' creator={}: {}",
                    factionName, normalized, creatorName, e.getMessage(), e);
            return Result.failure(ERR_INTERNAL);
        }
    }

    /**
     * Deletes a subdivision by id with the given reason (Req. 16.3).
     * <p>
     * Atomic effects on success:
     * <ol>
     *   <li>Clear {@code players.subdivision_id} for every member (the
     *       {@code players.subdivision_id} FK is application-managed — no
     *       {@code ON DELETE} action is configured for it).</li>
     *   <li>Delete the {@code subdivisions} row. The matching
     *       {@code subdivision_members} rows are removed by the
     *       {@code ON DELETE CASCADE} FK on the join table.</li>
     *   <li>Insert a {@code SUBDIV_DELETE} row in {@code audit_log} carrying
     *       the {@code reason}.</li>
     * </ol>
     * <p>
     * Returns {@link Result.Failure} with {@link #ERR_NOT_FOUND} if no
     * subdivision matches the id, or {@link #ERR_INTERNAL} on persistence
     * failure. The {@code reason} is preserved verbatim in
     * {@code audit_log.reason}.
     */
    public Result<Subdivision> delete(int subdivisionId,
                                       String deleterUuid, String deleterName,
                                       String reason) {
        long now = System.currentTimeMillis();

        try {
            return database.inTx(conn -> {
                Optional<Subdivision> existing = subdivisionsDao.findById(conn, subdivisionId);
                if (existing.isEmpty()) {
                    return Result.<Subdivision>failure(ERR_NOT_FOUND);
                }
                Subdivision sub = existing.get();

                // Cascading member cleanup: clear FK refs in players, then
                // drop the subdivisions row (subdivision_members rows are
                // wiped by ON DELETE CASCADE).
                playersDao.clearSubdivisionId(conn, sub.id());
                subdivisionsDao.delete(conn, sub.id());

                auditLogDao.insert(conn, now, deleterUuid, deleterName,
                        null, null, ACTION_DELETE, reason,
                        "{\"faction\":\"" + sub.faction() + "\","
                                + "\"name\":\"" + escapeJson(sub.name()) + "\","
                                + "\"id\":" + sub.id() + "}");
                return Result.success(sub);
            });
        } catch (RuntimeException e) {
            WarProject.LOGGER.error("[WP SubdivisionService.delete] Transaction failed for id={} deleter={}: {}",
                    subdivisionId, deleterName, e.getMessage(), e);
            return Result.failure(ERR_INTERNAL);
        }
    }

    /**
     * Adds a player to the given subdivision (Req. 16.5 — invite).
     * <p>
     * Atomic effects on success:
     * <ol>
     *   <li>Insert into {@code subdivision_members(subdivision_id, player_uuid, joined_at)}.</li>
     *   <li>Update {@code players.subdivision_id} for the target.</li>
     *   <li>Insert a {@code SUBDIV_INVITE} row in {@code audit_log}.</li>
     * </ol>
     * <p>
     * Returns:
     * <ul>
     *   <li>{@link Result.Failure} with {@link #ERR_NOT_FOUND} if the
     *       subdivision does not exist;</li>
     *   <li>{@link Result.Failure} with {@link #ERR_TARGET_ALREADY_IN} if the
     *       target already belongs to a subdivision (full reassignment is out
     *       of scope here — callers should kick first or use admin tools);</li>
     *   <li>{@link Result.Failure} with {@link #ERR_INTERNAL} on any other
     *       persistence failure.</li>
     * </ul>
     */
    public Result<Subdivision> invite(int subdivisionId,
                                       String targetUuid, String targetName,
                                       String inviterUuid, String inviterName) {
        Objects.requireNonNull(targetUuid, "targetUuid");
        Objects.requireNonNull(inviterUuid, "inviterUuid");

        long now = System.currentTimeMillis();

        try {
            return database.inTx(conn -> {
                Optional<Subdivision> sub = subdivisionsDao.findById(conn, subdivisionId);
                if (sub.isEmpty()) {
                    return Result.<Subdivision>failure(ERR_NOT_FOUND);
                }

                Optional<PlayersDao.Player> targetRow = playersDao.findByUuid(conn, targetUuid);
                if (targetRow.isPresent() && targetRow.get().subdivisionId() != null) {
                    return Result.<Subdivision>failure(ERR_TARGET_ALREADY_IN);
                }

                subdivisionsDao.addMember(conn, subdivisionId, targetUuid, now);
                playersDao.setSubdivisionId(conn, targetUuid, subdivisionId);
                auditLogDao.insert(conn, now, inviterUuid, inviterName,
                        targetUuid, targetName, ACTION_INVITE, null,
                        "{\"subdivision_id\":" + subdivisionId + ","
                                + "\"name\":\"" + escapeJson(sub.get().name()) + "\"}");
                return Result.success(sub.get());
            });
        } catch (RuntimeException e) {
            WarProject.LOGGER.error("[WP SubdivisionService.invite] Transaction failed for sub={} target={} inviter={}: {}",
                    subdivisionId, targetName, inviterName, e.getMessage(), e);
            return Result.failure(ERR_INTERNAL);
        }
    }

    /**
     * Removes a player from the given subdivision (Req. 16.5 — kick).
     * <p>
     * Atomic effects on success:
     * <ol>
     *   <li>Delete the matching row from {@code subdivision_members}.</li>
     *   <li>Clear {@code players.subdivision_id} for the target.</li>
     *   <li>Insert a {@code SUBDIV_KICK} row in {@code audit_log} carrying the
     *       {@code reason}.</li>
     * </ol>
     * <p>
     * Returns:
     * <ul>
     *   <li>{@link Result.Failure} with {@link #ERR_NOT_FOUND} if the
     *       subdivision does not exist;</li>
     *   <li>{@link Result.Failure} with {@link #ERR_TARGET_NOT_MEMBER} if the
     *       target does not belong to that subdivision (either no players row
     *       at all, or {@code subdivision_id} differs);</li>
     *   <li>{@link Result.Failure} with {@link #ERR_INTERNAL} on persistence
     *       failure.</li>
     * </ul>
     */
    public Result<Subdivision> kick(int subdivisionId,
                                     String targetUuid, String targetName,
                                     String kickerUuid, String kickerName,
                                     String reason) {
        Objects.requireNonNull(targetUuid, "targetUuid");
        Objects.requireNonNull(kickerUuid, "kickerUuid");

        long now = System.currentTimeMillis();

        try {
            return database.inTx(conn -> {
                Optional<Subdivision> sub = subdivisionsDao.findById(conn, subdivisionId);
                if (sub.isEmpty()) {
                    return Result.<Subdivision>failure(ERR_NOT_FOUND);
                }

                Optional<PlayersDao.Player> targetRow = playersDao.findByUuid(conn, targetUuid);
                Integer currentSub = targetRow.map(PlayersDao.Player::subdivisionId).orElse(null);
                if (currentSub == null || currentSub != subdivisionId) {
                    return Result.<Subdivision>failure(ERR_TARGET_NOT_MEMBER);
                }

                subdivisionsDao.removeMember(conn, subdivisionId, targetUuid);
                playersDao.setSubdivisionId(conn, targetUuid, null);
                auditLogDao.insert(conn, now, kickerUuid, kickerName,
                        targetUuid, targetName, ACTION_KICK, reason,
                        "{\"subdivision_id\":" + subdivisionId + ","
                                + "\"name\":\"" + escapeJson(sub.get().name()) + "\"}");
                return Result.success(sub.get());
            });
        } catch (RuntimeException e) {
            WarProject.LOGGER.error("[WP SubdivisionService.kick] Transaction failed for sub={} target={} kicker={}: {}",
                    subdivisionId, targetName, kickerName, e.getMessage(), e);
            return Result.failure(ERR_INTERNAL);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Read operations (no audit, no transaction beyond the read)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Returns every subdivision belonging to the given faction (Req. 16.5 — list).
     * <p>
     * Read-only; runs inside a single short transaction so the result is a
     * consistent snapshot.
     */
    public List<Subdivision> list(FactionId faction) {
        Objects.requireNonNull(faction, "faction");
        String factionName = factionKey(faction);
        return database.inTx(conn -> subdivisionsDao.findByFaction(conn, factionName));
    }

    /**
     * Returns the requested subdivision plus its current member roster
     * (Req. 16.5 — info), or {@link Optional#empty()} if no such subdivision
     * exists.
     * <p>
     * Read-only; the subdivision row and its member rows are read inside a
     * single transaction.
     */
    public Optional<SubdivisionInfo> info(int subdivisionId) {
        return database.inTx(conn -> {
            Optional<Subdivision> sub = subdivisionsDao.findById(conn, subdivisionId);
            if (sub.isEmpty()) {
                return Optional.<SubdivisionInfo>empty();
            }
            List<SubdivisionMember> members = subdivisionsDao.findMembers(conn, sub.get().id());
            return Optional.of(new SubdivisionInfo(sub.get(), members));
        });
    }

    /**
     * Resolves a subdivision by faction + name (case-sensitive on the trimmed
     * name, faction matched against its upper-case serialized form). Returns
     * an empty optional if no such row exists.
     * <p>
     * Useful as a precursor to the id-based write operations when the caller
     * only knows the human-readable name.
     */
    public Optional<Subdivision> findByName(FactionId faction, String name) {
        Objects.requireNonNull(faction, "faction");
        String factionName = factionKey(faction);
        String normalized = normalizeName(name);
        if (!isValidName(normalized)) {
            return Optional.empty();
        }
        return database.inTx(conn -> subdivisionsDao.findByFactionAndName(conn, factionName, normalized));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    /** Normalizes whitespace in a subdivision name (collapse + trim). */
    private static String normalizeName(String raw) {
        if (raw == null) return "";
        return raw.trim().replaceAll("\\s+", " ");
    }

    /** Validates a subdivision name (non-empty, length-bounded). */
    private static boolean isValidName(String name) {
        return !name.isEmpty() && name.length() <= MAX_NAME_LENGTH;
    }

    /** Returns the upper-case serialized faction name used in the {@code subdivisions.faction} column. */
    private static String factionKey(FactionId faction) {
        return faction.getSerializedName().toUpperCase(Locale.ROOT);
    }

    /** Minimal JSON-string escape for {@code audit_log.extra_json}. */
    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 2);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Result type
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Sealed result type for service operations. Either a {@link Success}
     * carrying the operation's value, or a {@link Failure} carrying a
     * translation key suitable for {@code Component.translatable(errorKey, ...)}
     * in the calling Brigadier handler.
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
            throw new IllegalStateException("SubdivisionService.Result was a Failure: "
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

    /**
     * Snapshot returned by {@link #info(int)} — the subdivision row plus its
     * current member list. Members are returned in the natural order produced
     * by {@link SubdivisionsDao#findMembers}.
     */
    public record SubdivisionInfo(Subdivision subdivision, List<SubdivisionMember> members) {}
}
