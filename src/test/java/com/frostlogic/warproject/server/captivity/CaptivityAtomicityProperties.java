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
 */
class CaptivityAtomicityProperties {

    private static final int TRANSACTION_STEPS = 3;

    private Connection conn;

    private String initiatorUuid;
    private String targetUuid;
    private String passportId;

    @BeforeTry
    void setupDatabase() throws Exception {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        conn.setAutoCommit(true);
        applyMigration(conn);

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

    @Property(tries = 50)
    void captureTransactionIsAtomic(@ForAll("failurePoints") int failAtStep) {
        long initialAuditCount = countAuditEntries();

        boolean committed = attemptCaptureWithFailure(failAtStep);

        PassportsDao.Passport passport = new PassportsDao().findById(conn, passportId).orElseThrow();
        PlayersDao.Player player = new PlayersDao().findByUuid(conn, targetUuid).orElseThrow();
        long newAuditCount = countAuditEntries() - initialAuditCount;

        boolean passportCaptured = passport.capturedByUuid() != null;
        boolean playerCaptured = player.captured();
        boolean auditExists = newAuditCount > 0;

        if (committed) {
            assertThat(passportCaptured).isTrue();
            assertThat(passport.capturedByUuid()).isEqualTo(initiatorUuid);
            assertThat(passport.trophy()).isTrue();
            assertThat(playerCaptured).isTrue();
            assertThat(auditExists).isTrue();
            assertThat(newAuditCount).isEqualTo(1);
        } else {
            assertThat(passportCaptured).isFalse();
            assertThat(passport.trophy()).isFalse();
            assertThat(playerCaptured).isFalse();
            assertThat(auditExists).isFalse();
        }
    }

    @Provide
    Arbitrary<Integer> failurePoints() {
        return Arbitraries.integers().between(0, TRANSACTION_STEPS + 1);
    }

    private boolean attemptCaptureWithFailure(int failAtStep) {
        try {
            conn.setAutoCommit(false);

            AtomicInteger executeCount = new AtomicInteger(0);

            Connection proxiedConn = createFailingConnectionProxy(conn, executeCount, failAtStep);

            try {
                PassportsDao passportsDao = new PassportsDao();
                PlayersDao playersDao = new PlayersDao();
                AuditLogDao auditLogDao = new AuditLogDao();

                passportsDao.updateCaptureState(proxiedConn, passportId, initiatorUuid, true);
                playersDao.setCaptured(proxiedConn, targetUuid, true);
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

    private Connection createFailingConnectionProxy(Connection realConn, AtomicInteger executeCount, int failAtStep) {
        return (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    Object result = method.invoke(realConn, args);
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

    private void insertPrerequisites() {
        AccountsDao accountsDao = new AccountsDao();
        PlayersDao playersDao = new PlayersDao();
        PassportsDao passportsDao = new PassportsDao();

        accountsDao.insert(conn, new AccountsDao.Account(
                initiatorUuid, "$2a$10$dummyhash000000000000000000000000000000000000000000",
                System.currentTimeMillis(), null, 0, 0));
        accountsDao.insert(conn, new AccountsDao.Account(
                targetUuid, "$2a$10$dummyhash000000000000000000000000000000000000000000",
                System.currentTimeMillis(), null, 0, 0));

        playersDao.insert(conn, new PlayersDao.Player(
                initiatorUuid, "ZARNAVIA", "SOLDIER", null, "ACCEPTED",
                "Ivan", "Petrov", false, null, false, 0,
                System.currentTimeMillis(), System.currentTimeMillis(), null, "Commander", null));
        playersDao.insert(conn, new PlayersDao.Player(
                targetUuid, "CHERNOGRYAD", "SOLDIER", null, "ACCEPTED",
                "Oleg", "Sidorov", false, null, false, 0,
                System.currentTimeMillis(), System.currentTimeMillis(), null, "Commander", null));

        passportsDao.insert(conn, new PassportsDao.Passport(
                passportId, targetUuid, "CHERNOGRYAD",
                "Oleg", "Sidorov", "15.06.1995", 12345L,
                "ACCEPTED", System.currentTimeMillis(), "Commander",
                null, null, false, System.currentTimeMillis()));
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
