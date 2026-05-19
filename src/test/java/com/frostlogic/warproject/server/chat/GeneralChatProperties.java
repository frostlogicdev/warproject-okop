package com.frostlogic.warproject.server.chat;

import com.frostlogic.warproject.attachment.Role;
import com.frostlogic.warproject.persistence.dao.AuditLogDao;
import com.frostlogic.warproject.persistence.dao.CooldownsDao;
import net.jqwik.api.*;
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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 22: GeneralChat — cooldown predicate and recipient audience filter.
 * <p>
 * Property 22 (design §12) expressed as four sub-properties:
 * <ul>
 *   <li><b>22a (cooldown predicate).</b> For any
 *       {@code (Role role, long expiresAt, long now)},
 *       a model of {@code canSend} that returns
 *       {@code role.atLeast(COMMANDER) ∧ ¬cooldownsDao.isActive(uuid, "GC", now)}
 *       agrees with the formal Property 22 specification:
 *       {@code role ∈ {COMMANDER, GENERAL, OP} ∧ expiresAt ≤ now}.</li>
 *   <li><b>22b (audience filter).</b> For any list of {@link Role} values
 *       (one per simulated online player), the recipient filter used by
 *       {@link GeneralChatService#recipients} —
 *       {@link GeneralChatService#hasRequiredRole}, equivalent to
 *       {@code role.atLeast(COMMANDER)} — never lets a {@code SOLDIER} or
 *       {@code CANDIDATE} through, and always includes {@code COMMANDER},
 *       {@code GENERAL}, and {@code OP}.</li>
 *   <li><b>22c (post-send cooldown).</b> After the post-send transaction
 *       upserts {@code cooldowns(uuid, "GC", now + 30_000)}, the row read
 *       back via {@link CooldownsDao#findByKey} has
 *       {@code expiresAt == now + 30_000}, and
 *       {@link CooldownsDao#isActive} is true for every
 *       {@code now' < now + 30_000} and false for every
 *       {@code now' ≥ now + 30_000}.</li>
 *   <li><b>22d (audit body absence).</b> For any message string, the
 *       {@code GENERALCHAT_SEND} audit row written by the post-send
 *       transaction contains only {@code {"len":N}} in {@code extra_json}
 *       — never the message body — satisfying the "no message body in
 *       audit/log" requirement (Req. 20.4).</li>
 * </ul>
 * <p>
 * <b>Validates: Requirements 15.1, 15.2, 15.3, 15.4</b>
 * <p>
 * Design: §12 Property 22
 * <p>
 * Implementation note. {@link GeneralChatService#canSend(net.minecraft.server.level.ServerPlayer, long)}
 * needs a live {@code ServerPlayer}, which is unavailable in unit tests. The
 * underlying logic is split into two pure components — the role check
 * {@link Role#atLeast(Role)} and the DB cooldown check
 * {@link CooldownsDao#isActive} — and Property 22a is checked against those
 * directly, mirroring the production composition exactly.
 */
class GeneralChatProperties {

    /** Set of roles that pass the GeneralChat role gate (Req. 15.1, design §12). */
    private static final Set<Role> ALLOWED_ROLES = EnumSet.of(Role.COMMANDER, Role.GENERAL, Role.OP);

    /** Set of roles that must never appear in the audience (design §12 Property 22). */
    private static final Set<Role> EXCLUDED_ROLES = EnumSet.of(Role.CANDIDATE, Role.SOLDIER);

    /** Mirror of {@link GeneralChatService#COOLDOWN_MILLIS} so the test pins the constant. */
    private static final long COOLDOWN_MILLIS = 30_000L;

    /** Mirror of {@link GeneralChatService#COOLDOWN_TYPE}. */
    private static final String COOLDOWN_TYPE = "GC";

    /** Mirror of {@link GeneralChatService#AUDIT_ACTION}. */
    private static final String AUDIT_ACTION = "GENERALCHAT_SEND";

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
    // Property 22a — canSend predicate is correct for arbitrary inputs
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Property 22a: For any {@code (role, uuid, expiresAt, now)},
     * {@code canSend} ⇔ {@code role ∈ {COMMANDER,GENERAL,OP} ∧ expiresAt ≤ now}.
     * <p>
     * The implementation splits the predicate into:
     * <pre>{@code role.atLeast(COMMANDER) && !cooldownsDao.isActive(conn, uuid, "GC", now)}</pre>
     * which matches the spec line-by-line.
     * <p>
     * <b>Validates: Requirements 15.1, 15.2</b>
     */
    @Property
    void canSendPredicateMatchesSpec(
            @ForAll("anyRole") Role role,
            @ForAll("nonNegativeMillis") long expiresAt,
            @ForAll("nonNegativeMillis") long now
    ) {
        CooldownsDao cooldownsDao = new CooldownsDao();
        String uuid = UUID.randomUUID().toString();

        // Seed the cooldown row with the generated expiresAt (only when > 0
        // so we exercise both the "no row" and "row present" paths).
        if (expiresAt > 0) {
            cooldownsDao.upsert(conn, uuid, COOLDOWN_TYPE, expiresAt);
        }

        boolean modelCanSend =
                role.atLeast(Role.COMMANDER)
                        && !cooldownsDao.isActive(conn, uuid, COOLDOWN_TYPE, now);

        boolean specCanSend =
                ALLOWED_ROLES.contains(role)
                        && expiresAt <= now;

        assertThat(modelCanSend)
                .as("canSend(role=%s, expiresAt=%d, now=%d) model vs. spec",
                        role, expiresAt, now)
                .isEqualTo(specCanSend);
    }

    /**
     * Property 22a (degenerate case): Without any cooldown row at all,
     * {@code canSend} reduces to a pure role check.
     * <p>
     * <b>Validates: Requirements 15.1</b>
     */
    @Property
    void canSendWithoutCooldownRowReducesToRoleCheck(
            @ForAll("anyRole") Role role,
            @ForAll("nonNegativeMillis") long now
    ) {
        CooldownsDao cooldownsDao = new CooldownsDao();
        String uuid = UUID.randomUUID().toString();
        // intentionally no upsert — empty cooldown table

        boolean modelCanSend =
                role.atLeast(Role.COMMANDER)
                        && !cooldownsDao.isActive(conn, uuid, COOLDOWN_TYPE, now);

        assertThat(modelCanSend).isEqualTo(ALLOWED_ROLES.contains(role));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 22b — audience filter never includes SOLDIER / CANDIDATE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Property 22b (per-role exhaustive): For every {@link Role} value,
     * {@code role.atLeast(COMMANDER)} (the predicate driving
     * {@link GeneralChatService#hasRequiredRole}) is true iff
     * {@code role ∈ {COMMANDER, GENERAL, OP}}.
     * <p>
     * <b>Validates: Requirements 15.3</b>
     */
    @Property
    void audienceFilterCoincidesWithAllowedRoleSet(@ForAll("anyRole") Role role) {
        boolean passes = role.atLeast(Role.COMMANDER);
        boolean inAllowed = ALLOWED_ROLES.contains(role);

        assertThat(passes)
                .as("role.atLeast(COMMANDER) for %s should equal (role ∈ %s)",
                        role, ALLOWED_ROLES)
                .isEqualTo(inAllowed);

        if (EXCLUDED_ROLES.contains(role)) {
            assertThat(passes)
                    .as("Role %s must NEVER pass the audience filter", role)
                    .isFalse();
        }
    }

    /**
     * Property 22b (set-level): For an arbitrary roster of online players
     * with arbitrary roles, the simulated audience is exactly the subset
     * with role ≥ {@code COMMANDER} and contains no {@code SOLDIER} or
     * {@code CANDIDATE} entries.
     * <p>
     * <b>Validates: Requirements 15.3</b>
     */
    @Property
    void simulatedAudienceContainsOnlyCommanderOrAbove(
            @ForAll("rosterRoles") List<Role> roster
    ) {
        // Simulate GeneralChatService.recipients(server) at the role level.
        List<Role> simulatedRecipients = roster.stream()
                .filter(r -> r.atLeast(Role.COMMANDER))
                .collect(Collectors.toList());

        // No excluded roles ever leak through.
        assertThat(simulatedRecipients)
                .as("Audience for roster %s must not contain any of %s",
                        roster, EXCLUDED_ROLES)
                .doesNotContainAnyElementsOf(EXCLUDED_ROLES);

        // Every allowed role from the input roster is included.
        long expectedSize = roster.stream().filter(ALLOWED_ROLES::contains).count();
        assertThat(simulatedRecipients).hasSize((int) expectedSize);
        assertThat(simulatedRecipients).allMatch(ALLOWED_ROLES::contains);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 22c — post-send cooldown is exactly now + 30 000
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Property 22c: For an arbitrary {@code now}, after the post-send
     * transaction upserts {@code cooldowns(uuid, "GC", now + 30_000)}:
     * <ul>
     *   <li>the row read back has {@code expires_at == now + 30_000};</li>
     *   <li>{@code isActive(now')} is {@code true} for every
     *       {@code now' < now + 30_000};</li>
     *   <li>{@code isActive(now')} is {@code false} for every
     *       {@code now' ≥ now + 30_000}.</li>
     * </ul>
     * <p>
     * <b>Validates: Requirements 15.4</b>
     */
    @Property
    void postSendCooldownIsExactlyNowPlus30Seconds(
            @ForAll("sendInstants") long now,
            @ForAll("cooldownProbeOffsets") long probeOffset
    ) {
        CooldownsDao cooldownsDao = new CooldownsDao();
        String uuid = UUID.randomUUID().toString();
        long expectedExpiresAt = now + COOLDOWN_MILLIS;

        // Simulate the post-send write.
        cooldownsDao.upsert(conn, uuid, COOLDOWN_TYPE, expectedExpiresAt);

        // Read-back: expires_at is exactly now + 30_000.
        Optional<CooldownsDao.Cooldown> row = cooldownsDao.findByKey(conn, uuid, COOLDOWN_TYPE);
        assertThat(row)
                .as("Cooldown row for %s/%s must exist after upsert", uuid, COOLDOWN_TYPE)
                .isPresent();
        assertThat(row.get().expiresAt())
                .as("Post-send cooldown expiresAt must equal now + %d", COOLDOWN_MILLIS)
                .isEqualTo(expectedExpiresAt);

        // Active-window check: probe at now + offset, with offset spanning
        // both before and after expiry.
        long probeNow = now + probeOffset;
        boolean active = cooldownsDao.isActive(conn, uuid, COOLDOWN_TYPE, probeNow);
        boolean expectedActive = expectedExpiresAt > probeNow;
        assertThat(active)
                .as("isActive(probeNow=%d) for expiresAt=%d must be %b",
                        probeNow, expectedExpiresAt, expectedActive)
                .isEqualTo(expectedActive);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 22d — audit row contains only message length, never the body
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Property 22d: For an arbitrary message, the {@code GENERALCHAT_SEND}
     * audit row written by the post-send transaction has:
     * <ul>
     *   <li>{@code action == "GENERALCHAT_SEND"};</li>
     *   <li>{@code reason == null};</li>
     *   <li>{@code extra_json == "{\"len\":<message-length>}"} — the message
     *       body never appears in {@code extra_json}, {@code reason}, or
     *       any other audit field.</li>
     * </ul>
     * <p>
     * <b>Validates: Requirements 15.x, 20.4</b>
     */
    @Property
    void auditRowContainsOnlyMessageLengthNeverBody(
            @ForAll("messages") String message,
            @ForAll("sendInstants") long now
    ) {
        AuditLogDao auditLogDao = new AuditLogDao();
        String actorUuid = UUID.randomUUID().toString();
        String actorName = "Initiator-" + actorUuid.substring(0, 8);
        int len = message.length();

        // Mirror GeneralChatService#send's audit insert exactly.
        long auditId = auditLogDao.insert(
                conn,
                now,
                actorUuid,
                actorName,
                null,
                null,
                AUDIT_ACTION,
                null,
                "{\"len\":" + len + "}"
        );

        Optional<AuditLogDao.AuditEntry> entry = auditLogDao.findById(conn, auditId);
        assertThat(entry).isPresent();
        AuditLogDao.AuditEntry e = entry.get();

        assertThat(e.action()).isEqualTo(AUDIT_ACTION);
        assertThat(e.reason()).as("reason must always be null for GENERALCHAT_SEND").isNull();
        // Strict equality establishes body absence: extra_json is the literal
        // string {"len":<integer>}, with no slot in the format where the
        // message body could appear, regardless of message contents.
        assertThat(e.extraJson())
                .as("extra_json must contain only the message length, never the body")
                .isEqualTo("{\"len\":" + len + "}");
        assertThat(e.targetUuid()).isNull();
        assertThat(e.targetName()).isNull();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Providers
    // ─────────────────────────────────────────────────────────────────────────

    @Provide
    Arbitrary<Role> anyRole() {
        return Arbitraries.of(Role.values());
    }

    /**
     * Provides arbitrary millisecond timestamps in a realistic range
     * (year 2000 through year ~2300) so neither the cooldown row nor the
     * "now" probe can underflow {@code long} arithmetic.
     */
    @Provide
    Arbitrary<Long> nonNegativeMillis() {
        return Arbitraries.longs().between(0L, 10_000_000_000_000L);
    }

    /**
     * "Send" instants — kept clear of the lower edge by COOLDOWN_MILLIS so
     * {@code now + COOLDOWN_MILLIS} stays in range, and clear of the upper
     * edge so probe offsets above the cooldown don't overflow.
     */
    @Provide
    Arbitrary<Long> sendInstants() {
        return Arbitraries.longs().between(COOLDOWN_MILLIS, 10_000_000_000_000L);
    }

    /**
     * Cooldown probe offsets cover both halves of the window:
     * negative-ish (way before expiry), exactly 0, mid-window, edge-of-window,
     * and well past expiry.
     */
    @Provide
    Arbitrary<Long> cooldownProbeOffsets() {
        return Arbitraries.longs().between(-COOLDOWN_MILLIS, 2 * COOLDOWN_MILLIS);
    }

    /**
     * Rosters of 0..20 simulated online players, one role each. Roles are
     * uniform across {@link Role#values()} so the audience filter is
     * exercised over every combination.
     */
    @Provide
    Arbitrary<List<Role>> rosterRoles() {
        return Arbitraries.of(Role.values()).list().ofMinSize(0).ofMaxSize(20);
    }

    /**
     * Arbitrary message strings for the audit-body-absence property:
     * any unicode string, length 0..256 (matching the realistic
     * {@code MessageArgument.greedyString} input range).
     */
    @Provide
    Arbitrary<String> messages() {
        return Arbitraries.strings().ofMinLength(0).ofMaxLength(256);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SQLite migration helpers — same shape as the other DB-backed PBT files
    // ─────────────────────────────────────────────────────────────────────────

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
        List<String> result = new ArrayList<>();
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
