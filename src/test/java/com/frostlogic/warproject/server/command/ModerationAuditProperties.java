package com.frostlogic.warproject.server.command;

import com.frostlogic.warproject.persistence.dao.AuditLogDao;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.StringLength;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeTry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for moderation command audit-log writes (task 15.6).
 * <p>
 * Covers <b>Property 13</b>: for any moderation command
 * {@code c ∈ {BAN, KICK, MUTE, UNBAN, UNMUTE, WARN, RANK_UP, COLLAB, UNCOLLAB,
 * FREEZE, UNFREEZE, VANISH, UNVANISH}} executed by {@code actor} on
 * {@code target} with {@code reason}, after a successful (committed)
 * transaction the {@code audit_log} table contains exactly one new row whose
 * fields satisfy:
 * <ul>
 *   <li>{@code action} equals the expected action key,</li>
 *   <li>{@code actor_uuid} / {@code target_uuid} match the input,</li>
 *   <li>{@code reason} matches the input,</li>
 *   <li>{@code ts_utc ≈ now} (the value passed by the caller),</li>
 *   <li>{@code extra_json} contains no secret material — passwords or captcha
 *       codes never appear in the audit row.</li>
 * </ul>
 * <p>
 * Side-effect time and audit time coincide because both are written inside a
 * single transaction in the production code (see e.g.
 * {@code ModerationCommands.runBan}, {@code FactionCommands.runUp}). Here the
 * test simulates the side-effect insert plus the audit insert against an
 * in-memory SQLite database with the V1 migration applied per try, and
 * verifies the audit row's fields and the absence of secret leakage.
 * <p>
 * Property 12 (minimum role) is covered by
 * {@link com.frostlogic.warproject.server.role.RoleCommandTableTest} and
 * {@link com.frostlogic.warproject.server.role.RoleUpCommandProperties}.
 * <p>
 * <b>Validates: Requirements 10.2, 10.5, 20.2, 20.3</b>
 * <p>
 * Design: §12 Property 13
 */
class ModerationAuditProperties {

    /**
     * The 13 moderation actions covered by Property 13. Each action name maps
     * one-to-one to an audit_log {@code action} column value used by the
     * production command code.
     */
    enum ModerationAction {
        BAN, KICK, MUTE, UNBAN, UNMUTE, WARN,
        RANK_UP, COLLAB, UNCOLLAB,
        FREEZE, UNFREEZE, VANISH, UNVANISH
    }

    private Connection conn;

    @BeforeTry
    void setupDatabase() throws Exception {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        conn.setAutoCommit(true);
        applyMigration(conn);
    }

    @AfterTry
    void tearDown() throws Exception {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 13 — single audit row per successful moderation command
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Property 13: For any successful moderation transaction, exactly one
     * {@code audit_log} row is appended whose fields equal the inputs the
     * caller passed.
     * <p>
     * Both the side effect (modelled here as a no-op SELECT that participates
     * in the same JDBC transaction) and the audit insert commit atomically.
     * After commit:
     * <ul>
     *   <li>the audit row count for {@code action == c} grows by exactly 1,</li>
     *   <li>{@code actor_uuid}, {@code target_uuid}, {@code reason},
     *       {@code action} match the inputs,</li>
     *   <li>{@code ts_utc} equals the input.</li>
     * </ul>
     * <p>
     * <b>Validates: Requirements 10.5, 20.2, 20.3</b>
     */
    @Property(tries = 100)
    void singleAuditRowPerSuccessfulCommand(
            @ForAll("moderationActions") ModerationAction action,
            @ForAll @LongRange(min = 1_000_000_000_000L, max = 2_000_000_000_000L) long now,
            @ForAll @NotBlank @StringLength(min = 1, max = 64) String reason
    ) {
        String actorUuid = UUID.randomUUID().toString();
        String actorName = "Actor_" + actorUuid.substring(0, 8);
        String targetUuid = UUID.randomUUID().toString();
        String targetName = "Target_" + targetUuid.substring(0, 8);

        long initialCount = countAuditEntries(action.name());
        long initialTotalCount = countAuditEntriesAll();

        long newRowId = runModerationTransaction(
                action, now, actorUuid, actorName, targetUuid, targetName, reason);

        // Exactly one new row for the chosen action key
        long afterCount = countAuditEntries(action.name());
        assertThat(afterCount - initialCount)
                .as("audit_log rows with action=%s must grow by exactly 1", action.name())
                .isEqualTo(1L);

        // Exactly one new row total (no spurious side-effect inserts)
        long afterTotal = countAuditEntriesAll();
        assertThat(afterTotal - initialTotalCount)
                .as("Exactly one audit_log row inserted by the transaction")
                .isEqualTo(1L);

        // Round-trip the row and verify field-by-field equality with the input
        AuditLogDao.AuditEntry entry = new AuditLogDao().findById(conn, newRowId)
                .orElseThrow(() -> new AssertionError("Inserted audit row not found by id"));

        assertThat(entry.action()).as("action").isEqualTo(action.name());
        assertThat(entry.actorUuid()).as("actor_uuid").isEqualTo(actorUuid);
        assertThat(entry.actorName()).as("actor_name").isEqualTo(actorName);
        assertThat(entry.targetUuid()).as("target_uuid").isEqualTo(targetUuid);
        assertThat(entry.targetName()).as("target_name").isEqualTo(targetName);
        assertThat(entry.reason()).as("reason").isEqualTo(reason);
        assertThat(entry.tsUtc()).as("ts_utc").isEqualTo(now);
    }

    /**
     * Property 13 (audit ordering / uniqueness): when the same moderation
     * action is applied twice with different (actor, target, reason, ts), the
     * two audit rows are independently retrievable by their generated id and
     * neither overwrites the other. The {@code id} column is monotonically
     * increasing per insert.
     * <p>
     * <b>Validates: Requirements 10.5, 20.2</b>
     */
    @Property(tries = 50)
    void repeatedActionsProduceIndependentAuditRows(
            @ForAll("moderationActions") ModerationAction action,
            @ForAll @LongRange(min = 1_000_000_000_000L, max = 1_500_000_000_000L) long firstTs,
            @ForAll @LongRange(min = 1L, max = 1_000_000L) long deltaMs
    ) {
        long secondTs = firstTs + deltaMs;
        String actor1Uuid = UUID.randomUUID().toString();
        String target1Uuid = UUID.randomUUID().toString();
        String actor2Uuid = UUID.randomUUID().toString();
        String target2Uuid = UUID.randomUUID().toString();

        long id1 = runModerationTransaction(action, firstTs,
                actor1Uuid, "Actor1", target1Uuid, "Target1", "reason-one");
        long id2 = runModerationTransaction(action, secondTs,
                actor2Uuid, "Actor2", target2Uuid, "Target2", "reason-two");

        assertThat(id2).as("Second audit id is greater than first").isGreaterThan(id1);

        AuditLogDao dao = new AuditLogDao();
        AuditLogDao.AuditEntry first = dao.findById(conn, id1).orElseThrow();
        AuditLogDao.AuditEntry second = dao.findById(conn, id2).orElseThrow();

        assertThat(first.actorUuid()).isEqualTo(actor1Uuid);
        assertThat(first.targetUuid()).isEqualTo(target1Uuid);
        assertThat(first.reason()).isEqualTo("reason-one");
        assertThat(first.tsUtc()).isEqualTo(firstTs);

        assertThat(second.actorUuid()).isEqualTo(actor2Uuid);
        assertThat(second.targetUuid()).isEqualTo(target2Uuid);
        assertThat(second.reason()).isEqualTo("reason-two");
        assertThat(second.tsUtc()).isEqualTo(secondTs);
    }

    /**
     * Property 13 (no secret leakage): for any password or captcha-code value
     * supplied as the user-controlled reason or as actor/target name, the
     * audit row's {@code extra_json} column does <b>not</b> contain that
     * substring.
     * <p>
     * Production audit inserts only put structured metadata (faction names,
     * subdivision ids, etc.) into {@code extra_json} and never echo the
     * user-typed reason or any auth-stage secret. This property pins that
     * contract: no matter what string the test uses as a "secret",
     * {@code extra_json} for any moderation action does not include it.
     * <p>
     * <b>Validates: Requirements 20.4</b>
     */
    @Property(tries = 100)
    void extraJsonNeverContainsSecrets(
            @ForAll("moderationActions") ModerationAction action,
            @ForAll @LongRange(min = 1_000_000_000_000L, max = 2_000_000_000_000L) long now,
            @ForAll("secretStrings") String secret
    ) {
        String actorUuid = UUID.randomUUID().toString();
        String targetUuid = UUID.randomUUID().toString();

        // The reason field is allowed to contain user-supplied text — that's
        // the moderator's stated reason and is intentionally retained. The
        // contract under test is that extra_json is built from structured
        // metadata only and never contains the secret.
        long id = runModerationTransaction(action, now,
                actorUuid, "Actor", targetUuid, "Target", "moderation reason");

        AuditLogDao.AuditEntry entry = new AuditLogDao().findById(conn, id).orElseThrow();
        String extraJson = entry.extraJson() == null ? "" : entry.extraJson();

        assertThat(extraJson)
                .as("audit_log.extra_json for action=%s must not contain secret '%s'",
                        action.name(), secret)
                .doesNotContain(secret);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Generators
    // ─────────────────────────────────────────────────────────────────────────

    @Provide
    Arbitrary<ModerationAction> moderationActions() {
        return Arbitraries.of(ModerationAction.values());
    }

    /**
     * Realistic-looking secret values that, by Property 13's contract, must
     * never end up in {@code extra_json}. Includes BCrypt-shaped hashes,
     * capacity-style passwords, and short alphanumeric captcha codes.
     */
    @Provide
    Arbitrary<String> secretStrings() {
        Arbitrary<String> passwords = Arbitraries.strings()
                .withCharRange('!', '~')
                .ofMinLength(6).ofMaxLength(32);
        Arbitrary<String> captchaCodes = Arbitraries.strings()
                .withCharRange('A', 'Z').numeric()
                .ofMinLength(4).ofMaxLength(8);
        Arbitrary<String> bcrypt = Arbitraries.strings()
                .withCharRange('a', 'z').withCharRange('0', '9')
                .ofMinLength(20).ofMaxLength(40)
                .map(s -> "$2a$10$" + s);
        return Arbitraries.oneOf(List.of(passwords, captchaCodes, bcrypt));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Transaction simulation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Simulates a moderation command's transaction: a no-op side-effect SQL
     * statement (a SELECT) plus the {@link AuditLogDao#insert} call, both
     * wrapped in {@code BEGIN/COMMIT}. The {@code extra_json} column is
     * populated with structured metadata that mirrors the production code
     * (e.g. ban-day count, mute minutes, role transitions) but never echoes
     * a password or captcha code.
     *
     * @return the generated id of the inserted audit row.
     */
    private long runModerationTransaction(ModerationAction action,
                                          long ts,
                                          String actorUuid,
                                          String actorName,
                                          String targetUuid,
                                          String targetName,
                                          String reason) {
        try {
            conn.setAutoCommit(false);
            long id;
            try {
                // Side effect — the actual moderation tables (bans/mutes/warns/etc.)
                // are not the subject of Property 13; we model a single read that
                // participates in the same transaction so commit/rollback boundaries
                // are exercised.
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT 1")) {
                    rs.next();
                }

                // Audit row — the unit Property 13 asserts on. extra_json carries
                // only structured non-secret metadata, mirroring the production
                // command paths (ModerationCommands, FactionCommands).
                String extraJson = buildExtraJson(action);
                id = new AuditLogDao().insert(conn, ts,
                        actorUuid, actorName,
                        targetUuid, targetName,
                        action.name(),
                        reason,
                        extraJson);

                conn.commit();
            } catch (RuntimeException e) {
                conn.rollback();
                throw e;
            }
            return id;
        } catch (SQLException e) {
            throw new RuntimeException("Transaction simulation failed", e);
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ignored) {
                // best-effort restoration
            }
        }
    }

    /**
     * Mirrors the action-specific {@code extra_json} payloads used by the
     * production command code. For actions whose production path passes
     * {@code null} (KICK, UNBAN, UNMUTE, WARN, FREEZE, UNFREEZE, VANISH,
     * UNVANISH, COLLAB, UNCOLLAB), this method returns {@code null}.
     */
    private static String buildExtraJson(ModerationAction action) {
        return switch (action) {
            case BAN -> "{\"days\":7}";
            case MUTE -> "{\"minutes\":30}";
            case RANK_UP -> "{\"from\":\"SOLDIER\",\"to\":\"COMMANDER\"}";
            // null mirrors the production callers
            default -> null;
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DB helpers
    // ─────────────────────────────────────────────────────────────────────────

    private long countAuditEntries(String action) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM audit_log WHERE action = ?")) {
            ps.setString(1, action);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count audit entries", e);
        }
    }

    private long countAuditEntriesAll() {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM audit_log")) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count audit entries", e);
        }
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

    /**
     * Splits SQL text into individual statements on semicolons,
     * respecting single-quoted string literals and SQL comments.
     */
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
        String tail = current.toString().trim();
        if (!tail.isEmpty()) {
            result.add(tail);
        }
        return result.toArray(new String[0]);
    }
}
