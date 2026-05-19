package com.frostlogic.warproject.server.audit;

import com.frostlogic.warproject.persistence.Database;
import com.frostlogic.warproject.persistence.dao.AuditLogDao;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Service facade around {@link AuditLogDao} that implements the audit-write
 * strategy from <strong>design §13.1</strong>.
 *
 * <h2>Two write paths</h2>
 * <ul>
 *   <li>{@link #write(Connection, AuditEntry)} — synchronous insert on a caller-provided
 *       {@link Connection}. Used by domain operations (accept, capture, ban, …) that
 *       must commit the audit row in the <em>same</em> transaction as the state change
 *       so atomicity holds (Req. 20.3, Property 13).</li>
 *   <li>{@link #writeAsync(AuditEntry)} — fire-and-forget insert on a single-threaded
 *       background executor. Used for informational events without an accompanying DB
 *       state change ({@code CAPTCHA_FAIL}, {@code LOGIN_FAIL}, {@code GENERALCHAT_SEND}).
 *       Three exponential-backoff retries (50 ms, 200 ms, 1000 ms) on {@code SQLException};
 *       on final failure the entry is appended to {@code logs/audit.log} as a single
 *       JSON line and a {@code WARN} is emitted to the general logger.</li>
 * </ul>
 *
 * <h2>Secret-substring guard (Req. 20.4)</h2>
 * Every entry passes through {@link #validate(AuditEntry)} which rejects rows whose
 * {@code reason} or {@code extraJson} contain markers that look like password material
 * or active captcha codes. The check is intentionally conservative — false positives are
 * preferable to leaking a credential into a forensic log.
 *
 * <h2>File fallback format</h2>
 * One line per entry, NDJSON-style:
 * <pre>{"ts_utc":1700000000000,"actor_uuid":"…","actor_name":"…","target_uuid":null,
 *  "target_name":null,"action":"DB_UNAVAILABLE","reason":null,"extra_json":null}</pre>
 * The file is created on demand at {@code logs/audit.log} relative to the server's
 * working directory; parent directories are created if absent.
 *
 * <p>Requirements: 20.1, 20.2, 20.3, 20.4
 * <br>Design: §13.1, §13.2
 */
public final class AuditLogger {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Retry delays (in milliseconds) for {@link #writeAsync} per design §13.1. */
    static final long[] RETRY_DELAYS_MS = {50L, 200L, 1000L};

    /**
     * Default location for the file-fallback log, resolved relative to the server's
     * working directory. {@code Paths.get("logs", "audit.log")} matches the design.
     */
    static final Path DEFAULT_FALLBACK_PATH = Paths.get("logs", "audit.log");

    /**
     * Heuristic patterns for forbidden secret-substrings in {@code extra_json} / {@code reason}.
     * The patterns are case-insensitive and match common JSON keys (e.g.
     * {@code "password":"…"}, {@code "captchaCode":"…"}) as well as raw markers
     * (BCrypt hash prefix). This is intentionally a substring check, not a structural
     * parser — the goal is to fail loud on any callsite that might leak a secret rather
     * than to define every possible safe encoding.
     */
    private static final Pattern[] FORBIDDEN_PATTERNS = {
            Pattern.compile("\"\\s*password\\s*\"\\s*:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\"\\s*password_?hash\\s*\"\\s*:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\"\\s*pwd\\s*\"\\s*:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\"\\s*captcha_?code\\s*\"\\s*:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\"\\s*captcha\\s*\"\\s*:", Pattern.CASE_INSENSITIVE),
            // BCrypt hash prefix: $2a$, $2b$, $2y$ followed by cost.
            Pattern.compile("\\$2[aby]\\$\\d{2}\\$"),
    };

    private final Database database;
    private final AuditLogDao auditLogDao;
    private final Path fallbackPath;
    private final ExecutorService executor;

    /**
     * Constructs a logger that writes asynchronous entries through {@code database}
     * and falls back to {@link #DEFAULT_FALLBACK_PATH} on persistent failure.
     */
    public AuditLogger(Database database, AuditLogDao auditLogDao) {
        this(database, auditLogDao, DEFAULT_FALLBACK_PATH, defaultExecutor());
    }

    /**
     * Test-friendly constructor: callers may inject an alternate fallback path and a
     * synchronous executor (e.g. {@code Runnable::run}) for deterministic assertions.
     */
    public AuditLogger(Database database, AuditLogDao auditLogDao,
                       Path fallbackPath, ExecutorService executor) {
        this.database = Objects.requireNonNull(database, "database");
        this.auditLogDao = Objects.requireNonNull(auditLogDao, "auditLogDao");
        this.fallbackPath = Objects.requireNonNull(fallbackPath, "fallbackPath");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * Inserts {@code entry} on the given {@link Connection}. The connection's
     * transaction lifecycle is the caller's responsibility — this method intentionally
     * does NOT commit or rollback. Used by domain operations that bundle the audit row
     * with their own state change.
     *
     * @return generated {@code audit_log.id}
     * @throws IllegalArgumentException if the entry contains forbidden secret substrings
     *                                  (Req. 20.4)
     */
    public long write(Connection conn, AuditEntry entry) {
        Objects.requireNonNull(conn, "conn");
        Objects.requireNonNull(entry, "entry");
        validate(entry);
        return auditLogDao.insert(conn,
                entry.tsUtc(),
                entry.actorUuid(),
                entry.actorName(),
                entry.targetUuid(),
                entry.targetName(),
                entry.action().name(),
                entry.reason(),
                entry.extraJson());
    }

    /**
     * Asynchronously persists {@code entry} in its own short transaction.
     * <p>
     * Behaviour on failure:
     * <ol>
     *   <li>Three retries with delays from {@link #RETRY_DELAYS_MS}.</li>
     *   <li>If all retries fail — append the entry to {@code logs/audit.log} as JSON and
     *       emit a {@code WARN} to the general logger.</li>
     *   <li>If the file write also fails — emit an {@code ERROR}; the entry is lost.</li>
     * </ol>
     *
     * @throws IllegalArgumentException synchronously if {@code entry} contains forbidden
     *                                  secret substrings (Req. 20.4). Validation runs
     *                                  on the calling thread so callsite bugs surface
     *                                  immediately rather than disappearing into the
     *                                  background executor.
     */
    public void writeAsync(AuditEntry entry) {
        Objects.requireNonNull(entry, "entry");
        validate(entry);
        executor.execute(() -> writeWithRetry(entry));
    }

    /**
     * Validates that the entry does not contain plaintext password, BCrypt hash, or
     * active captcha-code substrings in {@code reason} or {@code extraJson} (Req. 20.4).
     *
     * @throws IllegalArgumentException if a forbidden substring is detected. The
     *         exception message names the offending field but does NOT echo the
     *         payload — re-emitting the secret into a stack trace would defeat the
     *         purpose of the check.
     */
    static void validate(AuditEntry entry) {
        checkForSecrets("extraJson", entry.extraJson());
        checkForSecrets("reason", entry.reason());
    }

    private static void checkForSecrets(String field, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        for (Pattern p : FORBIDDEN_PATTERNS) {
            if (p.matcher(value).find()) {
                throw new IllegalArgumentException(
                        "Audit entry " + field + " contains forbidden secret substring "
                                + "(password / hash / captcha code) — refusing to log");
            }
        }
    }

    /** Background loop: insert with retries, then file-fallback on persistent failure. */
    private void writeWithRetry(AuditEntry entry) {
        Throwable lastFailure = null;
        for (int attempt = 0; attempt <= RETRY_DELAYS_MS.length; attempt++) {
            try {
                database.transaction(conn -> auditLogDao.insert(conn,
                        entry.tsUtc(),
                        entry.actorUuid(),
                        entry.actorName(),
                        entry.targetUuid(),
                        entry.targetName(),
                        entry.action().name(),
                        entry.reason(),
                        entry.extraJson()));
                return;
            } catch (RuntimeException e) {
                lastFailure = e;
                if (attempt < RETRY_DELAYS_MS.length) {
                    sleep(RETRY_DELAYS_MS[attempt]);
                }
            }
        }
        LOGGER.warn("Audit DB write failed after {} retries; falling back to {}",
                RETRY_DELAYS_MS.length, fallbackPath, lastFailure);
        appendToFallbackFile(entry);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Appends a single NDJSON line to {@link #fallbackPath}. Best-effort. */
    private void appendToFallbackFile(AuditEntry entry) {
        try {
            Path parent = fallbackPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String line = toJsonLine(entry) + System.lineSeparator();
            Files.writeString(fallbackPath, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException io) {
            LOGGER.error("Audit file-fallback write to {} failed; entry lost: action={} ts={}",
                    fallbackPath, entry.action(), entry.tsUtc(), io);
        }
    }

    /**
     * Hand-rolled JSON encoder for the fallback line. Avoids pulling in a JSON library
     * for a single-purpose write path. Output keys mirror the DB column names.
     */
    static String toJsonLine(AuditEntry entry) {
        StringBuilder sb = new StringBuilder(192);
        sb.append('{');
        appendNumber(sb, "ts_utc", entry.tsUtc(), false);
        appendString(sb, "actor_uuid", entry.actorUuid(), true);
        appendString(sb, "actor_name", entry.actorName(), true);
        appendString(sb, "target_uuid", entry.targetUuid(), true);
        appendString(sb, "target_name", entry.targetName(), true);
        appendString(sb, "action", entry.action().name(), true);
        appendString(sb, "reason", entry.reason(), true);
        appendString(sb, "extra_json", entry.extraJson(), true);
        sb.append('}');
        return sb.toString();
    }

    private static void appendNumber(StringBuilder sb, String key, long value, boolean leadingComma) {
        if (leadingComma) sb.append(',');
        sb.append('"').append(key).append("\":").append(value);
    }

    private static void appendString(StringBuilder sb, String key, String value, boolean leadingComma) {
        if (leadingComma) sb.append(',');
        sb.append('"').append(key).append("\":");
        if (value == null) {
            sb.append("null");
        } else {
            sb.append('"').append(escapeJsonString(value)).append('"');
        }
    }

    private static String escapeJsonString(String s) {
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    private static ExecutorService defaultExecutor() {
        ThreadFactory tf = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "wp-audit-async-" + counter.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        };
        return Executors.newSingleThreadExecutor(tf);
    }

    /**
     * Releases the background executor. Idempotent. Intended to be called on server
     * stop so daemon threads do not leak across reloads.
     */
    public void shutdown() {
        executor.shutdown();
    }
}
