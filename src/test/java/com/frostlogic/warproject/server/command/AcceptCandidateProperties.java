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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for the candidate-accept transaction and related
 * CANDIDATE-state predicates (task 14.4).
 */
class AcceptCandidateProperties {

    private static final int TRANSACTION_STEPS = 3;

    private Connection conn;

    private String initiatorUuid;
    private String initiatorName;
    private String targetUuid;
    private String targetName;
    private String passportId;
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

    @Property(tries = 50)
    void acceptTransactionIsAtomic(@ForAll("failurePoints") int failAtStep,
                                   @ForAll("acceptSources") AcceptCommandHandler.AcceptSource source) {
        long initialAuditCount = countAuditEntries("ACCEPT");
        long acceptanceTs = 1_700_000_000_000L + failAtStep;

        boolean committed = attemptAcceptWithFailure(failAtStep, source, acceptanceTs);

        PassportsDao.Passport passport = new PassportsDao().findById(conn, passportId).orElseThrow();
        PlayersDao.Player player = new PlayersDao().findByUuid(conn, targetUuid).orElseThrow();
        long newAuditCount = countAuditEntries("ACCEPT") - initialAuditCount;

        if (committed) {
            assertThat(player.status()).isEqualTo(PlayerState.ACCEPTED.getSerializedName());
            assertThat(player.acceptedAt()).isEqualTo(acceptanceTs);
            assertThat(player.acceptedByUuid()).isEqualTo(initiatorUuid);
            assertThat(player.acceptedByName()).isEqualTo(initiatorName);

            assertThat(passport.status()).isEqualTo(PlayerState.ACCEPTED.getSerializedName());
            assertThat(passport.acceptedAt()).isEqualTo(acceptanceTs);
            assertThat(passport.acceptedBy()).isEqualTo(initiatorName);

            assertThat(newAuditCount).isEqualTo(1L);
        } else {
            assertThat(player.status()).isEqualTo(PlayerState.CANDIDATE.getSerializedName());
            assertThat(player.acceptedAt()).isNull();
            assertThat(player.acceptedByUuid()).isNull();
            assertThat(player.acceptedByName()).isNull();

            assertThat(passport.status()).isEqualTo(PlayerState.CANDIDATE.getSerializedName());
            assertThat(passport.acceptedAt()).isNull();
            assertThat(passport.acceptedBy()).isNull();

            assertThat(newAuditCount).isZero();
        }
    }

    @Property(tries = 50)
    void acceptIsIdempotent(@ForAll("acceptSources") AcceptCommandHandler.AcceptSource firstSource,
                            @ForAll("acceptSources") AcceptCommandHandler.AcceptSource secondSource,
                            @ForAll @LongRange(min = 1_000_000_000_000L, max = 2_000_000_000_000L) long firstTs) {
        boolean firstCommitted = attemptAcceptWithFailure(TRANSACTION_STEPS + 1, firstSource, firstTs);
        assertThat(firstCommitted).isTrue();

        PassportsDao.Passport afterFirst = new PassportsDao().findById(conn, passportId).orElseThrow();
        PlayersDao.Player playerAfterFirst = new PlayersDao().findByUuid(conn, targetUuid).orElseThrow();
        long acceptCountAfterFirst = countAuditEntries("ACCEPT");
        long alreadyAcceptedCountAfterFirst = countAuditEntries("ACCEPT_REJECTED_ALREADY_ACCEPTED");

        assertThat(playerAfterFirst.status()).isEqualTo(PlayerState.ACCEPTED.getSerializedName());
        assertThat(afterFirst.status()).isEqualTo(PlayerState.ACCEPTED.getSerializedName());
        assertThat(afterFirst.acceptedAt()).isEqualTo(firstTs);
        assertThat(afterFirst.acceptedBy()).isEqualTo(initiatorName);

        long secondTs = firstTs + 100_000L;
        applyAlreadyAcceptedGuard(secondSource, secondTs);

        PassportsDao.Passport afterSecond = new PassportsDao().findById(conn, passportId).orElseThrow();
        PlayersDao.Player playerAfterSecond = new PlayersDao().findByUuid(conn, targetUuid).orElseThrow();

        assertThat(playerAfterSecond.status()).isEqualTo(playerAfterFirst.status());
        assertThat(playerAfterSecond.acceptedAt()).isEqualTo(playerAfterFirst.acceptedAt());
        assertThat(playerAfterSecond.acceptedByUuid()).isEqualTo(playerAfterFirst.acceptedByUuid());
        assertThat(playerAfterSecond.acceptedByName()).isEqualTo(playerAfterFirst.acceptedByName());

        assertThat(afterSecond.acceptedAt()).isEqualTo(afterFirst.acceptedAt());
        assertThat(afterSecond.acceptedBy()).isEqualTo(afterFirst.acceptedBy());

        long acceptCountAfterSecond = countAuditEntries("ACCEPT");
        long alreadyAcceptedCountAfterSecond = countAuditEntries("ACCEPT_REJECTED_ALREADY_ACCEPTED");

        assertThat(acceptCountAfterSecond - acceptCountAfterFirst).isZero();
        assertThat(alreadyAcceptedCountAfterSecond - alreadyAcceptedCountAfterFirst).isEqualTo(1L);
    }

    @Property(tries = 50)
    void commandAndRadialPathsAreEquivalent(
            @ForAll @LongRange(min = 1_000_000_000_000L, max = 2_000_000_000_000L) long acceptanceTs
    ) throws Exception {
        boolean commandOk = attemptAcceptWithFailure(TRANSACTION_STEPS + 1,
                AcceptCommandHandler.AcceptSource.COMMAND, acceptanceTs);
        assertThat(commandOk).isTrue();
        PathSnapshot commandSnapshot = capturePathSnapshot();

        resetDatabaseFresh();
        boolean radialOk = attemptAcceptWithFailure(TRANSACTION_STEPS + 1,
                AcceptCommandHandler.AcceptSource.RADIAL, acceptanceTs);
        assertThat(radialOk).isTrue();
        PathSnapshot radialSnapshot = capturePathSnapshot();

        assertThat(radialSnapshot.player).isEqualTo(commandSnapshot.player);
        assertThat(radialSnapshot.passport).isEqualTo(commandSnapshot.passport);

        assertThat(radialSnapshot.auditAction).isEqualTo(commandSnapshot.auditAction);
        assertThat(radialSnapshot.auditActorUuid).isEqualTo(commandSnapshot.auditActorUuid);
        assertThat(radialSnapshot.auditActorName).isEqualTo(commandSnapshot.auditActorName);
        assertThat(radialSnapshot.auditTargetUuid).isEqualTo(commandSnapshot.auditTargetUuid);
        assertThat(radialSnapshot.auditTargetName).isEqualTo(commandSnapshot.auditTargetName);
        assertThat(radialSnapshot.auditReason).isEqualTo(commandSnapshot.auditReason);
        assertThat(radialSnapshot.auditTs).isEqualTo(commandSnapshot.auditTs);

        String commandNormalised = commandSnapshot.auditExtraJson.replace(
                "\"source\":\"COMMAND\"", "\"source\":\"RADIAL\"");
        assertThat(radialSnapshot.auditExtraJson).isEqualTo(commandNormalised);

        assertThat(commandSnapshot.auditExtraJson).contains(passportId);
        assertThat(radialSnapshot.auditExtraJson).contains(passportId);
    }

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

        assertThat(actual).isEqualTo(expected);
    }

    @Property
    void acceptIsTheUniqueOwnBaseCandidateAction(
            @ForAll("radialMenuItems") RadialMenuItem item
    ) {
        boolean isOwnBaseCandidateAction =
                item.requiresOwnBase() && item.appliesTo(PlayerState.CANDIDATE);
        boolean isAccept = item == RadialMenuItem.ACCEPT;

        assertThat(isOwnBaseCandidateAction).isEqualTo(isAccept);
    }

    @Property
    void acceptRoleCheckMatchesDesign(@ForAll("roles") Role role) {
        boolean expected = role.atLeast(Role.COMMANDER);
        boolean actual = role.atLeast(RadialMenuItem.ACCEPT.requiredRole());
        assertThat(actual).isEqualTo(expected);
    }

    @Property(tries = 200)
    void shouldKickIffElapsedReachesThreshold(
            @ForAll @LongRange(min = 0L, max = 30L * 24L * 60L * 60L * 1000L) long elapsedMs,
            @ForAll @IntRange(min = 1, max = 7 * 24 * 60 * 60) int timeoutSeconds
    ) {
        long thresholdMs = (long) timeoutSeconds * 1000L;
        boolean expected = elapsedMs >= thresholdMs;
        boolean actual = CandidateTimeoutService.shouldKick(elapsedMs, timeoutSeconds);
        assertThat(actual).isEqualTo(expected);
    }

    @Property
    void shouldKickIsExactBoundary(
            @ForAll @IntRange(min = 1, max = 7 * 24 * 60 * 60) int timeoutSeconds
    ) {
        long thresholdMs = (long) timeoutSeconds * 1000L;
        assertThat(CandidateTimeoutService.shouldKick(thresholdMs, timeoutSeconds)).isTrue();
        if (thresholdMs > 0) {
            assertThat(CandidateTimeoutService.shouldKick(thresholdMs - 1, timeoutSeconds)).isFalse();
        }
        assertThat(CandidateTimeoutService.shouldKick(thresholdMs + 1, timeoutSeconds)).isTrue();
    }

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

                playersDao.updateAcceptance(proxiedConn, targetUuid,
                        PlayerState.ACCEPTED.getSerializedName(),
                        acceptanceTs, initiatorUuid, initiatorName);

                passportsDao.updateAcceptance(proxiedConn, passportId,
                        PlayerState.ACCEPTED.getSerializedName(),
                        acceptanceTs, initiatorName);

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
            }
        }
    }

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
            }
        }
    }

    private Connection createFailingConnectionProxy(Connection realConn, AtomicInteger executeCount, int failAtStep) {
        return (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Connection.class},
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
                new Class<?>[]{PreparedStatement.class},
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

    private PathSnapshot capturePathSnapshot() throws SQLException {
        PassportsDao.Passport p = new PassportsDao().findById(conn, passportId).orElseThrow();
        PlayersDao.Player pl = new PlayersDao().findByUuid(conn, targetUuid).orElseThrow();

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

    private void insertPrerequisites() {
        AccountsDao accountsDao = new AccountsDao();
        PlayersDao playersDao = new PlayersDao();
        PassportsDao passportsDao = new PassportsDao();

        long now = seedTimestamp;

        accountsDao.insert(conn, new AccountsDao.Account(
                initiatorUuid, "$2a$10$dummyhash000000000000000000000000000000000000000000",
                now, null, 0, 0));
        accountsDao.insert(conn, new AccountsDao.Account(
                targetUuid, "$2a$10$dummyhash000000000000000000000000000000000000000000",
                now, null, 0, 0));

        playersDao.insert(conn, new PlayersDao.Player(
                initiatorUuid, "ZARNAVIA", "COMMANDER", null,
                PlayerState.ACCEPTED.getSerializedName(),
                "Ivan", "Petrov", false, null, false, 0,
                now, now, null, "Ivan Petrov", null));

        playersDao.insert(conn, new PlayersDao.Player(
                targetUuid, "ZARNAVIA", "CANDIDATE", null,
                PlayerState.CANDIDATE.getSerializedName(),
                "Oleg", "Sidorov", false, null, false, 0,
                now, null, null, null, null));

        passportsDao.insert(conn, new PassportsDao.Passport(
                passportId, targetUuid, "ZARNAVIA",
                "Oleg", "Sidorov", "15.06.1995", 12345L,
                PlayerState.CANDIDATE.getSerializedName(),
                null, null,
                null, null, false, now));
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
        String[] migrationFiles = {"V1__init.sql", "V2__military_features.sql", "V3__captivity_timeout.sql"};
        for (String migrationFile : migrationFiles) {
            InputStream is = getClass().getClassLoader().getResourceAsStream("db/migrations/" + migrationFile);
            if (is == null) {
                throw new IOException(migrationFile + " not found on classpath");
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
    }

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

    private static class SimulatedDbFailureException extends RuntimeException {
        SimulatedDbFailureException(String message) {
            super(message);
        }
    }
}
