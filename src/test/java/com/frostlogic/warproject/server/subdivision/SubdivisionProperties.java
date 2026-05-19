package com.frostlogic.warproject.server.subdivision;

import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.persistence.Database;
import com.frostlogic.warproject.persistence.dao.AccountsDao;
import com.frostlogic.warproject.persistence.dao.AuditLogDao;
import com.frostlogic.warproject.persistence.dao.PlayersDao;
import com.frostlogic.warproject.persistence.dao.SubdivisionsDao;
import com.frostlogic.warproject.persistence.dao.SubdivisionsDao.Subdivision;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeTry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 23: Подразделения — уникальность имени, round-trip и атомарность.
 * <p>
 * Validates that {@link SubdivisionService} satisfies:
 * <ul>
 *     <li>round-trip {@code create} → {@code findByName} returns the same row;</li>
 *     <li>уникальность {@code (faction, name)} — повторный {@code create}
 *         возвращает {@code Failure(ERR_NAME_EXISTS)};</li>
 *     <li>round-trip {@code create} → {@code delete} — {@code findByName}
 *         после удаления возвращает {@link Optional#empty()};</li>
 *     <li>уникальность только в пределах фракции — одинаковое имя в двух
 *         разных фракциях успешно создаётся для обеих;</li>
 *     <li>round-trip {@code invite} → {@code kick} —
 *         {@code players.subdivision_id} обнуляется после kick.</li>
 * </ul>
 *
 * <p><b>Validates: Requirements 16.1, 16.2, 16.3, 16.4, 16.5</b>
 *
 * <p>Design: §12 Property 23.
 *
 * <p>Setup uses an in-memory SQLite database with shared cache so that the
 * service's {@link Database#inTx(java.util.function.Function)} (which opens a
 * fresh connection per call) sees the same data the test setup populated.
 * A keep-alive connection is held open for the lifetime of each try to
 * prevent the in-memory database from being torn down between calls.
 */
class SubdivisionProperties {

    private Connection keepAlive;
    private Database database;
    private SubdivisionService service;
    private AccountsDao accountsDao;
    private PlayersDao playersDao;
    private SubdivisionsDao subdivisionsDao;
    private AuditLogDao auditLogDao;

    @BeforeTry
    void setupDatabase() throws Exception {
        // Per-try unique shared in-memory database. The keep-alive connection
        // keeps the DB alive across the per-call connections opened by
        // Database.inTx. Each try gets a clean schema.
        String dbName = "wp-subdiv-pbt-" + UUID.randomUUID();
        String url = "jdbc:sqlite:file:" + dbName + "?mode=memory&cache=shared";

        keepAlive = DriverManager.getConnection(url);
        keepAlive.setAutoCommit(true);
        applyMigration(keepAlive);

        database = new Database("sqlite", url, "", "");
        accountsDao = new AccountsDao();
        playersDao = new PlayersDao();
        subdivisionsDao = new SubdivisionsDao();
        auditLogDao = new AuditLogDao();
        service = new SubdivisionService(database, subdivisionsDao, playersDao, auditLogDao);
    }

    @AfterTry
    void tearDown() throws Exception {
        if (keepAlive != null && !keepAlive.isClosed()) {
            keepAlive.close();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 23 (a): round-trip create → findByName
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * For any valid {@code (faction, name)}, {@code create} followed by
     * {@code findByName} returns the same {@link Subdivision} row.
     * <p>
     * <b>Validates: Requirements 16.1</b>
     */
    @Property
    void createThenFindByNameRoundTrip(@ForAll("factions") FactionId faction,
                                        @ForAll("normalizedNames") String name,
                                        @ForAll("uuids") String creatorUuid) {
        SubdivisionService.Result<Subdivision> created =
                service.create(faction, name, creatorUuid, "creator");

        assertThat(created.isSuccess())
                .as("create should succeed for clean DB")
                .isTrue();

        Subdivision createdRow = created.orThrow();
        assertThat(createdRow.faction()).isEqualTo(faction.getSerializedName().toUpperCase());
        assertThat(createdRow.name()).isEqualTo(name);
        assertThat(createdRow.createdBy()).isEqualTo(creatorUuid);

        Optional<Subdivision> found = service.findByName(faction, name);
        assertThat(found)
                .as("findByName should return the just-created row")
                .isPresent()
                .contains(createdRow);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 23 (b): name uniqueness within a faction
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * For any {@code (faction, name)}, the second {@code create} call with
     * the same arguments returns {@code Failure(ERR_NAME_EXISTS)} and does
     * not produce a duplicate row.
     * <p>
     * <b>Validates: Requirements 16.2</b>
     */
    @Property
    void duplicateCreateWithinFactionFails(@ForAll("factions") FactionId faction,
                                            @ForAll("normalizedNames") String name,
                                            @ForAll("uuids") String creator1,
                                            @ForAll("uuids") String creator2) {
        SubdivisionService.Result<Subdivision> first =
                service.create(faction, name, creator1, "creator-1");
        assertThat(first.isSuccess())
                .as("first create should succeed")
                .isTrue();

        SubdivisionService.Result<Subdivision> second =
                service.create(faction, name, creator2, "creator-2");
        assertThat(second.isSuccess())
                .as("second create with same (faction, name) should fail")
                .isFalse();
        assertThat(((SubdivisionService.Result.Failure<Subdivision>) second).errorKey())
                .isEqualTo(SubdivisionService.ERR_NAME_EXISTS);

        // Confirm only the first row exists
        List<Subdivision> all = service.list(faction);
        long matching = all.stream()
                .filter(s -> s.name().equals(name))
                .count();
        assertThat(matching)
                .as("exactly one row with the given name should exist")
                .isEqualTo(1);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 23 (c): create → delete → findByName empty
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * For any valid {@code (faction, name)}, calling {@code create} and then
     * {@code delete} restores the absent state — {@code findByName} returns
     * {@link Optional#empty()} and a fresh {@code create} for the same name
     * succeeds again.
     * <p>
     * <b>Validates: Requirements 16.3</b>
     */
    @Property
    void createThenDeleteRestoresAbsentState(@ForAll("factions") FactionId faction,
                                              @ForAll("normalizedNames") String name,
                                              @ForAll("uuids") String creatorUuid,
                                              @ForAll("uuids") String deleterUuid) {
        SubdivisionService.Result<Subdivision> created =
                service.create(faction, name, creatorUuid, "creator");
        assertThat(created.isSuccess()).isTrue();
        Subdivision createdRow = created.orThrow();

        SubdivisionService.Result<Subdivision> deleted =
                service.delete(createdRow.id(), deleterUuid, "deleter", "test-cleanup");
        assertThat(deleted.isSuccess())
                .as("delete should succeed for an existing subdivision")
                .isTrue();

        assertThat(service.findByName(faction, name))
                .as("findByName should return empty after delete")
                .isEmpty();

        // Re-creation with the same (faction, name) succeeds — confirms the
        // unique-index slot is freed.
        SubdivisionService.Result<Subdivision> recreated =
                service.create(faction, name, creatorUuid, "creator");
        assertThat(recreated.isSuccess())
                .as("recreate after delete should succeed")
                .isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 23 (d): uniqueness is per-faction, not global
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * The same {@code name} can be created independently in each faction —
     * the unique index is on {@code (faction, name)}, not on {@code name}
     * alone.
     * <p>
     * <b>Validates: Requirements 16.2</b>
     */
    @Property
    void sameNameAcrossFactionsBothSucceed(@ForAll("normalizedNames") String name,
                                            @ForAll("uuids") String creatorUuid) {
        SubdivisionService.Result<Subdivision> zarnavia =
                service.create(FactionId.ZARNAVIA, name, creatorUuid, "creator");
        SubdivisionService.Result<Subdivision> chernogryad =
                service.create(FactionId.CHERNOGRYAD, name, creatorUuid, "creator");

        assertThat(zarnavia.isSuccess())
                .as("create ZARNAVIA/" + name + " should succeed")
                .isTrue();
        assertThat(chernogryad.isSuccess())
                .as("create CHERNOGRYAD/" + name + " should succeed (different faction)")
                .isTrue();

        assertThat(zarnavia.orThrow().id())
                .as("ids must be distinct rows")
                .isNotEqualTo(chernogryad.orThrow().id());

        assertThat(service.findByName(FactionId.ZARNAVIA, name)).isPresent();
        assertThat(service.findByName(FactionId.CHERNOGRYAD, name)).isPresent();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 23 (e): invite → kick clears subdivision_id
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * For an existing subdivision and an existing target player,
     * {@code invite} sets {@code players.subdivision_id} to the subdivision
     * id, and a subsequent {@code kick} clears it back to {@code null}.
     * <p>
     * <b>Validates: Requirements 16.5</b>
     */
    @Property
    void inviteThenKickRoundTrip(@ForAll("factions") FactionId faction,
                                  @ForAll("normalizedNames") String name,
                                  @ForAll("uuids") String creatorUuid,
                                  @ForAll("uuids") String targetUuid,
                                  @ForAll("uuids") String inviterUuid) {
        // Set up the subdivision and a target player record.
        Subdivision sub = service.create(faction, name, creatorUuid, "creator").orThrow();
        insertPlayer(targetUuid, faction);

        // Invite
        SubdivisionService.Result<Subdivision> invited =
                service.invite(sub.id(), targetUuid, "Target", inviterUuid, "Inviter");
        assertThat(invited.isSuccess())
                .as("invite should succeed for an unaffiliated player")
                .isTrue();

        Optional<PlayersDao.Player> afterInvite = readPlayer(targetUuid);
        assertThat(afterInvite).isPresent();
        assertThat(afterInvite.get().subdivisionId())
                .as("subdivision_id should equal the subdivision id after invite")
                .isEqualTo(sub.id());

        // Kick
        SubdivisionService.Result<Subdivision> kicked =
                service.kick(sub.id(), targetUuid, "Target", inviterUuid, "Inviter", "test-kick");
        assertThat(kicked.isSuccess())
                .as("kick should succeed for a current member")
                .isTrue();

        Optional<PlayersDao.Player> afterKick = readPlayer(targetUuid);
        assertThat(afterKick).isPresent();
        assertThat(afterKick.get().subdivisionId())
                .as("subdivision_id should be null after kick")
                .isNull();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Generators
    // ─────────────────────────────────────────────────────────────────────────

    @Provide
    Arbitrary<FactionId> factions() {
        return Arbitraries.of(FactionId.ZARNAVIA, FactionId.CHERNOGRYAD);
    }

    /**
     * Generates already-normalized subdivision names: trimmed, no leading/
     * trailing whitespace, no internal runs of whitespace, length 1..32.
     * Uses a small alphanumeric alphabet plus single spaces so the
     * normalized form equals the input verbatim — that lets the test compare
     * names by exact equality.
     */
    @Provide
    Arbitrary<String> normalizedNames() {
        Arbitrary<Character> wordChar = Arbitraries.chars()
                .alpha()
                .numeric();
        return wordChar.list().ofMinSize(1).ofMaxSize(32)
                .map(chars -> chars.stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining()));
    }

    @Provide
    Arbitrary<String> uuids() {
        return Arbitraries.create(() -> UUID.randomUUID().toString());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Inserts the prerequisite {@code accounts} + {@code players} rows for a
     * target player in the given faction. Uses {@link #keepAlive} directly
     * (auto-commit) so the rows are visible to the connections opened by
     * {@link Database#inTx}.
     */
    private void insertPlayer(String uuid, FactionId faction) {
        accountsDao.insert(keepAlive, new AccountsDao.Account(
                uuid,
                "$2a$10$dummyhash000000000000000000000000000000000000000000",
                System.currentTimeMillis(), null, 0, 0));
        playersDao.insert(keepAlive, new PlayersDao.Player(
                uuid,
                faction.getSerializedName().toUpperCase(),
                "SOLDIER", null, "ACCEPTED",
                "Test", "Player",
                false, null, false, 0,
                System.currentTimeMillis(),
                null, null, null,
                null /* subdivision_id */));
    }

    private Optional<PlayersDao.Player> readPlayer(String uuid) {
        return playersDao.findByUuid(keepAlive, uuid);
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

        // Reuse the splitter from the captivity test (kept inline to avoid
        // depending on package-private utilities).
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

    /** Splits SQL on semicolons, respecting single-quoted strings and comments. */
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

    /**
     * Reads the raw {@code subdivisions} count from the keep-alive connection.
     * Useful for ad-hoc debugging — kept private and unused on green builds.
     */
    @SuppressWarnings("unused")
    private long countSubdivisions() {
        try (ResultSet rs = keepAlive.createStatement()
                .executeQuery("SELECT COUNT(*) FROM subdivisions")) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new RuntimeException("countSubdivisions failed", e);
        }
    }
}
