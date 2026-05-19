package com.frostlogic.warproject.server.captivity;

import com.frostlogic.warproject.persistence.dao.AccountsDao;
import com.frostlogic.warproject.persistence.dao.AuditLogDao;
import com.frostlogic.warproject.persistence.dao.PassportsDao;
import com.frostlogic.warproject.persistence.dao.PlayersDao;
import net.jqwik.api.*;
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
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 21: Atomicity of passport capture.
 * <p>
 * For any generated failure point (0..N steps in the transaction), the post-state is either:
 * <ul>
 *   <li><b>Captured</b>: passport.captured_by_uuid IS SET AND player.captured=1 AND audit entry exists</li>
 *   <li><b>Not captured</b>: passport.captured_by_uuid IS NULL AND player.captured=0 AND no audit entry</li>
 * </ul>
 * <p>
 * Tests DB-level atomicity directly using in-memory SQLite with injected failures
 * at different points during the capture transaction.
 * <p>
 * <b>Validates: Requirements 14.3</b>
 * <p>
 * Design: §12 Property 21
 */
class CaptivityAtomicityProperties {

    /**
     * The capture transaction performs exactly 3 executeUpdate calls:
     * <ol>
     *   <li>UPDATE passports SET captured_by_uuid, trophy</li>
     *   <li>UPDATE players SET captured = 1</li>
     *   <li>INSERT INTO audit_log (...)</li>
     * </ol>
     */
    private static final int TRANSACTION_STEPS = 3;

    private Connection conn;

    // Test data
    private String initiatorUuid;
    private String targetUuid;
    private String passportId;

    @BeforeTry
    void setupDatabase() throws Exception {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        conn.setAutoCommit(true);
        applyMigration(conn);

        // Insert prerequisite records
        initiatorUuid = UUID.randomUUID().toString();
        targetUuid = UUID.randomUUID().toString();
        passportId = "ZRN-" + String.format("%06d", (int) (Math.random() * 999999) + 1);

        insertPrerequisites();
    }

    @AfterTry
    void tearDown() throws Exception {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
    }

    /**
     * Property 21: For any generated failure point (0..N steps in the transaction),
     * the post-state is either {Captured} or {Not captured} — never partial.
     */
    @Property(tries = 50)
    void captureTransactionIsAtomic(@ForAll("failurePoints") int failAtStep) {
        // Count the initial audit entries
        long initialAuditCount = countAuditEntries();

        // Attempt the capture transaction with a failure injected at the given step
        boolean committed = attemptCaptureWithFailure(failAtStep);

        // Verify post-state is consistent: either fully captured or not captured at all
        PassportsDao.Passport passport = new PassportsDao().findById(conn, passportId).orElseThrow();
        PlayersDao.Player player = new PlayersDao().findByUuid(conn, targetUuid).orElseThrow();
        long newAuditCount = countAuditEntries() - initialAuditCount;

        boolean passportCaptured = passport.capturedByUuid() != null;
        boolean playerCaptured = player.captured();
        boolean auditExists = newAuditCount > 0;

        if (committed) {
            // All changes must be applied
            assertThat(passportCaptured)
                    .as("Passport should be marked as captured after successful commit")
                    .isTrue();
            assertThat(passport.capturedByUuid())
                    .as("Passport captured_by_uuid should be initiator's UUID")
                    .isEqualTo(initiatorUuid);
            assertThat(passport.trophy())
                    .as("Passport trophy flag should be set")
                    .isTrue();
            assertThat(playerCaptured)
                    .as("Player should be marked as captured after successful commit")
                    .isTrue();
            assertThat(auditExists)
                    .as("Audit entry should exist after successful commit")
                    .isTrue();
            assertThat(newAuditCount)
                    .as("Exactly one CAPTURE_PASSPORT audit entry should exist")
                    .isEqualTo(1);
        } else {
            // No changes must be applied (rolled back)
            assertThat(passportCaptured)
                    .as("Passport should NOT be captured after rollback")
                    .isFalse();
            assertThat(passport.trophy())
                    .as("Passport trophy flag should remain false after rollback")
                    .isFalse();
            assertThat(playerCaptured)
                    .as("Player should NOT be captured after rollback")
                    .isFalse();
            assertThat(auditExists)
                    .as("No audit entry should exist after rollback")
                    .isFalse();
        }
    }

    /**
     * Generates failure points: 0 means fail before any step (immediate failure),
     * 1..TRANSACTION_STEPS means fail after that many executeUpdate calls,
     * and TRANSACTION_STEPS+1 means no failure (successful commit).
     */
    @Provide
    Arbitrary<Integer> failurePoints() {
        // 0 = fail immediately, 1 = fail after step 1, 2 = after step 2, 3 = after step 3 (commit succeeds)
        // We include TRANSACTION_STEPS + 1 to test the success path as well
        return Arbitraries.integers().between(0, TRANSACTION_STEPS + 1);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Transaction simulation
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Simulates the capture transaction with a failure injected at the specified step.
     * Uses the same DAO calls as CaptivityService.capture() but with a proxied connection
     * that throws after N executeUpdate calls.
     *
     * @param failAtStep the step number at which to inject a failure (0 = immediate, >STEPS = no failure)
     * @return true if the transaction committed successfully, false if it was rolled back
     */
    private boolean attemptCaptureWithFailure(int failAtStep) {
        try {
            conn.setAutoCommit(false);

            AtomicInteger executeCount = new AtomicInteger(0);

            // Create a proxied connection that counts executeUpdate calls and throws at the target step
            Connection proxiedConn = createFailingConnectionProxy(conn, executeCount, failAtStep);

            try {
                PassportsDao passportsDao = new PassportsDao();
                PlayersDao playersDao = new PlayersDao();
                AuditLogDao auditLogDao = new AuditLogDao();

                // Step 1: Update passport capture state
                passportsDao.updateCaptureState(proxiedConn, passportId, initiatorUuid, true);

                // Step 2: Update player captured flag
                playersDao.setCaptured(proxiedConn, targetUuid, true);

                // Step 3: Insert audit log entry
                auditLogDao.insert(proxiedConn,
                        System.currentTimeMillis(),
                        initiatorUuid,
                        "Initiator",
                        targetUuid,
                        "Target",
                        "CAPTURE_PASSPORT",
                        null,
                        null
                );

                conn.commit();
                return true;
            } catch (SimulatedDbFailureException e) {
                conn.rollback();
                return false;
            } catch (RuntimeException e) {
                // DAO methods wrap SQLExceptions in RuntimeExceptions
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
            } catch (SQLException ignored) {}
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Connection proxy for failure injection
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Creates a dynamic proxy around the real connection that intercepts prepareStatement
     * and wraps the returned PreparedStatement with a proxy that counts executeUpdate calls.
     * When the count reaches failAtStep, a SimulatedDbFailureException is thrown.
     */
    private Connection createFailingConnectionProxy(Connection realConn, AtomicInteger executeCount, int failAtStep) {
        return (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[]{Connection.class},
                (proxy, method, args) -> {
                    Object result = method.invoke(realConn, args);
                    if ("prepareStatement".equals(method.getName()) && result instanceof PreparedStatement ps) {
                        return createFailingPreparedStatementProxy(ps, executeCount, failAtStep);
                    }
                    return result;
                }
        );
    }

    /**
     * Creates a dynamic proxy around a PreparedStatement that throws after the Nth executeUpdate.
     */
    private PreparedStatement createFailingPreparedStatementProxy(PreparedStatement realPs,
                                                                   AtomicInteger executeCount,
                                                                   int failAtStep) {
        return (PreparedStatement) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[]{PreparedStatement.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if ("executeUpdate".equals(method.getName())) {
                            int currentStep = executeCount.incrementAndGet();
                            if (currentStep > failAtStep) {
                                throw new SimulatedDbFailureException(
                                        "Simulated DB failure at step " + currentStep +
                                                " (configured to fail after step " + failAtStep + ")");
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

    // ─────────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────────

    private void insertPrerequisites() {
        AccountsDao accountsDao = new AccountsDao();
        PlayersDao playersDao = new PlayersDao();
        PassportsDao passportsDao = new PassportsDao();

        // Insert accounts for both players
        accountsDao.insert(conn, new AccountsDao.Account(
                initiatorUuid, "$2a$10$dummyhash000000000000000000000000000000000000000000",
                System.currentTimeMillis(), null, 0, 0));
        accountsDao.insert(conn, new AccountsDao.Account(
                targetUuid, "$2a$10$dummyhash000000000000000000000000000000000000000000",
                System.currentTimeMillis(), null, 0, 0));

        // Insert players: initiator = ZARNAVIA/ACCEPTED, target = CHERNOGRYAD/ACCEPTED
        playersDao.insert(conn, new PlayersDao.Player(
                initiatorUuid, "ZARNAVIA", "SOLDIER", null, "ACCEPTED",
                "Ivan", "Petrov", false, null, false, 0,
                System.currentTimeMillis(), System.currentTimeMillis(), null, "Commander", null));
        playersDao.insert(conn, new PlayersDao.Player(
                targetUuid, "CHERNOGRYAD", "SOLDIER", null, "ACCEPTED",
                "Oleg", "Sidorov", false, null, false, 0,
                System.currentTimeMillis(), System.currentTimeMillis(), null, "Commander", null));

        // Insert passport for target (not captured, no trophy)
        passportsDao.insert(conn, new PassportsDao.Passport(
                passportId, targetUuid, "CHERNOGRYAD",
                "Oleg", "Sidorov", "15.06.1995", 12345L,
                "ACCEPTED", System.currentTimeMillis(), "Commander",
                null, false, System.currentTimeMillis()));
    }

    private long countAuditEntries() {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM audit_log WHERE action = 'CAPTURE_PASSPORT'")) {
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

        // Simple statement splitting: split on semicolons not inside single quotes
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
     * respecting single-quoted strings and comments.
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

    /**
     * Custom exception used to simulate a database failure at a specific point in the transaction.
     */
    private static class SimulatedDbFailureException extends RuntimeException {
        SimulatedDbFailureException(String message) {
            super(message);
        }
    }
}
