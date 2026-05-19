package com.frostlogic.warproject.persistence;

import com.frostlogic.warproject.persistence.dao.*;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeTry;
import org.assertj.core.api.Assertions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for DAO round-trip: insert → read by key returns identical object.
 * For audit_log — additionally validates Property 27 (no secrets in extra_json).
 * <p>
 * Uses in-memory SQLite ({@code jdbc:sqlite::memory:}) with V1 migration applied before each test.
 * <p>
 * <b>Validates: Requirements 18.2, 20.2</b>
 * <p>
 * Design: §12 Property 13, 27
 */
class DaoRoundTripProperties {

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

    private void applyMigration(Connection conn) throws IOException, SQLException {
        InputStream is = getClass().getClassLoader().getResourceAsStream("db/migrations/V1__init.sql");
        if (is == null) {
            throw new IOException("V1__init.sql not found on classpath");
        }
        String sql;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            sql = reader.lines().collect(Collectors.joining("\n"));
        }
        // Replace ${AI} token with AUTOINCREMENT for SQLite
        sql = sql.replace("${AI}", "AUTOINCREMENT");

        // Use Migrations.splitStatements which correctly handles comments and quotes
        String[] statements = Migrations.splitStatements(sql);
        try (Statement stmt = conn.createStatement()) {
            for (String s : statements) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) {
                    stmt.execute(trimmed);
                }
            }
        }
    }

    // ==================== AccountsDao ====================

    @Property
    void accountsRoundTrip(@ForAll("accounts") AccountsDao.Account account) {
        AccountsDao dao = new AccountsDao();
        dao.insert(conn, account);

        Optional<AccountsDao.Account> found = dao.findByUuid(conn, account.uuid());
        assertThat(found).isPresent();
        assertThat(found.get()).isEqualTo(account);
    }

    @Provide
    Arbitrary<AccountsDao.Account> accounts() {
        return Combinators.combine(
                uuids(),
                Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(60),
                Arbitraries.longs().between(0L, System.currentTimeMillis()),
                Arbitraries.longs().between(0L, System.currentTimeMillis()).injectNull(0.3),
                Arbitraries.integers().between(0, 10),
                Arbitraries.longs().between(0L, System.currentTimeMillis())
        ).as(AccountsDao.Account::new);
    }

    // ==================== PlayersDao ====================

    @Property
    void playersRoundTrip(@ForAll("players") PlayersDao.Player player) {
        // Insert prerequisite account
        AccountsDao accountsDao = new AccountsDao();
        accountsDao.insert(conn, new AccountsDao.Account(
                player.uuid(), "$2a$10$dummyhash000000000000000000000000000000000000000000",
                System.currentTimeMillis(), null, 0, 0));

        PlayersDao dao = new PlayersDao();
        dao.insert(conn, player);

        Optional<PlayersDao.Player> found = dao.findByUuid(conn, player.uuid());
        assertThat(found).isPresent();
        assertThat(found.get()).isEqualTo(player);
    }

    @Provide
    Arbitrary<PlayersDao.Player> players() {
        Arbitrary<String> factions = Arbitraries.of("ZARNAVIA", "CHERNOGRYAD").injectNull(0.2);
        Arbitrary<String> roles = Arbitraries.of("CANDIDATE", "SOLDIER", "COMMANDER", "GENERAL", "OP");
        Arbitrary<String> ranks = Arbitraries.of("PRIVATE", "SERGEANT", "LIEUTENANT").injectNull(0.3);
        Arbitrary<String> statuses = Arbitraries.of("NEW", "CANDIDATE", "ACCEPTED", "CAPTURED");
        Arbitrary<String> names = Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(16).injectNull(0.2);
        Arbitrary<String> reasons = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(50).injectNull(0.5);

        return Combinators.combine(
                uuids(), factions, roles, ranks, statuses, names, names
        ).as((uuid, faction, role, rank, status, rpName, rpSurname) ->
                new PlayersDao.Player(uuid, faction, role, rank, status, rpName, rpSurname,
                        false, null, false, 0,
                        System.currentTimeMillis(), null, null, null, null)
        ).flatMap(base -> Combinators.combine(
                Arbitraries.of(true, false),
                reasons,
                Arbitraries.of(true, false),
                Arbitraries.integers().between(0, 10000),
                Arbitraries.longs().between(0L, System.currentTimeMillis()).injectNull(0.4),
                uuids().injectNull(0.5),
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(16).injectNull(0.5)
        ).as((collab, collabReason, captured, enemyTicks, acceptedAt, acceptedByUuid, acceptedByName) ->
                new PlayersDao.Player(base.uuid(), base.faction(), base.role(), base.rank(),
                        base.status(), base.rpName(), base.rpSurname(),
                        collab, collab ? collabReason : null, captured, enemyTicks,
                        base.joinedAt(), acceptedAt, acceptedByUuid, acceptedByName, null)
        ));
    }

    // ==================== PassportsDao ====================

    @Property
    void passportsRoundTrip(@ForAll("passports") PassportsDao.Passport passport) {
        // Insert prerequisite account and player
        AccountsDao accountsDao = new AccountsDao();
        accountsDao.insert(conn, new AccountsDao.Account(
                passport.ownerUuid(), "$2a$10$dummyhash000000000000000000000000000000000000000000",
                System.currentTimeMillis(), null, 0, 0));
        PlayersDao playersDao = new PlayersDao();
        playersDao.insert(conn, new PlayersDao.Player(
                passport.ownerUuid(), passport.faction(), "SOLDIER", null, "ACCEPTED",
                passport.rpName(), passport.rpSurname(), false, null, false, 0,
                System.currentTimeMillis(), null, null, null, null));

        PassportsDao dao = new PassportsDao();
        dao.insert(conn, passport);

        Optional<PassportsDao.Passport> found = dao.findById(conn, passport.passportId());
        assertThat(found).isPresent();
        assertThat(found.get()).isEqualTo(passport);
    }

    @Provide
    Arbitrary<PassportsDao.Passport> passports() {
        Arbitrary<String> passportIds = Arbitraries.of("ZRN-", "CHN-")
                .flatMap(p -> Arbitraries.integers().between(1, 999999)
                        .map(n -> p + String.format("%06d", n)));
        Arbitrary<String> factions = Arbitraries.of("ZARNAVIA", "CHERNOGRYAD");
        Arbitrary<String> statuses = Arbitraries.of("CANDIDATE", "ACCEPTED", "CAPTURED");

        // Split into two combines since jqwik supports max 8 params
        return Combinators.combine(
                passportIds, uuids(), factions,
                Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(16),
                Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(16),
                Arbitraries.of("01.01.1980", "15.06.1995", "28.02.2000", "31.12.1974"),
                Arbitraries.longs(),
                statuses
        ).flatAs((id, owner, faction, rpName, rpSurname, dob, sigSeed, status) ->
                Combinators.combine(
                        Arbitraries.longs().between(0L, System.currentTimeMillis()).injectNull(0.4),
                        Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(16).injectNull(0.4),
                        uuids().injectNull(0.5),
                        Arbitraries.of(true, false),
                        Arbitraries.longs().between(0L, System.currentTimeMillis())
                ).as((acceptedAt, acceptedBy, capturedByUuid, trophy, createdAt) ->
                        new PassportsDao.Passport(id, owner, faction, rpName, rpSurname,
                                dob, sigSeed, status, acceptedAt, acceptedBy,
                                capturedByUuid, trophy, createdAt))
        );
    }

    // ==================== SubdivisionsDao ====================

    @Property
    void subdivisionsRoundTrip(@ForAll("subdivisionInputs") SubdivisionInput input) {
        SubdivisionsDao dao = new SubdivisionsDao();
        int id = dao.insert(conn, input.faction(), input.name(), input.createdBy(), input.createdAt());

        Optional<SubdivisionsDao.Subdivision> found = dao.findById(conn, id);
        assertThat(found).isPresent();
        assertThat(found.get().faction()).isEqualTo(input.faction());
        assertThat(found.get().name()).isEqualTo(input.name());
        assertThat(found.get().createdBy()).isEqualTo(input.createdBy());
        assertThat(found.get().createdAt()).isEqualTo(input.createdAt());
    }

    record SubdivisionInput(String faction, String name, String createdBy, long createdAt) {}

    @Provide
    Arbitrary<SubdivisionInput> subdivisionInputs() {
        return Combinators.combine(
                Arbitraries.of("ZARNAVIA", "CHERNOGRYAD"),
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20),
                uuids(),
                Arbitraries.longs().between(0L, System.currentTimeMillis())
        ).as(SubdivisionInput::new);
    }

    // ==================== AuditLogDao (Property 27) ====================

    /**
     * Property 27: round-trip insert → findById returns identical entry.
     * Additionally verifies that extra_json does NOT contain password or captcha code substrings.
     * <p>
     * <b>Validates: Requirements 18.2, 20.2</b>
     */
    @Property
    void auditLogRoundTrip(@ForAll("auditEntries") AuditInput input) {
        AuditLogDao dao = new AuditLogDao();
        long id = dao.insert(conn, input.tsUtc(), input.actorUuid(), input.actorName(),
                input.targetUuid(), input.targetName(), input.action(),
                input.reason(), input.extraJson());

        Optional<AuditLogDao.AuditEntry> found = dao.findById(conn, id);
        assertThat(found).isPresent();
        AuditLogDao.AuditEntry entry = found.get();
        assertThat(entry.id()).isEqualTo(id);
        assertThat(entry.tsUtc()).isEqualTo(input.tsUtc());
        assertThat(entry.actorUuid()).isEqualTo(input.actorUuid());
        assertThat(entry.actorName()).isEqualTo(input.actorName());
        assertThat(entry.targetUuid()).isEqualTo(input.targetUuid());
        assertThat(entry.targetName()).isEqualTo(input.targetName());
        assertThat(entry.action()).isEqualTo(input.action());
        assertThat(entry.reason()).isEqualTo(input.reason());
        assertThat(entry.extraJson()).isEqualTo(input.extraJson());

        // Property 27: extra_json must NOT contain passwords or captcha codes
        if (entry.extraJson() != null) {
            assertThat(entry.extraJson()).doesNotContainIgnoringCase("password");
            assertThat(entry.extraJson()).doesNotContainIgnoringCase("captcha_code");
            assertThat(entry.extraJson()).doesNotContainIgnoringCase("secret");
            assertThat(entry.extraJson()).doesNotContainIgnoringCase("$2a$");
        }
    }

    record AuditInput(long tsUtc, String actorUuid, String actorName,
                      String targetUuid, String targetName, String action,
                      String reason, String extraJson) {}

    @Provide
    Arbitrary<AuditInput> auditEntries() {
        Arbitrary<String> actions = Arbitraries.of(
                "REGISTER", "LOGIN_FAIL", "CAPTCHA_OK", "CAPTCHA_FAIL",
                "CHOOSE_FACTION", "ACCEPT", "RANK_UP", "BAN", "KICK",
                "MUTE", "WARN", "CAPTURE_PASSPORT", "COLLAB", "UNCOLLAB",
                "SUBDIV_CREATE", "SUBDIV_DELETE", "GENERALCHAT_SEND"
        );
        // extra_json must never contain secrets — generate safe JSON-like content
        Arbitrary<String> safeExtraJson = Arbitraries.of(
                null,
                "{\"faction\":\"ZARNAVIA\"}",
                "{\"rank\":\"SERGEANT\"}",
                "{\"duration_days\":7}",
                "{\"subdivision\":\"Alpha\"}",
                "{\"old_status\":\"CANDIDATE\",\"new_status\":\"ACCEPTED\"}"
        );

        return Combinators.combine(
                Arbitraries.longs().between(0L, System.currentTimeMillis()),
                uuids().injectNull(0.2),
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(16).injectNull(0.2),
                uuids().injectNull(0.3),
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(16).injectNull(0.3),
                actions,
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(50).injectNull(0.3),
                safeExtraJson
        ).as(AuditInput::new);
    }

    // ==================== BansDao ====================

    @Property
    void bansRoundTrip(@ForAll("bans") BansDao.Ban ban) {
        BansDao dao = new BansDao();
        dao.upsert(conn, ban);

        Optional<BansDao.Ban> found = dao.findByUuid(conn, ban.uuid());
        assertThat(found).isPresent();
        assertThat(found.get()).isEqualTo(ban);
    }

    @Provide
    Arbitrary<BansDao.Ban> bans() {
        return Combinators.combine(
                uuids(),
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(50),
                uuids(),
                Arbitraries.longs().between(0L, System.currentTimeMillis()),
                Arbitraries.longs().between(System.currentTimeMillis(), System.currentTimeMillis() + 86400000L * 365)
        ).as(BansDao.Ban::new);
    }

    // ==================== MutesDao ====================

    @Property
    void mutesRoundTrip(@ForAll("mutes") MutesDao.Mute mute) {
        MutesDao dao = new MutesDao();
        dao.upsert(conn, mute);

        Optional<MutesDao.Mute> found = dao.findByUuid(conn, mute.uuid());
        assertThat(found).isPresent();
        assertThat(found.get()).isEqualTo(mute);
    }

    @Provide
    Arbitrary<MutesDao.Mute> mutes() {
        return Combinators.combine(
                uuids(),
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(50),
                uuids(),
                Arbitraries.longs().between(0L, System.currentTimeMillis()),
                Arbitraries.longs().between(System.currentTimeMillis(), System.currentTimeMillis() + 86400000L * 30)
        ).as(MutesDao.Mute::new);
    }

    // ==================== WarnsDao ====================

    @Property
    void warnsRoundTrip(@ForAll("warnInputs") WarnInput input) {
        WarnsDao dao = new WarnsDao();
        int id = dao.insert(conn, input.uuid(), input.reason(), input.issuedBy(), input.issuedAt());

        Optional<WarnsDao.Warn> found = dao.findById(conn, id);
        assertThat(found).isPresent();
        assertThat(found.get().uuid()).isEqualTo(input.uuid());
        assertThat(found.get().reason()).isEqualTo(input.reason());
        assertThat(found.get().issuedBy()).isEqualTo(input.issuedBy());
        assertThat(found.get().issuedAt()).isEqualTo(input.issuedAt());
    }

    record WarnInput(String uuid, String reason, String issuedBy, long issuedAt) {}

    @Provide
    Arbitrary<WarnInput> warnInputs() {
        return Combinators.combine(
                uuids(),
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(50),
                uuids(),
                Arbitraries.longs().between(0L, System.currentTimeMillis())
        ).as(WarnInput::new);
    }

    // ==================== CooldownsDao ====================

    @Property
    void cooldownsRoundTrip(@ForAll("cooldownInputs") CooldownInput input) {
        CooldownsDao dao = new CooldownsDao();
        dao.upsert(conn, input.uuid(), input.cdType(), input.expiresAt());

        Optional<CooldownsDao.Cooldown> found = dao.findByKey(conn, input.uuid(), input.cdType());
        assertThat(found).isPresent();
        assertThat(found.get().uuid()).isEqualTo(input.uuid());
        assertThat(found.get().cdType()).isEqualTo(input.cdType());
        assertThat(found.get().expiresAt()).isEqualTo(input.expiresAt());
    }

    record CooldownInput(String uuid, String cdType, long expiresAt) {}

    @Provide
    Arbitrary<CooldownInput> cooldownInputs() {
        return Combinators.combine(
                uuids(),
                Arbitraries.of("LOGIN", "GC", "RANK_UP", "CAPTCHA"),
                Arbitraries.longs().between(System.currentTimeMillis(), System.currentTimeMillis() + 86400000L)
        ).as(CooldownInput::new);
    }

    // ==================== RanksDao ====================

    @Property
    void ranksRoundTrip(@ForAll("rankInputs") RanksDao.Rank rank) {
        RanksDao dao = new RanksDao();
        dao.insert(conn, rank);

        Optional<RanksDao.Rank> found = dao.findByKey(conn, rank.faction(), rank.rank());
        assertThat(found).isPresent();
        assertThat(found.get()).isEqualTo(rank);
    }

    @Provide
    Arbitrary<RanksDao.Rank> rankInputs() {
        return Combinators.combine(
                Arbitraries.of("ZARNAVIA", "CHERNOGRYAD"),
                Arbitraries.of("SOLDIER", "COMMANDER", "GENERAL"),
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20),
                Arbitraries.integers().between(1, 100)
        ).as(RanksDao.Rank::new);
    }

    // ==================== PassportSequenceDao ====================

    @Property
    void passportSequenceRoundTrip(@ForAll("prefixes") String prefix) {
        PassportSequenceDao dao = new PassportSequenceDao();
        dao.initializePrefix(conn, prefix);

        Optional<PassportSequenceDao.PassportSequence> found = dao.findByPrefix(conn, prefix);
        assertThat(found).isPresent();
        assertThat(found.get().prefix()).isEqualTo(prefix);
        assertThat(found.get().lastN()).isEqualTo(0);

        // Verify nextValue increments correctly
        int next = dao.nextValue(conn, prefix);
        assertThat(next).isEqualTo(1);

        Optional<PassportSequenceDao.PassportSequence> afterIncrement = dao.findByPrefix(conn, prefix);
        assertThat(afterIncrement).isPresent();
        assertThat(afterIncrement.get().lastN()).isEqualTo(1);
    }

    @Provide
    Arbitrary<String> prefixes() {
        return Arbitraries.of("ZRN-", "CHN-");
    }

    // ==================== Helpers ====================

    private Arbitrary<String> uuids() {
        return Arbitraries.create(() -> UUID.randomUUID().toString());
    }
}
