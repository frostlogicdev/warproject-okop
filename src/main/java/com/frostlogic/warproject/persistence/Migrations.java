package com.frostlogic.warproject.persistence;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Simple Flyway-like migration runner for WarProject.
 * <p>
 * Loads SQL migration files from the classpath at {@code db/migrations/}
 * in version order (V1, V2, ...), applies them, and records each applied
 * version in the {@code schema_version} table.
 * <p>
 * The {@code ${AI}} token in SQL files is replaced with:
 * <ul>
 *   <li>{@code AUTOINCREMENT} for SQLite</li>
 *   <li>{@code AUTO_INCREMENT} for MySQL</li>
 * </ul>
 * <p>
 * Requirements: 18.1, 18.4
 * Design: §4.1
 */
public final class Migrations {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Pattern to match migration file names: V{version}__{description}.sql
     */
    private static final Pattern MIGRATION_PATTERN = Pattern.compile("V(\\d+)__(.+)\\.sql");

    /**
     * Classpath directory containing migration files.
     */
    private static final String MIGRATIONS_DIR = "db/migrations/";

    /**
     * Index file listing all migration files (generated at build time or manually maintained).
     * If not present, we scan known versions.
     */
    private static final String INDEX_FILE = MIGRATIONS_DIR + "index.txt";

    /**
     * Token replaced with the appropriate auto-increment keyword.
     */
    private static final String AI_TOKEN = "${AI}";

    private Migrations() {
        // utility class
    }

    /**
     * Runs all pending migrations against the given database.
     *
     * @param database the database instance
     */
    public static void run(Database database) {
        try (Connection conn = database.getConnection()) {
            conn.setAutoCommit(false);
            ensureSchemaVersionTable(conn);
            conn.commit();

            int currentVersion = getCurrentVersion(conn);
            List<MigrationFile> migrations = discoverMigrations();
            migrations.sort(Comparator.comparingInt(MigrationFile::version));

            int applied = 0;
            for (MigrationFile migration : migrations) {
                if (migration.version() <= currentVersion) {
                    continue;
                }
                LOGGER.info("Applying migration V{}__{}...", migration.version(), migration.description());
                String sql = loadMigrationSql(migration.fileName(), database.isSqlite());
                executeMigration(conn, sql);
                recordVersion(conn, migration.version());
                conn.commit();
                applied++;
            }

            if (applied > 0) {
                LOGGER.info("Applied {} migration(s). Current schema version: {}",
                        applied, applied > 0 ? migrations.get(migrations.size() - 1).version() : currentVersion);
            } else {
                LOGGER.info("Database schema is up to date (version {}).", currentVersion);
            }
        } catch (SQLException | IOException e) {
            throw new RuntimeException("Migration failed", e);
        }
    }

    /**
     * Creates the schema_version table if it does not exist.
     */
    private static void ensureSchemaVersionTable(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS schema_version (
                        version    INTEGER NOT NULL PRIMARY KEY,
                        applied_at BIGINT  NOT NULL
                    )
                    """);
        }
    }

    /**
     * Returns the highest applied migration version, or 0 if none.
     */
    private static int getCurrentVersion(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        }
    }

    /**
     * Discovers migration files from the classpath.
     * First tries to read an index file; if not present, scans known versions.
     */
    private static List<MigrationFile> discoverMigrations() throws IOException {
        List<MigrationFile> migrations = new ArrayList<>();

        // Try reading index file first
        InputStream indexStream = Migrations.class.getClassLoader().getResourceAsStream(INDEX_FILE);
        if (indexStream != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(indexStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    MigrationFile mf = parseMigrationFileName(line);
                    if (mf != null) {
                        migrations.add(mf);
                    }
                }
            }
            return migrations;
        }

        // Fallback: probe for V1..V100 sequentially
        for (int v = 1; v <= 100; v++) {
            MigrationFile mf = probeVersion(v);
            if (mf != null) {
                migrations.add(mf);
            } else if (v > 1) {
                // Stop probing after first gap beyond V1
                break;
            }
        }

        return migrations;
    }

    /**
     * Probes the classpath for a migration file with the given version number.
     */
    private static MigrationFile probeVersion(int version) {
        // Try common naming patterns
        String prefix = MIGRATIONS_DIR + "V" + version + "__";
        // We need to check if any resource with this prefix exists.
        // Since we can't list classpath resources portably, we try known names.
        // The primary migration is V1__init.sql
        String[] knownNames = {
                "V" + version + "__init.sql",
                "V" + version + "__update.sql",
                "V" + version + "__schema.sql",
                "V" + version + "__migration.sql"
        };

        for (String name : knownNames) {
            InputStream is = Migrations.class.getClassLoader().getResourceAsStream(MIGRATIONS_DIR + name);
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ignored) {
                }
                MigrationFile mf = parseMigrationFileName(name);
                if (mf != null) return mf;
            }
        }
        return null;
    }

    /**
     * Parses a migration file name into a MigrationFile record.
     */
    private static MigrationFile parseMigrationFileName(String fileName) {
        Matcher matcher = MIGRATION_PATTERN.matcher(fileName);
        if (matcher.matches()) {
            int version = Integer.parseInt(matcher.group(1));
            String description = matcher.group(2);
            return new MigrationFile(version, description, fileName);
        }
        return null;
    }

    /**
     * Loads and processes a migration SQL file from the classpath.
     * Replaces the ${AI} token with the appropriate keyword.
     */
    private static String loadMigrationSql(String fileName, boolean isSqlite) throws IOException {
        String resourcePath = MIGRATIONS_DIR + fileName;
        InputStream is = Migrations.class.getClassLoader().getResourceAsStream(resourcePath);
        if (is == null) {
            throw new IOException("Migration file not found on classpath: " + resourcePath);
        }

        String sql;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            sql = reader.lines().collect(Collectors.joining("\n"));
        }

        // Replace ${AI} token with the appropriate auto-increment keyword
        String aiReplacement = isSqlite ? "AUTOINCREMENT" : "AUTO_INCREMENT";
        sql = sql.replace(AI_TOKEN, aiReplacement);

        return sql;
    }

    /**
     * Executes a migration SQL script. Splits on semicolons and executes each statement.
     */
    private static void executeMigration(Connection conn, String sql) throws SQLException {
        // Split SQL into individual statements by semicolons (not inside quotes)
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
     * Records a successfully applied migration version.
     */
    private static void recordVersion(Connection conn, int version) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO schema_version (version, applied_at) VALUES (?, ?)")) {
            ps.setInt(1, version);
            ps.setLong(2, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    /**
     * Splits SQL text into individual statements by semicolons,
     * respecting single-quoted string literals and comments.
     */
    static String[] splitStatements(String sql) {
        List<String> statements = new ArrayList<>();
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
                    i++; // skip '/'
                }
                continue;
            }

            if (c == '-' && next == '-' && !inSingleQuote) {
                inLineComment = true;
                i++; // skip second '-'
                continue;
            }

            if (c == '/' && next == '*' && !inSingleQuote) {
                inBlockComment = true;
                i++; // skip '*'
                continue;
            }

            if (c == '\'' && !inBlockComment && !inLineComment) {
                inSingleQuote = !inSingleQuote;
                current.append(c);
                continue;
            }

            if (c == ';' && !inSingleQuote) {
                String stmt = current.toString().trim();
                if (!stmt.isEmpty()) {
                    statements.add(stmt);
                }
                current.setLength(0);
                continue;
            }

            current.append(c);
        }

        // Add last statement if no trailing semicolon
        String last = current.toString().trim();
        if (!last.isEmpty()) {
            statements.add(last);
        }

        return statements.toArray(new String[0]);
    }

    /**
     * Represents a discovered migration file.
     */
    record MigrationFile(int version, String description, String fileName) {
    }
}
