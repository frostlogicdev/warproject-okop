package com.frostlogic.warproject.persistence;

import com.frostlogic.warproject.WpConfig;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Simple JDBC connection manager for WarProject.
 * <p>
 * Supports SQLite (default) and MySQL via configuration.
 * Provides transaction helpers with appropriate isolation semantics
 * for each database engine.
 * <p>
 * Requirements: 18.1, 18.4
 * Design: §4.1
 */
public final class Database {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** busy_timeout for SQLite connections, in milliseconds. */
    private static final int SQLITE_BUSY_TIMEOUT_MS = 5_000;

    private final String driver;
    private final String jdbcUrl;
    private final String username;
    private final String password;

    private volatile boolean initialized;

    public Database(String driver, String jdbcUrl, String username, String password) {
        this.driver = driver;
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    /**
     * Creates a Database instance from the current WpConfig values.
     */
    public static Database fromConfig() {
        return new Database(
                WpConfig.DB_DRIVER.get(),
                WpConfig.DB_JDBC_URL.get(),
                WpConfig.DB_USERNAME.get(),
                WpConfig.DB_PASSWORD.get()
        );
    }

    /**
     * Initializes the database: loads the JDBC driver, applies file-level
     * pragmas (SQLite only) and runs migrations.
     */
    public void initialize() {
        loadDriver();
        if (isSqlite()) {
            applyPersistentSqlitePragmas();
        }
        Migrations.run(this);
        initialized = true;
        LOGGER.info("WarProject database initialized (driver={}, url={})", driver, jdbcUrl);
    }

    /**
     * Flips SQLite to WAL journaling once at boot. WAL is a file-level
     * persistent setting — once enabled it sticks across restarts, but we
     * re-apply it unconditionally so a manually rolled-back database is
     * upgraded again on the next start. synchronous=NORMAL is paired with
     * WAL because the default (FULL) gives no extra durability on top of
     * WAL's own fsync at checkpoint time and slows down small writes.
     */
    private void applyPersistentSqlitePragmas() {
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA synchronous=NORMAL");
            LOGGER.info("[WarProject] SQLite WAL journaling enabled (synchronous=NORMAL).");
        } catch (SQLException e) {
            LOGGER.warn("[WarProject] Failed to enable SQLite WAL mode, continuing in default journal mode: {}",
                    e.getMessage());
        }
    }

    /**
     * Returns whether this database uses SQLite.
     */
    public boolean isSqlite() {
        return "sqlite".equalsIgnoreCase(driver);
    }

    /**
     * Returns whether this database uses MySQL.
     */
    public boolean isMysql() {
        return "mysql".equalsIgnoreCase(driver);
    }

    /**
     * Opens a new JDBC connection. Caller is responsible for closing it.
     * <p>
     * For SQLite, session-local pragmas (busy_timeout, foreign_keys) are
     * applied to every fresh connection because they do not persist across
     * connection boundaries the way WAL does.
     */
    public Connection getConnection() throws SQLException {
        if (isMysql()) {
            return DriverManager.getConnection(jdbcUrl, username, password);
        }
        Connection conn = DriverManager.getConnection(jdbcUrl);
        try {
            applySessionSqlitePragmas(conn);
        } catch (SQLException e) {
            try { conn.close(); } catch (SQLException ignored) {}
            throw e;
        }
        return conn;
    }

    private static void applySessionSqlitePragmas(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA busy_timeout=" + SQLITE_BUSY_TIMEOUT_MS);
            stmt.execute("PRAGMA foreign_keys=ON");
        }
    }

    /**
     * Executes the given action inside a transaction.
     * <p>
     * For SQLite uses {@code BEGIN IMMEDIATE} to acquire a write lock early,
     * preventing SQLITE_BUSY in concurrent scenarios.
     * For MySQL uses {@code START TRANSACTION}.
     * <p>
     * On success the transaction is committed; on exception it is rolled back.
     *
     * @param action consumer that receives the transactional connection
     */
    public void transaction(Consumer<Connection> action) {
        inTx(conn -> {
            action.accept(conn);
            return null;
        });
    }

    /**
     * Executes the given function inside a transaction and returns its result.
     * <p>
     * For SQLite the transaction is started explicitly with {@code BEGIN IMMEDIATE}
     * to acquire a write lock early, preventing {@code SQLITE_BUSY} in
     * concurrent scenarios. The connection is left in {@code autoCommit=true}
     * mode because sqlite-jdbc 3.45+ already issues an implicit
     * {@code BEGIN} on {@code setAutoCommit(false)}, which would clash with an
     * explicit {@code BEGIN IMMEDIATE} ("cannot start a transaction within a
     * transaction"). With autoCommit on, the {@code BEGIN IMMEDIATE} /
     * {@code COMMIT} / {@code ROLLBACK} sequence works as documented.
     * <p>
     * For MySQL we use the standard JDBC pattern of
     * {@code setAutoCommit(false)}, which begins the transaction implicitly.
     * <p>
     * On success the transaction is committed; on exception it is rolled back.
     *
     * @param function function that receives the transactional connection
     * @param <T>      return type
     * @return the result of the function
     */
    public <T> T inTx(Function<Connection, T> function) {
        try (Connection conn = getConnection()) {
            boolean sqlite = isSqlite();
            if (sqlite) {
                // Keep autoCommit=true; drive transaction boundaries explicitly.
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("BEGIN IMMEDIATE");
                }
            } else {
                conn.setAutoCommit(false);
            }
            try {
                T result = function.apply(conn);
                if (sqlite) {
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute("COMMIT");
                    }
                } else {
                    conn.commit();
                }
                return result;
            } catch (Exception e) {
                if (sqlite) {
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute("ROLLBACK");
                    } catch (SQLException rollbackFailure) {
                        // Suppress rollback failure if the original exception
                        // already aborted the transaction (e.g. SQLite auto-rollback).
                    }
                } else {
                    conn.rollback();
                }
                throw e instanceof RuntimeException re ? re : new RuntimeException(e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database transaction failed", e);
        }
    }

    private void loadDriver() {
        try {
            if (isSqlite()) {
                Class.forName("org.sqlite.JDBC");
            } else if (isMysql()) {
                Class.forName("com.mysql.cj.jdbc.Driver");
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to load JDBC driver for: " + driver, e);
        }
    }
}
