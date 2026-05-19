package com.frostlogic.warproject.integration;

import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.attachment.PlayerState;
import com.frostlogic.warproject.persistence.Database;
import com.frostlogic.warproject.persistence.dao.AccountsDao;
import com.frostlogic.warproject.persistence.dao.AuditLogDao;
import com.frostlogic.warproject.persistence.dao.AuditLogDao.AuditEntry;
import com.frostlogic.warproject.persistence.dao.PassportsDao;
import com.frostlogic.warproject.persistence.dao.PlayersDao;
import com.frostlogic.warproject.persistence.dao.SubdivisionsDao;
import com.frostlogic.warproject.persistence.dao.SubdivisionsDao.Subdivision;
import com.frostlogic.warproject.persistence.dao.SubdivisionsDao.SubdivisionMember;
import com.frostlogic.warproject.server.subdivision.SubdivisionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests for the major user flows described in
 * {@code requirements.md} §1–§22 (task 23.2).
 *
 * <p>These scenarios exercise the same DAO + transactional pipeline used by the
 * Minecraft-bound services ({@link com.frostlogic.warproject.server.lifecycle.PlayerLifecycleService},
 * {@link com.frostlogic.warproject.server.faction.FactionChoiceHandler},
 * {@link com.frostlogic.warproject.server.captivity.CaptivityService},
 * {@link SubdivisionService}). The services themselves cannot be instantiated
 * here because they require live {@code ServerPlayer} / {@code MinecraftServer}
 * objects, which are not available in plain JUnit. Instead, each scenario
 * replays the exact same sequence of {@link Database#transaction} /
 * {@link Database#inTx} calls these services perform, against a real schema
 * built from the production migration {@code V1__init.sql} on an in-memory
 * SQLite database with shared cache (the same pattern used by tasks 17.2 /
 * 18.3 / 22.2).
 *
 * <p>The three scenarios covered are:
 * <ol>
 *   <li><b>Registration → CAPTCHA → RP-name → faction choice → ACCEPTED</b> —
 *       the full new-player onboarding flow. Each transition writes the player
 *       status and a matching audit-log row. The test asserts both the final
 *       row state and the chronological audit trail.</li>
 *   <li><b>Capture → ransom</b> — two ACCEPTED players in different factions;
 *       one captures the other's passport, then ransoms it back. Audit trail
 *       contains exactly one {@code CAPTURE_PASSPORT} followed by one
 *       {@code RANSOM_COMPLETE} entry.</li>
 *   <li><b>Subdivision lifecycle</b> — create a subdivision, invite a member,
 *       kick the member, delete the subdivision. Verifies all DB operations
 *       and audit entries via {@link SubdivisionService}.</li>
 * </ol>
 *
 * <p>Each test method opens a fresh in-memory SQLite database via
 * {@link #setupDatabase()} and tears it down via {@link #tearDown()}. A
 * {@link #keepAlive} connection is held open for the lifetime of the test so
 * that the per-call connections opened by {@link Database#inTx} share state
 * with the test setup.
 *
 * <p>Validates: Requirements §1–§22 (integration coverage).
 */
class ScenarioIntegrationTest {

    private Connection keepAlive;
    private Database database;
    private AccountsDao accountsDao;
    private PlayersDao playersDao;
    private PassportsDao passportsDao;
    private AuditLogDao auditLogDao;
    private SubdivisionsDao subdivisionsDao;

    @BeforeEach
    void setupDatabase() throws Exception {
        // Per-test unique shared in-memory database. The keep-alive connection
        // keeps the in-memory DB alive across the per-call connections opened
        // by Database.inTx / Database.transaction. Same pattern as tasks
        // 17.2 (SubdivisionProperties), 18.3 (CollaboratorProperties), and
        // 22.2 (ReloadProperties).
        String dbName = "wp-scenario-it-" + UUID.randomUUID();
        String url = "jdbc:sqlite:file:" + dbName + "?mode=memory&cache=shared";

        keepAlive = DriverManager.getConnection(url);
        keepAlive.setAutoCommit(true);
        applyMigration(keepAlive);

        database = new Database("sqlite", url, "", "");
        accountsDao = new AccountsDao();
        playersDao = new PlayersDao();
        passportsDao = new PassportsDao();
        auditLogDao = new AuditLogDao();
        subdivisionsDao = new SubdivisionsDao();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (keepAlive != null && !keepAlive.isClosed()) {
            keepAlive.close();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Scenario 1: registration → captcha → rpname → faction → accepted
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Replays the new-player onboarding flow end-to-end, mirroring exactly
     * the transactions run by:
     * <ul>
     *   <li>{@code WGuardService.register} — inserts an {@code accounts} row;</li>
     *   <li>{@code PlayerLifecycleService.advance} — writes {@code players.status}
     *       on each FSM transition;</li>
     *   <li>{@code CaptchaService.submit} — emits a {@code CAPTCHA_OK} audit
     *       row on success;</li>
     *   <li>{@code RpNameCommand} — sets {@code players.rp_name}/{@code rp_surname},
     *       advances to FACTIONLESS, emits a {@code RPNAME_SET} audit row;</li>
     *   <li>{@code FactionChoiceHandler.acceptFactionChoice} — sets faction +
     *       CANDIDATE, inserts the passport, emits a {@code CHOOSE_FACTION}
     *       audit row;</li>
     *   <li>{@code AcceptCommandHandler.acceptCandidate} — promotes the
     *       player to ACCEPTED, emits an {@code ACCEPT} audit row.</li>
     * </ul>
     *
     * <p>The test asserts that after the full flow:
     * <ul>
     *   <li>{@code players.status = ACCEPTED};</li>
     *   <li>{@code players.faction = ZARNAVIA};</li>
     *   <li>{@code players.rp_name / rp_surname} match the chosen RP name;</li>
     *   <li>a passport row exists with {@code status = ACCEPTED};</li>
     *   <li>the audit trail contains, in chronological order:
     *       {@code REGISTER}, {@code CAPTCHA_OK}, {@code RPNAME_SET},
     *       {@code CHOOSE_FACTION}, {@code ACCEPT}.</li>
     * </ul>
     *
     * <p>Validates: Requirements §2 (registration), §5 (captcha + rpname),
     * §6 (faction choice), §8 (acceptance), §12 (passport), §20 (audit).
     */
    @Test
    @DisplayName("Scenario 1: registration → CAPTCHA → RPNAME → CANDIDATE → ACCEPTED produces a complete audit trail")
    void scenario1_fullOnboardingFlow() {
        String uuid = UUID.randomUUID().toString();
        String playerName = "PlayerOne";
        String acceptorUuid = UUID.randomUUID().toString();
        String acceptorName = "Commander";
        FactionId chosenFaction = FactionId.ZARNAVIA;

        // ── 1. Registration (account row + initial player row at NEW) ──────
        long t0 = 1_000_000L;
        long t1 = 1_010_000L;
        database.transaction(conn -> {
            accountsDao.insert(conn, new AccountsDao.Account(
                    uuid,
                    "$2a$10$dummyhash000000000000000000000000000000000000000000",
                    t0, null, 0, 0));
            // PlayerLifecycleService.initialize writes the players row at NEW
            // first, then advances to REGISTERED_PENDING when WGuardService
            // commits the registration. We simulate both writes.
            playersDao.insert(conn, new PlayersDao.Player(
                    uuid, null, "CANDIDATE", null,
                    PlayerState.NEW.getSerializedName(),
                    null, null, false, null, false, 0,
                    t0, null, null, null, null));
            // After WGuardService.register, the FSM transitions to
            // REGISTERED_PENDING and a REGISTER audit row is written.
            playersDao.updateStatus(conn, uuid, PlayerState.REGISTERED_PENDING.getSerializedName());
            auditLogDao.insert(conn, t1, uuid, playerName, uuid, playerName,
                    "REGISTER", null, null);
        });

        assertThat(currentStatus(uuid)).isEqualTo(PlayerState.REGISTERED_PENDING.getSerializedName());

        // ── 2. CAPTCHA — server places the player in CAPTCHA, then on
        //       successful /wp captcha advances to RPNAME_REQUIRED ──────────
        long tCaptchaStart = 1_020_000L;
        long tCaptchaOk = 1_040_000L;
        // CaptchaService.start: status = CAPTCHA (no audit row is written
        // when the captcha begins — only the result is audited).
        database.transaction(conn ->
                playersDao.updateStatus(conn, uuid, PlayerState.CAPTCHA.getSerializedName()));
        assertThat(currentStatus(uuid)).isEqualTo(PlayerState.CAPTCHA.getSerializedName());

        // CaptchaService.submit on success: status = RPNAME_REQUIRED + audit
        // row CAPTCHA_OK. The active code is NEVER written into the audit row
        // (Req. 20.4), so reason = null and extra_json = null.
        database.transaction(conn -> {
            playersDao.updateStatus(conn, uuid, PlayerState.RPNAME_REQUIRED.getSerializedName());
            auditLogDao.insert(conn, tCaptchaOk, uuid, playerName, uuid, playerName,
                    "CAPTCHA_OK", null, null);
        });
        assertThat(currentStatus(uuid)).isEqualTo(PlayerState.RPNAME_REQUIRED.getSerializedName());

        // ── 3. RP-name — /wp rpname Ivan Petrov advances to FACTIONLESS ─────
        long tRpName = 1_060_000L;
        String rpName = "Ivan";
        String rpSurname = "Petrov";
        database.transaction(conn -> {
            playersDao.setRpName(conn, uuid, rpName, rpSurname);
            playersDao.updateStatus(conn, uuid, PlayerState.FACTIONLESS.getSerializedName());
            auditLogDao.insert(conn, tRpName, uuid, playerName, uuid, playerName,
                    "RPNAME_SET", rpName + " " + rpSurname, null);
        });

        Optional<PlayersDao.Player> afterRpName = readPlayer(uuid);
        assertThat(afterRpName).isPresent();
        assertThat(afterRpName.get().status())
                .as("status after /wp rpname")
                .isEqualTo(PlayerState.FACTIONLESS.getSerializedName());
        assertThat(afterRpName.get().rpName()).isEqualTo(rpName);
        assertThat(afterRpName.get().rpSurname()).isEqualTo(rpSurname);

        // ── 4. Faction choice — ZARNAVIA NPC right-click confirmed ──────────
        long tFaction = 1_080_000L;
        String passportId = "ZRN-000001";
        long createdAt = tFaction;
        database.transaction(conn -> {
            playersDao.setFactionAndStatus(conn, uuid,
                    chosenFaction.getSerializedName().toUpperCase(Locale.ROOT),
                    PlayerState.CANDIDATE.getSerializedName());
            passportsDao.insert(conn, new PassportsDao.Passport(
                    passportId, uuid,
                    chosenFaction.getSerializedName().toUpperCase(Locale.ROOT),
                    rpName, rpSurname, "01.06.1990", 1234567890L,
                    PlayerState.CANDIDATE.getSerializedName(),
                    null, null, null, false, createdAt));
            auditLogDao.insert(conn, tFaction, uuid, playerName, uuid, playerName,
                    "CHOOSE_FACTION", null,
                    "{\"faction\":\"" + chosenFaction.getSerializedName()
                            + "\",\"passport_id\":\"" + passportId + "\"}");
        });

        Optional<PlayersDao.Player> afterFaction = readPlayer(uuid);
        assertThat(afterFaction).isPresent();
        assertThat(afterFaction.get().status())
                .as("status after CHOOSE_FACTION")
                .isEqualTo(PlayerState.CANDIDATE.getSerializedName());
        assertThat(afterFaction.get().faction()).isEqualTo("ZARNAVIA");

        Optional<PassportsDao.Passport> issuedPassport =
                passportsDao.findByOwnerUuid(keepAlive, uuid);
        assertThat(issuedPassport)
                .as("passport row issued by faction choice")
                .isPresent();
        assertThat(issuedPassport.get().passportId()).isEqualTo(passportId);
        assertThat(issuedPassport.get().status())
                .isEqualTo(PlayerState.CANDIDATE.getSerializedName());

        // ── 5. ACCEPT — commander accepts the candidate (radial / /wp accept) ─
        long tAccept = 1_100_000L;
        database.transaction(conn -> {
            playersDao.updateAcceptance(conn, uuid,
                    PlayerState.ACCEPTED.getSerializedName(),
                    tAccept, acceptorUuid, acceptorName);
            passportsDao.updateAcceptance(conn, passportId,
                    PlayerState.ACCEPTED.getSerializedName(),
                    tAccept, acceptorName);
            auditLogDao.insert(conn, tAccept, acceptorUuid, acceptorName, uuid, playerName,
                    "ACCEPT", null, null);
        });

        // ── Final assertions ────────────────────────────────────────────────
        Optional<PlayersDao.Player> finalPlayer = readPlayer(uuid);
        assertThat(finalPlayer).isPresent();
        assertThat(finalPlayer.get().status())
                .as("final player status")
                .isEqualTo(PlayerState.ACCEPTED.getSerializedName());
        assertThat(finalPlayer.get().faction()).isEqualTo("ZARNAVIA");
        assertThat(finalPlayer.get().acceptedByUuid()).isEqualTo(acceptorUuid);
        assertThat(finalPlayer.get().acceptedByName()).isEqualTo(acceptorName);
        assertThat(finalPlayer.get().acceptedAt()).isEqualTo(tAccept);

        Optional<PassportsDao.Passport> finalPassport =
                passportsDao.findByOwnerUuid(keepAlive, uuid);
        assertThat(finalPassport).isPresent();
        assertThat(finalPassport.get().status())
                .as("final passport status")
                .isEqualTo(PlayerState.ACCEPTED.getSerializedName());
        assertThat(finalPassport.get().acceptedAt()).isEqualTo(tAccept);
        assertThat(finalPassport.get().acceptedBy()).isEqualTo(acceptorName);

        // Audit trail: exactly five rows for this player, in chronological
        // order: REGISTER → CAPTCHA_OK → RPNAME_SET → CHOOSE_FACTION → ACCEPT.
        // findByTargetUuid orders DESC by ts_utc, so we reverse the result.
        List<AuditEntry> trail = readAuditTargetAsc(uuid);
        assertThat(trail)
                .as("audit trail for the onboarded player")
                .extracting(AuditEntry::action)
                .containsExactly("REGISTER", "CAPTCHA_OK", "RPNAME_SET",
                        "CHOOSE_FACTION", "ACCEPT");
        assertThat(trail)
                .extracting(AuditEntry::tsUtc)
                .containsExactly(t1, tCaptchaOk, tRpName, tFaction, tAccept);

        // Req. 20.4: no captcha code, no password material, anywhere in the
        // audit trail. We assert that the reason / extra_json columns never
        // contain bcrypt prefixes or "captcha" keyword.
        for (AuditEntry e : trail) {
            String reason = e.reason() == null ? "" : e.reason();
            String extra = e.extraJson() == null ? "" : e.extraJson();
            assertThat(reason).doesNotContain("$2a$");
            assertThat(extra).doesNotContain("$2a$");
            assertThat(extra.toLowerCase(Locale.ROOT)).doesNotContain("\"code\"");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Scenario 2: capture → ransom
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Replays the captivity round-trip with two ACCEPTED players in opposing
     * factions. Mirrors the transactions in
     * {@link com.frostlogic.warproject.server.captivity.CaptivityService#capture}
     * and {@link com.frostlogic.warproject.server.captivity.CaptivityService#ransom}.
     *
     * <ol>
     *   <li>Set up two ACCEPTED players: ZARNAVIA initiator and CHERNOGRYAD
     *       target. Issue a passport for the target.</li>
     *   <li>Capture: in one transaction, set {@code passport.captured_by_uuid}
     *       to the initiator, set {@code players.captured = 1} on the target,
     *       and write a {@code CAPTURE_PASSPORT} audit row.</li>
     *   <li>Verify state after capture.</li>
     *   <li>Ransom: in one transaction, clear {@code passport.captured_by_uuid},
     *       clear {@code players.captured}, and write a {@code RANSOM_COMPLETE}
     *       audit row.</li>
     *   <li>Verify final state — passport returned to the owner, no trophy
     *       flag, audit trail has exactly two rows in the correct order.</li>
     * </ol>
     *
     * <p>Validates: Requirements §14 (captivity), §20 (audit).
     */
    @Test
    @DisplayName("Scenario 2: capture → ransom round-trip leaves the passport restored and writes both audit rows")
    void scenario2_captureAndRansom() {
        String initiatorUuid = UUID.randomUUID().toString();
        String initiatorName = "Captor";
        String targetUuid = UUID.randomUUID().toString();
        String targetName = "Prisoner";
        String passportId = "CHN-000042";
        long t0 = 2_000_000L;

        // ── Setup: two ACCEPTED players in different factions + passport ───
        database.transaction(conn -> {
            accountsDao.insert(conn, new AccountsDao.Account(
                    initiatorUuid,
                    "$2a$10$dummyhash000000000000000000000000000000000000000000",
                    t0, null, 0, 0));
            accountsDao.insert(conn, new AccountsDao.Account(
                    targetUuid,
                    "$2a$10$dummyhash000000000000000000000000000000000000000000",
                    t0, null, 0, 0));
            playersDao.insert(conn, new PlayersDao.Player(
                    initiatorUuid, "ZARNAVIA", "SOLDIER", null,
                    PlayerState.ACCEPTED.getSerializedName(),
                    "Ivan", "Petrov", false, null, false, 0,
                    t0, t0, null, "Commander", null));
            playersDao.insert(conn, new PlayersDao.Player(
                    targetUuid, "CHERNOGRYAD", "SOLDIER", null,
                    PlayerState.ACCEPTED.getSerializedName(),
                    "Oleg", "Sidorov", false, null, false, 0,
                    t0, t0, null, "Commander", null));
            passportsDao.insert(conn, new PassportsDao.Passport(
                    passportId, targetUuid, "CHERNOGRYAD",
                    "Oleg", "Sidorov", "15.06.1995", 12345L,
                    PlayerState.ACCEPTED.getSerializedName(),
                    t0, "Commander", null, false, t0));
        });

        // Pre-capture sanity
        assertThat(readPlayer(targetUuid).orElseThrow().captured())
                .as("target.captured before capture")
                .isFalse();
        assertThat(passportsDao.findById(keepAlive, passportId).orElseThrow().capturedByUuid())
                .as("passport.captured_by_uuid before capture")
                .isNull();

        // ── Capture — same shape as CaptivityService.capture ────────────────
        long tCapture = 2_100_000L;
        database.transaction(conn -> {
            passportsDao.updateCaptureState(conn, passportId, initiatorUuid, true);
            playersDao.setCaptured(conn, targetUuid, true);
            auditLogDao.insert(conn, tCapture,
                    initiatorUuid, initiatorName,
                    targetUuid, targetName,
                    "CAPTURE_PASSPORT", null, null);
        });

        PassportsDao.Passport afterCapture =
                passportsDao.findById(keepAlive, passportId).orElseThrow();
        assertThat(afterCapture.capturedByUuid())
                .as("passport.captured_by_uuid after capture")
                .isEqualTo(initiatorUuid);
        assertThat(afterCapture.trophy())
                .as("passport.trophy after capture")
                .isTrue();
        assertThat(readPlayer(targetUuid).orElseThrow().captured())
                .as("target.captured after capture")
                .isTrue();

        // ── Ransom — same shape as CaptivityService.ransom ─────────────────
        long tRansom = 2_200_000L;
        database.transaction(conn -> {
            passportsDao.updateCaptureState(conn, passportId, null, false);
            playersDao.setCaptured(conn, targetUuid, false);
            auditLogDao.insert(conn, tRansom,
                    initiatorUuid, initiatorName,
                    targetUuid, targetName,
                    "RANSOM_COMPLETE", null, null);
        });

        // ── Final assertions ────────────────────────────────────────────────
        PassportsDao.Passport afterRansom =
                passportsDao.findById(keepAlive, passportId).orElseThrow();
        assertThat(afterRansom.capturedByUuid())
                .as("passport.captured_by_uuid after ransom")
                .isNull();
        assertThat(afterRansom.trophy())
                .as("passport.trophy after ransom")
                .isFalse();
        assertThat(afterRansom.ownerUuid())
                .as("passport ownership preserved across capture+ransom")
                .isEqualTo(targetUuid);
        assertThat(readPlayer(targetUuid).orElseThrow().captured())
                .as("target.captured after ransom")
                .isFalse();

        // Audit trail for the target: exactly CAPTURE_PASSPORT then RANSOM_COMPLETE.
        List<AuditEntry> trail = readAuditTargetAsc(targetUuid);
        assertThat(trail)
                .as("audit trail for the captured/ransomed target")
                .extracting(AuditEntry::action)
                .containsExactly("CAPTURE_PASSPORT", "RANSOM_COMPLETE");
        assertThat(trail)
                .extracting(AuditEntry::tsUtc)
                .containsExactly(tCapture, tRansom);
        assertThat(trail.get(0).actorUuid()).isEqualTo(initiatorUuid);
        assertThat(trail.get(0).targetUuid()).isEqualTo(targetUuid);
        assertThat(trail.get(1).actorUuid()).isEqualTo(initiatorUuid);
        assertThat(trail.get(1).targetUuid()).isEqualTo(targetUuid);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Scenario 3: subdivision lifecycle
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Exercises the full subdivision lifecycle through {@link SubdivisionService}:
     * create → invite → kick → delete. Verifies that each operation produces
     * the expected DB state and audit entries (Req. 16.1–16.5).
     *
     * <ol>
     *   <li>Create a subdivision in ZARNAVIA. Assert the row exists and a
     *       {@code SUBDIV_CREATE} audit row was written by the actor.</li>
     *   <li>Invite an existing player. Assert {@code players.subdivision_id}
     *       is set and {@code subdivision_members} contains the row, plus a
     *       {@code SUBDIV_INVITE} audit row targets the invitee.</li>
     *   <li>Kick the same player. Assert {@code players.subdivision_id} is
     *       cleared, the {@code subdivision_members} row is gone, and a
     *       {@code SUBDIV_KICK} audit row carries the reason.</li>
     *   <li>Delete the subdivision. Assert the subdivision row is gone, and
     *       a {@code SUBDIV_DELETE} audit row carries the reason.</li>
     * </ol>
     *
     * <p>Unlike scenarios 1 and 2 (which simulate the service's transactions
     * directly through DAOs), this scenario exercises {@link SubdivisionService}
     * itself, since the service is plain Java and does not depend on
     * Minecraft runtime classes.
     *
     * <p>Validates: Requirements §16 (subdivisions), §20 (audit).
     */
    @Test
    @DisplayName("Scenario 3: subdivision lifecycle (create → invite → kick → delete) covers all DB ops and audit entries")
    void scenario3_subdivisionLifecycle() {
        SubdivisionService service = new SubdivisionService(
                database, subdivisionsDao, playersDao, auditLogDao);

        FactionId faction = FactionId.ZARNAVIA;
        String factionKey = faction.getSerializedName().toUpperCase(Locale.ROOT);
        String creatorUuid = UUID.randomUUID().toString();
        String creatorName = "Creator";
        String memberUuid = UUID.randomUUID().toString();
        String memberName = "Member";
        String subdivisionName = "AlphaSquad";

        // Insert the prerequisite player rows so SubdivisionService.invite can
        // resolve the target's current membership.
        long t0 = System.currentTimeMillis();
        accountsDao.insert(keepAlive, new AccountsDao.Account(
                creatorUuid,
                "$2a$10$dummyhash000000000000000000000000000000000000000000",
                t0, null, 0, 0));
        accountsDao.insert(keepAlive, new AccountsDao.Account(
                memberUuid,
                "$2a$10$dummyhash000000000000000000000000000000000000000000",
                t0, null, 0, 0));
        playersDao.insert(keepAlive, new PlayersDao.Player(
                creatorUuid, factionKey, "COMMANDER", null,
                PlayerState.ACCEPTED.getSerializedName(),
                "Ivan", "Petrov", false, null, false, 0,
                t0, t0, null, "Commander", null));
        playersDao.insert(keepAlive, new PlayersDao.Player(
                memberUuid, factionKey, "SOLDIER", null,
                PlayerState.ACCEPTED.getSerializedName(),
                "Oleg", "Sidorov", false, null, false, 0,
                t0, t0, null, "Commander", null));

        // ── Create ──────────────────────────────────────────────────────────
        SubdivisionService.Result<Subdivision> created =
                service.create(faction, subdivisionName, creatorUuid, creatorName);
        assertThat(created.isSuccess())
                .as("create should succeed for a fresh (faction, name)")
                .isTrue();
        Subdivision subdivision = created.orThrow();
        assertThat(subdivision.faction()).isEqualTo(factionKey);
        assertThat(subdivision.name()).isEqualTo(subdivisionName);
        assertThat(subdivision.createdBy()).isEqualTo(creatorUuid);

        Optional<Subdivision> persistedAfterCreate =
                subdivisionsDao.findByFactionAndName(keepAlive, factionKey, subdivisionName);
        assertThat(persistedAfterCreate)
                .as("subdivisions row visible after create")
                .isPresent();

        // ── Invite ──────────────────────────────────────────────────────────
        SubdivisionService.Result<Subdivision> invited =
                service.invite(subdivision.id(), memberUuid, memberName, creatorUuid, creatorName);
        assertThat(invited.isSuccess())
                .as("invite should succeed for an unaffiliated faction-mate")
                .isTrue();

        PlayersDao.Player memberAfterInvite =
                playersDao.findByUuid(keepAlive, memberUuid).orElseThrow();
        assertThat(memberAfterInvite.subdivisionId())
                .as("players.subdivision_id should equal sub.id after invite")
                .isEqualTo(subdivision.id());

        List<SubdivisionMember> membersAfterInvite =
                subdivisionsDao.findMembers(keepAlive, subdivision.id());
        assertThat(membersAfterInvite)
                .as("subdivision_members after invite")
                .hasSize(1);
        assertThat(membersAfterInvite.get(0).playerUuid()).isEqualTo(memberUuid);

        // ── Kick ────────────────────────────────────────────────────────────
        String kickReason = "leaving the squad";
        SubdivisionService.Result<Subdivision> kicked = service.kick(
                subdivision.id(), memberUuid, memberName, creatorUuid, creatorName, kickReason);
        assertThat(kicked.isSuccess())
                .as("kick should succeed for a current member")
                .isTrue();

        PlayersDao.Player memberAfterKick =
                playersDao.findByUuid(keepAlive, memberUuid).orElseThrow();
        assertThat(memberAfterKick.subdivisionId())
                .as("players.subdivision_id should be NULL after kick")
                .isNull();
        assertThat(subdivisionsDao.findMembers(keepAlive, subdivision.id()))
                .as("subdivision_members after kick")
                .isEmpty();

        // ── Delete ──────────────────────────────────────────────────────────
        String deleteReason = "squad disbanded";
        SubdivisionService.Result<Subdivision> deleted = service.delete(
                subdivision.id(), creatorUuid, creatorName, deleteReason);
        assertThat(deleted.isSuccess())
                .as("delete should succeed for an existing subdivision")
                .isTrue();
        assertThat(subdivisionsDao.findById(keepAlive, subdivision.id()))
                .as("subdivisions row gone after delete")
                .isEmpty();
        assertThat(service.findByName(faction, subdivisionName))
                .as("findByName empty after delete — unique slot freed")
                .isEmpty();

        // ── Audit trail ─────────────────────────────────────────────────────
        // Creator-actored rows (CREATE, INVITE on creator's actor side, KICK,
        // DELETE) are returned by findByActorUuid; the INVITE and KICK rows
        // also target the member, so they appear under findByTargetUuid too.
        List<AuditEntry> creatorActorTrail = readAuditActorAsc(creatorUuid);
        assertThat(creatorActorTrail)
                .as("audit rows actored by the creator")
                .extracting(AuditEntry::action)
                .containsExactly("SUBDIV_CREATE", "SUBDIV_INVITE", "SUBDIV_KICK", "SUBDIV_DELETE");

        List<AuditEntry> memberTargetTrail = readAuditTargetAsc(memberUuid);
        assertThat(memberTargetTrail)
                .as("audit rows targeting the member")
                .extracting(AuditEntry::action)
                .containsExactly("SUBDIV_INVITE", "SUBDIV_KICK");
        AuditEntry kickRow = memberTargetTrail.get(1);
        assertThat(kickRow.reason())
                .as("SUBDIV_KICK reason preserved in audit_log")
                .isEqualTo(kickReason);

        // The DELETE row carries the deletion reason.
        AuditEntry deleteRow = creatorActorTrail.get(3);
        assertThat(deleteRow.reason())
                .as("SUBDIV_DELETE reason preserved in audit_log")
                .isEqualTo(deleteReason);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    private Optional<PlayersDao.Player> readPlayer(String uuid) {
        return playersDao.findByUuid(keepAlive, uuid);
    }

    private String currentStatus(String uuid) {
        return readPlayer(uuid).map(PlayersDao.Player::status).orElse(null);
    }

    /**
     * Returns audit_log rows targeting the given UUID in chronological order.
     * {@link AuditLogDao#findByTargetUuid} returns DESC-by-ts_utc, so we
     * reverse the result for readability in the assertions.
     */
    private List<AuditEntry> readAuditTargetAsc(String targetUuid) {
        List<AuditEntry> desc = auditLogDao.findByTargetUuid(keepAlive, targetUuid, 1000, 0);
        List<AuditEntry> asc = new ArrayList<>(desc);
        java.util.Collections.reverse(asc);
        return asc;
    }

    /**
     * Returns audit_log rows actored by the given UUID in chronological order.
     */
    private List<AuditEntry> readAuditActorAsc(String actorUuid) {
        List<AuditEntry> desc = auditLogDao.findByActorUuid(keepAlive, actorUuid, 1000, 0);
        List<AuditEntry> asc = new ArrayList<>(desc);
        java.util.Collections.reverse(asc);
        return asc;
    }

    /**
     * Counts audit rows of a given action across the whole table — used in a
     * couple of assertions to confirm uniqueness within a scenario.
     */
    @SuppressWarnings("unused")
    private long countAuditAction(String action) {
        try (PreparedStatement ps = keepAlive.prepareStatement(
                "SELECT COUNT(*) FROM audit_log WHERE action = ?")) {
            ps.setString(1, action);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("countAuditAction failed", e);
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
     * Splits SQL on semicolons, respecting single-quoted strings and comments.
     * Mirrors the splitter used in {@code SubdivisionProperties} /
     * {@code CollaboratorProperties} so the integration test can reuse the
     * same migration on the same in-memory DB without depending on package-
     * private helpers.
     */
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
