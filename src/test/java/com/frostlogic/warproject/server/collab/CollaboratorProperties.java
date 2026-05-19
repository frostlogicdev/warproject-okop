package com.frostlogic.warproject.server.collab;

import com.frostlogic.warproject.attachment.EnemyTickCounter;
import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.persistence.Database;
import com.frostlogic.warproject.persistence.dao.AccountsDao;
import com.frostlogic.warproject.persistence.dao.AuditLogDao;
import com.frostlogic.warproject.persistence.dao.AuditLogDao.AuditEntry;
import com.frostlogic.warproject.persistence.dao.PlayersDao;
import com.frostlogic.warproject.persistence.dao.PlayersDao.Player;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeTry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for the collaborator subsystem (Req. 11).
 *
 * <h2>Property 24: Автоматическая пометка коллаборанта</h2>
 * <em>For any</em> sequence of ticks with switching {@code regionAt(player.position).faction},
 * the {@code enemy_region_ticks} counter increments by exactly 1 on every tick
 * where {@code region != null && region.faction != player.faction}, and resets
 * to 0 on any tick where the condition is violated. {@code collaborator} is set
 * to {@code true} exactly on the first tick at which the counter first reaches
 * the configured {@code threshold_ticks}.
 *
 * <p>Modelled at the counter level (using {@link EnemyTickCounter}) because the
 * real {@link CollaboratorService#onPlayerTick} subscriber requires a live
 * {@code ServerPlayer}, which is not available in plain unit tests. The pure
 * counter-driven predicate tested here is the same one the service evaluates
 * inside {@code tickPlayer}.
 *
 * <h2>Property 25: Round-trip коллаборанта</h2>
 * <em>For any</em> player {@code t} and any pair of reasons {@code r1}, {@code r2},
 * the sequence {@code collab(t, r1); uncollab(t, r2)} restores
 * {@code players.collaborator = 0} (with {@code collab_reason = NULL}) and
 * writes exactly two rows to {@code audit_log}:
 * {@code (action=COLLAB, target=t, reason=r1)} and
 * {@code (action=UNCOLLAB, target=t, reason=r2)}.
 *
 * <p>Replays the two transactions the service performs inside
 * {@link CollaboratorService#runTransaction} directly through {@link PlayersDao}
 * and {@link AuditLogDao}, so the test does not depend on a live
 * {@code ServerPlayer} or on the post-commit attachment refresh. Each transaction
 * is wrapped in {@link Database#transaction} so the same atomicity guarantees
 * the service relies on are exercised.
 *
 * <p><b>Validates: Requirements 11.1, 11.3, 11.4</b>
 *
 * <p>Design: §12 Properties 24, 25.
 *
 * <p>Setup uses an in-memory SQLite database with shared-cache so that
 * {@link Database#inTx} (which opens a fresh connection per transaction) sees
 * the schema and rows populated by the test. A keep-alive connection is held
 * open for the lifetime of each {@code @BeforeTry}/{@code @AfterTry} pair to
 * prevent the in-memory database from being torn down between calls.
 */
class CollaboratorProperties {

    private Connection keepAlive;
    private Database database;
    private AccountsDao accountsDao;
    private PlayersDao playersDao;
    private AuditLogDao auditLogDao;

    @BeforeTry
    void setupDatabase() throws Exception {
        // Per-try unique shared in-memory database. Same pattern as
        // SubdivisionProperties (task 17.2).
        String dbName = "wp-collab-pbt-" + UUID.randomUUID();
        String url = "jdbc:sqlite:file:" + dbName + "?mode=memory&cache=shared";

        keepAlive = DriverManager.getConnection(url);
        keepAlive.setAutoCommit(true);
        applyMigration(keepAlive);

        database = new Database("sqlite", url, "", "");
        accountsDao = new AccountsDao();
        playersDao = new PlayersDao();
        auditLogDao = new AuditLogDao();
    }

    @AfterTry
    void tearDown() throws Exception {
        if (keepAlive != null && !keepAlive.isClosed()) {
            keepAlive.close();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 24: counter increments / resets and threshold detection
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Property 24 (a): Counter dynamics — for any boolean tick sequence, the
     * running {@link EnemyTickCounter} value matches the reference predicate:
     * <ul>
     *   <li>{@code +1} on every tick where {@code inEnemyRegion == true};</li>
     *   <li>{@code reset to 0} on every tick where {@code inEnemyRegion == false}.</li>
     * </ul>
     *
     * <p><b>Validates: Requirements 11.4</b>
     */
    @Property
    void counterIncrementsAndResetsOnTickSequence(@ForAll("tickSequences") List<Boolean> ticks) {
        EnemyTickCounter actual = EnemyTickCounter.ZERO;
        int reference = 0;

        for (int i = 0; i < ticks.size(); i++) {
            boolean inEnemy = ticks.get(i);
            // Drive the actual record through its own API (matches the service).
            actual = inEnemy ? actual.increment() : actual.reset();
            // Drive the reference scalar in lockstep.
            reference = inEnemy ? reference + 1 : 0;

            assertThat(actual.ticks())
                    .as("counter at tick %d (inEnemy=%s, sequence=%s)", i, inEnemy, ticks)
                    .isEqualTo(reference);
        }
    }

    /**
     * Property 24 (b): Auto-flag fires on the first tick at which the counter
     * reaches {@code threshold}. Models the {@code tickPlayer} loop without
     * Minecraft: if {@code inEnemy} is {@code true} we increment; if the new
     * value is {@code >= threshold} the player is flagged and the counter is
     * reset to zero (so we don't re-trigger on the next tick).
     *
     * <p>The expected first-fire index is computed by an independent reference:
     * scan the sequence, advancing a running counter, and report the index of
     * the first tick where the running counter reaches the threshold.
     *
     * <p><b>Validates: Requirements 11.4</b>
     */
    @Property
    void autoFlagFiresOnFirstTickReachingThreshold(
            @ForAll("tickSequences") List<Boolean> ticks,
            @ForAll @IntRange(min = 1, max = 12) int threshold
    ) {
        // Reference: index of the first tick (0-based) at which the
        // running counter first reaches the threshold; -1 if it never does.
        int expectedFireIndex = -1;
        {
            int running = 0;
            for (int i = 0; i < ticks.size(); i++) {
                running = ticks.get(i) ? running + 1 : 0;
                if (running >= threshold) {
                    expectedFireIndex = i;
                    break;
                }
            }
        }

        // Simulate the service's counter+flag loop.
        EnemyTickCounter counter = EnemyTickCounter.ZERO;
        boolean flagged = false;
        int actualFireIndex = -1;
        for (int i = 0; i < ticks.size(); i++) {
            if (flagged) {
                break;
            }
            boolean inEnemy = ticks.get(i);
            if (inEnemy) {
                counter = counter.increment();
                if (counter.ticks() >= threshold) {
                    flagged = true;
                    actualFireIndex = i;
                    counter = counter.reset();
                }
            } else if (counter.ticks() != 0) {
                counter = counter.reset();
            }
        }

        assertThat(actualFireIndex)
                .as("auto-flag should fire on the first tick at which the counter reaches threshold=%d (sequence=%s)",
                        threshold, ticks)
                .isEqualTo(expectedFireIndex);

        if (expectedFireIndex < 0) {
            assertThat(flagged)
                    .as("auto-flag must NOT fire when threshold is never reached")
                    .isFalse();
        } else {
            assertThat(flagged)
                    .as("auto-flag MUST fire when threshold is reached at index %d", expectedFireIndex)
                    .isTrue();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 25: round-trip collab/uncollab leaves exactly two audit rows
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Property 25: For any target player {@code t} and any pair of reasons
     * {@code r1}, {@code r2}, applying {@code collab(t, r1)} followed by
     * {@code uncollab(t, r2)} leaves:
     * <ul>
     *   <li>{@code players.collaborator = 0} and {@code players.collab_reason = NULL};</li>
     *   <li>exactly two rows in {@code audit_log} for that target — one
     *       {@code COLLAB} with {@code reason = r1} and one {@code UNCOLLAB}
     *       with {@code reason = r2}.</li>
     * </ul>
     *
     * <p><b>Validates: Requirements 11.1, 11.3</b>
     */
    @Property
    void collabUncollabRoundTripLeavesTwoAuditRows(
            @ForAll("factions") FactionId faction,
            @ForAll("uuids") String targetUuid,
            @ForAll("uuids") String actorUuid,
            @ForAll("reasons") String reason1,
            @ForAll("reasons") String reason2
    ) {
        String targetName = "Target_" + targetUuid.substring(0, 8);
        String actorName = "Actor_" + actorUuid.substring(0, 8);

        insertPlayer(targetUuid, faction);

        // collab(target, reason1) — same shape as CollaboratorService.set:
        // setCollaborator(uuid, true, reason1) + audit_log INSERT(action=COLLAB, reason=reason1)
        long t1 = System.currentTimeMillis();
        database.transaction(conn -> {
            playersDao.setCollaborator(conn, targetUuid, true, reason1);
            auditLogDao.insert(conn, t1,
                    actorUuid, actorName,
                    targetUuid, targetName,
                    CollaboratorService.AUDIT_ACTION_COLLAB, reason1, null);
        });

        // After collab: players.collaborator = 1, collab_reason = reason1.
        Optional<Player> afterCollab = playersDao.findByUuid(keepAlive, targetUuid);
        assertThat(afterCollab).isPresent();
        assertThat(afterCollab.get().collaborator())
                .as("players.collaborator should be 1 after collab()")
                .isTrue();
        assertThat(afterCollab.get().collabReason())
                .as("players.collab_reason should equal reason1 after collab()")
                .isEqualTo(reason1);

        // uncollab(target, reason2) — same shape as CollaboratorService.unset:
        // setCollaborator(uuid, false, null) + audit_log INSERT(action=UNCOLLAB, reason=reason2)
        long t2 = t1 + 1;
        database.transaction(conn -> {
            playersDao.setCollaborator(conn, targetUuid, false, null);
            auditLogDao.insert(conn, t2,
                    actorUuid, actorName,
                    targetUuid, targetName,
                    CollaboratorService.AUDIT_ACTION_UNCOLLAB, reason2, null);
        });

        // After uncollab: players.collaborator = 0, collab_reason = NULL.
        Optional<Player> afterUncollab = playersDao.findByUuid(keepAlive, targetUuid);
        assertThat(afterUncollab).isPresent();
        assertThat(afterUncollab.get().collaborator())
                .as("players.collaborator should be 0 after uncollab()")
                .isFalse();
        assertThat(afterUncollab.get().collabReason())
                .as("players.collab_reason should be NULL after uncollab()")
                .isNull();

        // Audit log: exactly two rows for this target — one COLLAB and one UNCOLLAB,
        // in chronological order, with the correct reasons.
        List<AuditEntry> entries = auditLogDao.findByTargetUuid(keepAlive, targetUuid, 100, 0);
        assertThat(entries)
                .as("audit_log should contain exactly two rows for target %s", targetUuid)
                .hasSize(2);

        // findByTargetUuid orders DESC by ts_utc, so [0] = UNCOLLAB, [1] = COLLAB.
        AuditEntry first = entries.get(1);
        AuditEntry second = entries.get(0);

        assertThat(first.action())
                .as("first chronological audit row must be COLLAB")
                .isEqualTo(CollaboratorService.AUDIT_ACTION_COLLAB);
        assertThat(first.reason())
                .as("COLLAB reason must equal reason1")
                .isEqualTo(reason1);
        assertThat(first.targetUuid()).isEqualTo(targetUuid);
        assertThat(first.actorUuid()).isEqualTo(actorUuid);

        assertThat(second.action())
                .as("second chronological audit row must be UNCOLLAB")
                .isEqualTo(CollaboratorService.AUDIT_ACTION_UNCOLLAB);
        assertThat(second.reason())
                .as("UNCOLLAB reason must equal reason2")
                .isEqualTo(reason2);
        assertThat(second.targetUuid()).isEqualTo(targetUuid);
        assertThat(second.actorUuid()).isEqualTo(actorUuid);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Generators
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates short boolean tick sequences. Length 1..30 keeps each try
     * cheap enough that we can run the configured 100 tries within seconds,
     * yet long enough to exercise multiple increment/reset transitions.
     */
    @Provide
    Arbitrary<List<Boolean>> tickSequences() {
        return Arbitraries.of(true, false).list().ofMinSize(1).ofMaxSize(30);
    }

    @Provide
    Arbitrary<FactionId> factions() {
        return Arbitraries.of(FactionId.ZARNAVIA, FactionId.CHERNOGRYAD);
    }

    @Provide
    Arbitrary<String> uuids() {
        return Arbitraries.create(() -> UUID.randomUUID().toString());
    }

    /**
     * Generates non-empty reason strings using a small alphanumeric alphabet
     * plus single spaces. Bounded length 1..64 stays well within the
     * {@code TEXT}/{@code reason} column and avoids DB-driver quirks around
     * extremely large strings.
     */
    @Provide
    Arbitrary<String> reasons() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars(' ', '-', '_', '.', ':')
                .ofMinLength(1)
                .ofMaxLength(64);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Inserts the prerequisite {@code accounts} + {@code players} rows for the
     * target in the given faction. Uses {@link #keepAlive} directly (auto-commit)
     * so the rows are visible to the connections opened by {@link Database#inTx}.
     */
    private void insertPlayer(String uuid, FactionId faction) {
        accountsDao.insert(keepAlive, new AccountsDao.Account(
                uuid,
                "$2a$10$dummyhash000000000000000000000000000000000000000000",
                System.currentTimeMillis(), null, 0, 0));
        playersDao.insert(keepAlive, new Player(
                uuid,
                faction.getSerializedName().toUpperCase(),
                "SOLDIER", null, "ACCEPTED",
                "Test", "Player",
                false, null, false, 0,
                System.currentTimeMillis(),
                null, null, null,
                null /* subdivision_id */));
    }

    private void applyMigration(Connection conn) throws IOException, SQLException {
        InputStream is = getClass().getClassLoader().getResourceAsStream("db/migrations/V1__init.sql");
        if (is == null) {
            throw new IOException("V1__init.sql not found on classpath");
        }
        String sql;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            sql = reader.lines().collect(Collectors.joining("\n"));
        }
        sql = sql.replace("${AI}", "AUTOINCREMENT");

        String[] statements = splitStatements(sql);
        try (Statement stmt = conn.createStatement()) {
            for (String s : statements) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) {
                    stmt.execute(trimmed);
                }
            }
        }
    }

    /** Splits SQL on semicolons, respecting single-quoted strings and comments. */
    private static String[] splitStatements(String sql) {
        java.util.List<String> result = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            char next = (i + 1 < sql.length()) ? sql.charAt(i + 1) : 0;

            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false;
                    current.append(c);
                }
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (c == '-' && next == '-' && !inSingleQuote) {
                inLineComment = true;
                i++;
                continue;
            }
            if (c == '/' && next == '*' && !inSingleQuote) {
                inBlockComment = true;
                i++;
                continue;
            }
            if (c == '\'') {
                inSingleQuote = !inSingleQuote;
                current.append(c);
                continue;
            }
            if (c == ';' && !inSingleQuote) {
                String stmt = current.toString().trim();
                if (!stmt.isEmpty()) {
                    result.add(stmt);
                }
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        String last = current.toString().trim();
        if (!last.isEmpty()) {
            result.add(last);
        }
        return result.toArray(new String[0]);
    }
}
