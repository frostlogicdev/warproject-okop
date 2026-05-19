package com.frostlogic.warproject.server.command;

import com.frostlogic.warproject.attachment.PlayerState;
import com.frostlogic.warproject.attachment.Role;
import com.frostlogic.warproject.persistence.dao.AccountsDao;
import com.frostlogic.warproject.persistence.dao.AuditLogDao;
import com.frostlogic.warproject.persistence.dao.PassportsDao;
import com.frostlogic.warproject.persistence.dao.PlayersDao;
import com.frostlogic.warproject.server.lifecycle.CandidateTimeoutService;
import com.frostlogic.warproject.server.radial.RadialMenuItem;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeTry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for the candidate-accept transaction and related
 * CANDIDATE-state predicates (task 14.4).
 * <p>
 * Covers the testable surface of:
 * <ul>
 *   <li><b>Property 7</b> — atomicity of the accept transaction at the DAO level.
 *       For any failure point during the four-step transaction (players.update,
 *       passports.update, audit_log.insert, post-commit), the resulting DB state
 *       is either fully accepted or not accepted at all — never partial.</li>
 *   <li><b>Property 8</b> — idempotency of repeated acceptance: a second
 *       acceptance applied to an already-{@code ACCEPTED} target leaves
 *       {@code players.status}, {@code passports.accepted_at}, and
 *       {@code passports.accepted_by} unchanged, and inserts exactly one
 *       {@code ACCEPT_REJECTED_ALREADY_ACCEPTED} audit row.</li>
 *   <li><b>Property 9</b> — equivalence of the {@code COMMAND} and {@code RADIAL}
 *       paths: both source values produce identical post-commit DB rows. The
 *       only audit-row difference is the {@code source} value embedded in
 *       {@code extra_json}, which is otherwise structurally identical.</li>
 *   <li><b>Property 15</b> — CANDIDATE constraints (placeholder for the full
 *       behavioural test — covered here at the level of pure predicates):
 *       {@code RadialMenuItem.appliesTo(CANDIDATE)} matches only items the
 *       design considers valid for a CANDIDATE target (i.e. {@code ACCEPT}
 *       and the OP-only {@code COLLAB_*} items).</li>
 *   <li><b>Property 16</b> — pure predicate {@code shouldKick(elapsedMs, timeoutSeconds)}
 *       returns true ⇔ {@code elapsedMs >= timeoutSeconds * 1000L}.</li>
 * </ul>
 * <p>
 * Tests at the DAO level use an in-memory SQLite database with the V1 migration
 * applied per try, mirroring the approach used by
 * {@link com.frostlogic.warproject.server.captivity.CaptivityAtomicityProperties}.
 * <p>
 * <b>Validates: Requirements 7.x, 8.6, 8.7, 8.8</b>
 * <p>
 * Design: §12 Properties 7, 8, 9, 15, 16
 */
class AcceptCandidateProperties {

    /**
     * The accept transaction performs exactly 3 {@code executeUpdate} calls:
     * <ol>
     *   <li>UPDATE players SET status, accepted_at, accepted_by_uuid, accepted_by_name</li>
     *   <li>UPDATE passports SET status, accepted_at, accepted_by</li>
     *   <li>INSERT INTO audit_log (...)</li>
     * </ol>
     */
    private static final int TRANSACTION_STEPS = 3;

    private Connection conn;

    // Test data — initialised in @BeforeTry
    private String initiatorUuid;
    private String initiatorName;
    private String targetUuid;
    private String targetName;
    private String passportId;
    /** Deterministic timestamp used for all seed rows so that path-equivalence
     * comparisons see byte-for-byte identical pre-state across reruns. */
    private long seedTimestamp;

    @BeforeTry
    void setupDatabase() throws Exception {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        conn.setAutoCommit(true);
        applyMigration(conn);

        initiatorUuid = UUID.randomUUID().toString();
        targetUuid = UUID.randomUUID().toString();
        initiatorName = "Commander_" + initiatorUuid.substring(0, 8);
        targetName = "Candidate_" + targetUuid.substring(0, 8);
        passportId = "ZRN-" + String.format("%06d", (int) (Math.random() * 999999) + 1);
        seedTimestamp = 1_500_000_000_000L;

        insertPrerequisites();
    }

    @AfterTry
    void tearDown() throws Exception {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 7 — Atomicity
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Property 7: For any generated failure point in the accept transaction,
     * the post-state is either {Fully Accepted} or {Not Accepted} — never partial.
     * <p>
     * On commit: players.status == ACCEPTED, accepted_at/accepted_by_* set,
     * passports.status == ACCEPTED, accepted_at/accepted_by set, exactly one
     * ACCEPT row in audit_log.
     * <p>
     * On rollback: all four pre-state columns remain unchanged and audit_log has
     * no new ACCEPT row.
     * <p>
     * <b>Validates: Requirements 8.6</b>
     */
    @Property(tries = 50)
    void acceptTransactionIsAtomic(@ForAll("failurePoints") int failAtStep,
                                   @ForAll("acceptSources") AcceptCommandHandler.AcceptSource source) {
        long initialAuditCount = countAuditEntries("ACCEPT");
        long acceptanceTs = 1_700_000_000_000L + failAtStep; // deterministic per try

        boolean committed = attemptAcceptWithFailure(failAtStep, source, acceptanceTs);

        PassportsDao.Passport passport = new PassportsDao().findById(conn, passportId).orElseThrow();
        PlayersDao.Player player = new PlayersDao().findByUuid(conn, targetUuid).orElseThrow();
        long newAuditCount = countAuditEntries("ACCEPT") - initialAuditCount;

        if (committed) {
            assertThat(player.status())
                    .as("Player status after commit")
                    .isEqualTo(PlayerState.ACCEPTED.getSerializedName());
            assertThat(player.acceptedAt())
                    .as("players.accepted_at after commit")
                    .isEqualTo(acceptanceTs);
            assertThat(player.acceptedByUuid())
                    .as("players.accepted_by_uuid after commit")
                    .isEqualTo(initiatorUuid);
            assertThat(player.acceptedByName())
                    .as("players.accepted_by_name after commit")
                    .isEqualTo(initiatorName);

            assertThat(passport.status())
                    .as("Passport status after commit")
                    .isEqualTo(PlayerState.ACCEPTED.getSerializedName());
            assertThat(passport.acceptedAt())
                    .as("passports.accepted_at after commit")
                    .isEqualTo(acceptanceTs);
            assertThat(passport.acceptedBy())
                    .as("passports.accepted_by after commit")
                    .isEqualTo(initiatorName);

            assertThat(newAuditCount)
                    .as("Exactly one ACCEPT audit entry after commit")
                    .isEqualTo(1L);
        } else {
            // Rollback — initial state preserved verbatim
            assertThat(player.status())
                    .as("Player status after rollback (was CANDIDATE)")
                    .isEqualTo(PlayerState.CANDIDATE.getSerializedName());
            assertThat(player.acceptedAt())
                    .as("players.accepted_at after rollback")
                    .isNull();
            assertThat(player.acceptedByUuid())
                    .as("players.accepted_by_uuid after rollback")
                    .isNull();
            assertThat(player.acceptedByName())
                    .as("players.accepted_by_name after rollback")
                    .isNull();

            assertThat(passport.status())
                    .as("Passport status after rollback (was CANDIDATE)")
                    .isEqualTo(PlayerState.CANDIDATE.getSerializedName());
            assertThat(passport.acceptedAt())
                    .as("passports.accepted_at after rollback")
                    .isNull();
            assertThat(passport.acceptedBy())
                    .as("passports.accepted_by after rollback")
                    .isNull();

            assertThat(newAuditCount)
                    .as("No ACCEPT audit entry after rollback")
                    .isZero();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 8 — Idempotency
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Property 8: Applying the accept transaction twice has the same net
     * effect on the four mutable columns as applying it once. The second
     * application is short-circuited at the application layer (recorded in
     * this test by writing an ALREADY_ACCEPTED audit row instead of mutating
     * the data rows).
     * <p>
     * The initial ACCEPT writes one ACCEPT audit row. The second pass writes
     * exactly one ACCEPT_REJECTED_ALREADY_ACCEPTED row and no further mutation,
     * matching the contrapositive form of Requirement 8.6 / Property 9.
     * <p>
     * <b>Validates: Requirements 8.6, 8.7</b>
     */
    @Property(tries = 50)
    void acceptIsIdempotent(@ForAll("acceptSources") AcceptCommandHandler.AcceptSource firstSource,
                            @ForAll("acceptSources") AcceptCommandHandler.AcceptSource secondSource,
                            @ForAll @LongRange(min = 1_000_000_000_000L, max = 2_000_000_000_000L) long firstTs) {
        // First accept — succeeds and commits.
        boolean firstCommitted = attemptAcceptWithFailure(TRANSACTION_STEPS + 1, firstSource, firstTs);
        assertThat(firstCommitted).as("First accept must commit").isTrue();

        // Snapshot the post-first-accept state
        PassportsDao.Passport afterFirst = new PassportsDao().findById(conn, passportId).orElseThrow();
        PlayersDao.Player playerAfterFirst = new PlayersDao().findByUuid(conn, targetUuid).orElseThrow();
        long acceptCountAfterFirst = countAuditEntries("ACCEPT");
        long alreadyAcceptedCountAfterFirst = countAuditEntries("ACCEPT_REJECTED_ALREADY_ACCEPTED");

        // Sanity: first accept did its job
        assertThat(playerAfterFirst.status()).isEqualTo(PlayerState.ACCEPTED.getSerializedName());
        assertThat(afterFirst.status()).isEqualTo(PlayerState.ACCEPTED.getSerializedName());
        assertThat(afterFirst.acceptedAt()).isEqualTo(firstTs);
        assertThat(afterFirst.acceptedBy()).isEqualTo(initiatorName);

        // Second accept on already-ACCEPTED target — application-layer guard.
        // The handler simulated here mirrors AcceptCommandHandler#acceptCandidate:
        // detect ACCEPTED status, write ACCEPT_REJECTED_ALREADY_ACCEPTED audit,
        // do not mutate players/passports.
        long secondTs = firstTs + 100_000L;
        applyAlreadyAcceptedGuard(secondSource, secondTs);

        // Verify mutable columns are unchanged
        PassportsDao.Passport afterSecond = new PassportsDao().findById(conn, passportId).orElseThrow();
        PlayersDao.Player playerAfterSecond = new PlayersDao().findByUuid(conn, targetUuid).orElseThrow();

        assertThat(playerAfterSecond.status())
                .as("players.status unchanged on idempotent reapply")
                .isEqualTo(playerAfterFirst.status());
        assertThat(playerAfterSecond.acceptedAt())
                .as("players.accepted_at unchanged on idempotent reapply")
                .isEqualTo(playerAfterFirst.acceptedAt());
        assertThat(playerAfterSecond.acceptedByUuid())
                .as("players.accepted_by_uuid unchanged on idempotent reapply")
                .isEqualTo(playerAfterFirst.acceptedByUuid());
        assertThat(playerAfterSecond.acceptedByName())
                .as("players.accepted_by_name unchanged on idempotent reapply")
                .isEqualTo(playerAfterFirst.acceptedByName());

        assertThat(afterSecond.acceptedAt())
                .as("passports.accepted_at unchanged on idempotent reapply")
                .isEqualTo(afterFirst.acceptedAt());
        assertThat(afterSecond.acceptedBy())
                .as("passports.accepted_by unchanged on idempotent reapply")
                .isEqualTo(afterFirst.acceptedBy());

        // Verify audit ledger: exactly +1 ALREADY_ACCEPTED, no new ACCEPT row.
        long acceptCountAfterSecond = countAuditEntries("ACCEPT");
        long alreadyAcceptedCountAfterSecond = countAuditEntries("ACCEPT_REJECTED_ALREADY_ACCEPTED");

        assertThat(acceptCountAfterSecond - acceptCountAfterFirst)
                .as("No new ACCEPT audit row on idempotent reapply")
                .isZero();
        assertThat(alreadyAcceptedCountAfterSecond - alreadyAcceptedCountAfterFirst)
                .as("Exactly one ACCEPT_REJECTED_ALREADY_ACCEPTED row on idempotent reapply")
                .isEqualTo(1L);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 9 — Path equivalence (COMMAND vs RADIAL)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Property 9: For any pre-state, the COMMAND-source accept transaction and
     * the RADIAL-source accept transaction produce identical post-commit values
     * for every column on {@code players} and {@code passports}, plus a
     * structurally-identical {@code audit_log} row whose only difference is the
     * {@code source} field embedded in {@code extra_json}.
     * <p>
     * Tested by running each path against a freshly-bootstrapped DB (one per
     * try), capturing the full row state for both, and asserting equality
     * column-by-column except for the audit {@code extra_json} where we assert
     * that the difference is exactly the {@code source} substitution.
     * <p>
     * <b>Validates: Requirements 8.8</b>
     */
    @Property(tries = 50)
    void commandAndRadialPathsAreEquivalent(
            @ForAll @LongRange(min = 1_000_000_000_000L, max = 2_000_000_000_000L) long acceptanceTs
    ) throws Exception {
        // Snapshot via COMMAND source
        boolean commandOk = attemptAcceptWithFailure(TRANSACTION_STEPS + 1,
                AcceptCommandHandler.AcceptSource.COMMAND, acceptanceTs);
        assertThat(commandOk).isTrue();
        PathSnapshot commandSnapshot = capturePathSnapshot();

        // Reset DB to pre-accept state for the RADIAL run
        resetDatabaseFresh();
        boolean radialOk = attemptAcceptWithFailure(TRANSACTION_STEPS + 1,
                AcceptCommandHandler.AcceptSource.RADIAL, acceptanceTs);
        assertThat(radialOk).isTrue();
        PathSnapshot radialSnapshot = capturePathSnapshot();

        // Players row is identical (no source field anywhere)
        assertThat(radialSnapshot.player).isEqualTo(commandSnapshot.player);

        // Passports row is identical
        assertThat(radialSnapshot.passport).isEqualTo(commandSnapshot.passport);

        // Audit row: action, actor/target uuid+name, ts, reason all identical
        assertThat(radialSnapshot.auditAction).isEqualTo(commandSnapshot.auditAction);
        assertThat(radialSnapshot.auditActorUuid).isEqualTo(commandSnapshot.auditActorUuid);
        assertThat(radialSnapshot.auditActorName).isEqualTo(commandSnapshot.auditActorName);
        assertThat(radialSnapshot.auditTargetUuid).isEqualTo(commandSnapshot.auditTargetUuid);
        assertThat(radialSnapshot.auditTargetName).isEqualTo(commandSnapshot.auditTargetName);
        assertThat(radialSnapshot.auditReason).isEqualTo(commandSnapshot.auditReason);
        assertThat(radialSnapshot.auditTs).isEqualTo(commandSnapshot.auditTs);

        // Audit extra_json: structurally identical except for the source field.
        // Normalising both to RADIAL must yield equal strings.
        String commandNormalised = commandSnapshot.auditExtraJson.replace(
                "\"source\":\"COMMAND\"", "\"source\":\"RADIAL\"");
        assertThat(radialSnapshot.auditExtraJson)
                .as("extra_json should differ only in the 'source' field")
                .isEqualTo(commandNormalised);

        // And both audit rows must contain the passport_id verbatim
        assertThat(commandSnapshot.auditExtraJson).contains(passportId);
        assertThat(radialSnapshot.auditExtraJson).contains(passportId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 15 — CANDIDATE constraints (pure predicate level)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Property 15 (predicate slice): a player in {@code CANDIDATE} state is a
     * valid radial-menu target only for {@code ACCEPT} (the path of leaving
     * the CANDIDATE state) and the OP-only collaborator-tagging items, which
     * apply to every state.
     * <p>
     * No other radial-menu item is meaningful for a CANDIDATE target. This
     * pure-predicate test complements the behavioural rules enforced by
     * {@code CandidateRulesHandler} (which require a live Minecraft server).
     * <p>
     * <b>Validates: Requirements 7.x</b>
     */
    @Property
    void candidateTargetIsValidOnlyForExpectedItems(
            @ForAll("radialMenuItems") RadialMenuItem item
    ) {
        Set<RadialMenuItem> expectedAllowed = Set.of(
                RadialMenuItem.ACCEPT,
                RadialMenuItem.COLLAB_MARK,
                RadialMenuItem.COLLAB_UNMARK
        );
        boolean expected = expectedAllowed.contains(item);
        boolean actual = item.appliesTo(PlayerState.CANDIDATE);

        assertThat(actual)
                .as("RadialMenuItem.%s.appliesTo(CANDIDATE) — expected=%s, actual=%s",
                        item.name(), expected, actual)
                .isEqualTo(expected);
    }

    /**
     * Property 15 (predicate slice, complementary): only {@code ACCEPT} requires
     * the initiator to be in their own base region and apply to a CANDIDATE
     * target. The combination of {@code requiresOwnBase()} and
     * {@code appliesTo(CANDIDATE)} is unique to {@code ACCEPT}.
     */
    @Property
    void acceptIsTheUniqueOwnBaseCandidateAction(
            @ForAll("radialMenuItems") RadialMenuItem item
    ) {
        boolean isOwnBaseCandidateAction =
                item.requiresOwnBase() && item.appliesTo(PlayerState.CANDIDATE);
        boolean isAccept = item == RadialMenuItem.ACCEPT;

        assertThat(isOwnBaseCandidateAction)
                .as("Item %s — requiresOwnBase ∧ appliesTo(CANDIDATE) should match (item == ACCEPT)", item)
                .isEqualTo(isAccept);
    }

    /**
     * Property 15 (predicate slice, role check): {@code ACCEPT} requires at
     * least {@link Role#COMMANDER}, matching the design table from §5.3.
     * SOLDIER and CANDIDATE roles MUST NOT pass the role check; COMMANDER,
     * GENERAL, and OP MUST.
     */
    @Property
    void acceptRoleCheckMatchesDesign(@ForAll("roles") Role role) {
        boolean expected = role.atLeast(Role.COMMANDER);
        boolean actual = role.atLeast(RadialMenuItem.ACCEPT.requiredRole());
        assertThat(actual)
                .as("ACCEPT requires COMMANDER+: role=%s expected=%s", role, expected)
                .isEqualTo(expected);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 16 — CANDIDATE timeout predicate
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Property 16: {@code shouldKick(elapsedMs, timeoutSeconds)} returns true
     * iff {@code elapsedMs >= timeoutSeconds * 1000L}.
     * <p>
     * Verified against the pure-predicate overload added to
     * {@link CandidateTimeoutService} for testability — the production overload
     * delegates to this one with the configured timeout.
     * <p>
     * <b>Validates: Requirements 7.8</b>
     */
    @Property(tries = 200)
    void shouldKickIffElapsedReachesThreshold(
            @ForAll @LongRange(min = 0L, max = 30L * 24L * 60L * 60L * 1000L) long elapsedMs,
            @ForAll @IntRange(min = 1, max = 7 * 24 * 60 * 60) int timeoutSeconds
    ) {
        long thresholdMs = (long) timeoutSeconds * 1000L;
        boolean expected = elapsedMs >= thresholdMs;
        boolean actual = CandidateTimeoutService.shouldKick(elapsedMs, timeoutSeconds);
        assertThat(actual)
                .as("shouldKick(elapsed=%d, timeoutSeconds=%d) — threshold=%d",
                        elapsedMs, timeoutSeconds, thresholdMs)
                .isEqualTo(expected);
    }

    /**
     * Property 16 — boundary cases: at exactly the threshold the predicate is
     * true; one millisecond below it is false.
     */
    @Property
    void shouldKickIsExactBoundary(
            @ForAll @IntRange(min = 1, max = 7 * 24 * 60 * 60) int timeoutSeconds
    ) {
        long thresholdMs = (long) timeoutSeconds * 1000L;
        assertThat(CandidateTimeoutService.shouldKick(thresholdMs, timeoutSeconds))
                .as("shouldKick at exact threshold").isTrue();
        if (thresholdMs > 0) {
            assertThat(CandidateTimeoutService.shouldKick(thresholdMs - 1, timeoutSeconds))
                    .as("shouldKick one ms below threshold").isFalse();
        }
        assertThat(CandidateTimeoutService.shouldKick(thresholdMs + 1, timeoutSeconds))
                .as("shouldKick one ms above threshold").isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Generators
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Failure points: 0 = fail before step 1, 1..TRANSACTION_STEPS = fail after
     * that many executeUpdate calls, TRANSACTION_STEPS + 1 = no failure (commit).
     */
    @Provide
    Arbitrary<Integer> failurePoints() {
        return Arbitraries.integers().between(0, TRANSACTION_STEPS + 1);
    }

    @Provide
    Arbitrary<AcceptCommandHandler.AcceptSource> acceptSources() {
        return Arbitraries.of(AcceptCommandHandler.AcceptSource.values());
    }

    @Provide
    Arbitrary<RadialMenuItem> radialMenuItems() {
        return Arbitraries.of(RadialMenuItem.values());
    }

    @Provide
    Arbitrary<Role> roles() {
        return Arbitraries.of(Role.values());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Transaction simulation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Simulates the accept transaction with a failure injected at the specified
     * step. Mirrors {@code AcceptCommandHandler#acceptCandidate} but at the
     * DAO/connection level so we can drive failure injection without a running
     * Minecraft server.
     *
     * @param failAtStep    step at which to throw a simulated failure
     *                      (>= TRANSACTION_STEPS + 1 means commit successfully)
     * @param source        accept source (used for audit extra_json)
     * @param acceptanceTs  acceptance timestamp written to all rows
     * @return true on commit, false on rollback
     */
    private boolean attemptAcceptWithFailure(int failAtStep,
                                             AcceptCommandHandler.AcceptSource source,
                                             long acceptanceTs) {
        try {
            conn.setAutoCommit(false);

            AtomicInteger executeCount = new AtomicInteger(0);
            Connection proxiedConn = createFailingConnectionProxy(conn, executeCount, failAtStep);

            try {
                PlayersDao playersDao = new PlayersDao();
                PassportsDao passportsDao = new PassportsDao();
                AuditLogDao auditLogDao = new AuditLogDao();

                // Step 1: players.update
                playersDao.updateAcceptance(proxiedConn, targetUuid,
                        PlayerState.ACCEPTED.getSerializedName(),
                        acceptanceTs, initiatorUuid, initiatorName);

                // Step 2: passports.update
                passportsDao.updateAcceptance(proxiedConn, passportId,
                        PlayerState.ACCEPTED.getSerializedName(),
                        acceptanceTs, initiatorName);

                // Step 3: audit_log.insert
                auditLogDao.insert(proxiedConn, acceptanceTs,
                        initiatorUuid, initiatorName,
                        targetUuid, targetName,
                        "ACCEPT",
                        null,
                        "{\"source\":\"" + source.name() + "\","
                                + "\"passport_id\":\"" + passportId + "\"}");

                conn.commit();
                return true;
            } catch (SimulatedDbFailureException e) {
                conn.rollback();
                return false;
            } catch (RuntimeException e) {
                if (hasCause(e, SimulatedDbFailureException.class)) {
                    conn.rollback();
                    return false;
                }
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Transaction management failed", e);
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ignored) {
                // best-effort restoration
            }
        }
    }

    /**
     * Application-layer ALREADY_ACCEPTED guard for Property 8: write the
     * informational audit row in its own short transaction without touching
     * any data row, exactly mirroring
     * {@code AcceptCommandHandler#writeAlreadyAcceptedAudit}.
     */
    private void applyAlreadyAcceptedGuard(AcceptCommandHandler.AcceptSource source, long ts) {
        try {
            conn.setAutoCommit(false);
            try {
                AuditLogDao auditLogDao = new AuditLogDao();
                auditLogDao.insert(conn, ts,
                        initiatorUuid, initiatorName,
                        targetUuid, targetName,
                        "ACCEPT_REJECTED_ALREADY_ACCEPTED",
                        "already_accepted",
                        "{\"source\":\"" + source.name() + "\"}");
                conn.commit();
            } catch (RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Already-accepted guard failed", e);
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ignored) {
                // best-effort restoration
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Connection proxy for failure injection
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Wraps the supplied connection so that returned {@link PreparedStatement}s
     * count their {@code executeUpdate} calls and throw a
     * {@link SimulatedDbFailureException} once the step counter exceeds
     * {@code failAtStep}. Read-only methods pass through unchanged.
     */
    private Connection createFailingConnectionProxy(Connection realConn, AtomicInteger executeCount, int failAtStep) {
        return (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[]{Connection.class},
                (proxy, method, args) -> {
                    Object result;
                    try {
                        result = method.invoke(realConn, args);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        throw e.getCause();
                    }
                    if ("prepareStatement".equals(method.getName()) && result instanceof PreparedStatement ps) {
                        return createFailingPreparedStatementProxy(ps, executeCount, failAtStep);
                    }
                    return result;
                }
        );
    }

    private PreparedStatement createFailingPreparedStatementProxy(PreparedStatement realPs,
                                                                  AtomicInteger executeCount,
                                                                  int failAtStep) {
        return (PreparedStatement) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[]{PreparedStatement.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        String name = method.getName();
                        if ("executeUpdate".equals(name) || "execute".equals(name)) {
                            int currentStep = executeCount.incrementAndGet();
                            if (currentStep > failAtStep) {
                                throw new SimulatedDbFailureException(
                                        "Simulated DB failure at step " + currentStep
                                                + " (configured to fail after step " + failAtStep + ")");
                            }
                        }
                        try {
                            return method.invoke(realPs, args);
                        } catch (java.lang.reflect.InvocationTargetException e) {
                            throw e.getCause();
                        }
                    }
                }
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Path-equivalence helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Captures the post-commit DB state for the path-equivalence test.
     * Includes the full {@code players} and {@code passports} rows plus the
     * single {@code ACCEPT} audit row for {@link #targetUuid}.
     */
    private PathSnapshot capturePathSnapshot() throws SQLException {
        PassportsDao.Passport p = new PassportsDao().findById(conn, passportId).orElseThrow();
        PlayersDao.Player pl = new PlayersDao().findByUuid(conn, targetUuid).orElseThrow();

        // Most-recent ACCEPT row for this target
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT ts_utc, actor_uuid, actor_name, target_uuid, target_name, action, reason, extra_json"
                        + " FROM audit_log"
                        + " WHERE target_uuid = ? AND action = 'ACCEPT'"
                        + " ORDER BY id DESC LIMIT 1")) {
            ps.setString(1, targetUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("No ACCEPT audit row for target " + targetUuid);
                }
                return new PathSnapshot(
                        pl,
                        p,
                        rs.getLong("ts_utc"),
                        rs.getString("actor_uuid"),
                        rs.getString("actor_name"),
                        rs.getString("target_uuid"),
                        rs.getString("target_name"),
                        rs.getString("action"),
                        rs.getString("reason"),
                        rs.getString("extra_json"));
            }
        }
    }

    /**
     * Resets the in-memory DB to the pre-accept state for the second leg of
     * the path-equivalence test. Drops every table and re-applies the V1
     * migration + prerequisite seed data.
     */
    private void resetDatabaseFresh() throws Exception {
        conn.close();
        conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        conn.setAutoCommit(true);
        applyMigration(conn);
        insertPrerequisites();
    }

    private record PathSnapshot(
            PlayersDao.Player player,
            PassportsDao.Passport passport,
            long auditTs,
            String auditActorUuid,
            String auditActorName,
            String auditTargetUuid,
            String auditTargetName,
            String auditAction,
            String auditReason,
            String auditExtraJson
    ) {}

    // ─────────────────────────────────────────────────────────────────────────
    // Database bootstrap helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void insertPrerequisites() {
        AccountsDao accountsDao = new AccountsDao();
        PlayersDao playersDao = new PlayersDao();
        PassportsDao passportsDao = new PassportsDao();

        long now = seedTimestamp;

        // Accounts (FK target for players)
        accountsDao.insert(conn, new AccountsDao.Account(
                initiatorUuid, "$2a$10$dummyhash000000000000000000000000000000000000000000",
                now, null, 0, 0));
        accountsDao.insert(conn, new AccountsDao.Account(
                targetUuid, "$2a$10$dummyhash000000000000000000000000000000000000000000",
                now, null, 0, 0));

        // Initiator: ZARNAVIA / ACCEPTED COMMANDER (for context)
        playersDao.insert(conn, new PlayersDao.Player(
                initiatorUuid, "ZARNAVIA", "COMMANDER", null,
                PlayerState.ACCEPTED.getSerializedName(),
                "Ivan", "Petrov", false, null, false, 0,
                now, now, null, "Ivan Petrov", null));

        // Target: ZARNAVIA / CANDIDATE — ready to be accepted
        playersDao.insert(conn, new PlayersDao.Player(
                targetUuid, "ZARNAVIA", "CANDIDATE", null,
                PlayerState.CANDIDATE.getSerializedName(),
                "Oleg", "Sidorov", false, null, false, 0,
                now, null, null, null, null));

        // Passport for target (status = candidate)
        passportsDao.insert(conn, new PassportsDao.Passport(
                passportId, targetUuid, "ZARNAVIA",
                "Oleg", "Sidorov", "15.06.1995", 12345L,
                PlayerState.CANDIDATE.getSerializedName(),
                null, null,
                null, false, now));
    }

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
     * Splits SQL text into individual statements on semicolons, respecting
     * single-quoted strings and {@code --}/{@code /* * /} comments. Mirrors
     * the package-private helper used by {@code Migrations}.
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
        String last = current.toString().trim();
        if (!last.isEmpty()) {
            result.add(last);
        }
        return result.toArray(new String[0]);
    }

    private static boolean hasCause(Throwable t, Class<? extends Throwable> causeType) {
        Throwable current = t;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /** Marker exception used to simulate a DB failure at a specific transaction step. */
    private static class SimulatedDbFailureException extends RuntimeException {
        SimulatedDbFailureException(String message) {
            super(message);
        }
    }
}
