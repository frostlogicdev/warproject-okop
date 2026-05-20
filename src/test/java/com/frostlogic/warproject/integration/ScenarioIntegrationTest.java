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

    @Test
    @DisplayName("Scenario 1: registration → CAPTCHA → RPNAME → CANDIDATE → ACCEPTED produces a complete audit trail")
    void scenario1_fullOnboardingFlow() {
        String uuid = UUID.randomUUID().toString();
        String playerName = "PlayerOne";
        String acceptorUuid = UUID.randomUUID().toString();
        String acceptorName = "Commander";
        FactionId chosenFaction = FactionId.ZARNAVIA;

        long t0 = 1_000_000L;
        long t1 = 1_010_000L;
        database.transaction(conn -> {
            accountsDao.insert(conn, new AccountsDao.Account(
                    uuid,
                    "$2a$10$dummyhash000000000000000000000000000000000000000000",
                    t0, null, 0, 0));
            playersDao.insert(conn, new PlayersDao.Player(
                    uuid, null, "CANDIDATE", null,
                    PlayerState.NEW.getSerializedName(),
                    null, null, false, null, false, 0,
                    t0, null, null, null, null));
            playersDao.updateStatus(conn, uuid, PlayerState.REGISTERED_PENDING.getSerializedName());
            auditLogDao.insert(conn, t1, uuid, playerName, uuid, playerName,
                    "REGISTER", null, null);
        });

        assertThat(currentStatus(uuid)).isEqualTo(PlayerState.REGISTERED_PENDING.getSerializedName());

        long tCaptchaOk = 1_040_000L;
        database.transaction(conn ->
                playersDao.updateStatus(conn, uuid, PlayerState.CAPTCHA.getSerializedName()));
        assertThat(currentStatus(uuid)).isEqualTo(PlayerState.CAPTCHA.getSerializedName());

        database.transaction(conn -> {
            playersDao.updateStatus(conn, uuid, PlayerState.RPNAME_REQUIRED.getSerializedName());
            auditLogDao.insert(conn, tCaptchaOk, uuid, playerName, uuid, playerName,
                    "CAPTCHA_OK", null, null);
        });
        assertThat(currentStatus(uuid)).isEqualTo(PlayerState.RPNAME_REQUIRED.getSerializedName());

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
                .isEqualTo(PlayerState.FACTIONLESS.getSerializedName());
        assertThat(afterRpName.get().rpName()).isEqualTo(rpName);
        assertThat(afterRpName.get().rpSurname()).isEqualTo(rpSurname);

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
                    null, null, null, null, false, createdAt));
            auditLogDao.insert(conn, tFaction, uuid, playerName, uuid, playerName,
                    "CHOOSE_FACTION", null,
                    "{\"faction\":\"" + chosenFaction.getSerializedName()
                            + "\",\"passport_id\":\"" + passportId + "\"}");
        });

        Optional<PlayersDao.Player> afterFaction = readPlayer(uuid);
        assertThat(afterFaction).isPresent();
        assertThat(afterFaction.get().status())
                .isEqualTo(PlayerState.CANDIDATE.getSerializedName());
        assertThat(afterFaction.get().faction()).isEqualTo("ZARNAVIA");

        Optional<PassportsDao.Passport> issuedPassport =
                passportsDao.findByOwnerUuid(keepAlive, uuid);
        assertThat(issuedPassport).isPresent();
        assertThat(issuedPassport.get().passportId()).isEqualTo(passportId);
        assertThat(issuedPassport.get().status())
                .isEqualTo(PlayerState.CANDIDATE.getSerializedName());

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

        Optional<PlayersDao.Player> finalPlayer = readPlayer(uuid);
        assertThat(finalPlayer).isPresent();
        assertThat(finalPlayer.get().status())
                .isEqualTo(PlayerState.ACCEPTED.getSerializedName());
        assertThat(finalPlayer.get().faction()).isEqualTo("ZARNAVIA");
        assertThat(finalPlayer.get().acceptedByUuid()).isEqualTo(acceptorUuid);
        assertThat(finalPlayer.get().acceptedByName()).isEqualTo(acceptorName);
        assertThat(finalPlayer.get().acceptedAt()).isEqualTo(tAccept);

        Optional<PassportsDao.Passport> finalPassport =
                passportsDao.findByOwnerUuid(keepAlive, uuid);
        assertThat(finalPassport).isPresent();
        assertThat(finalPassport.get().status())
                .isEqualTo(PlayerState.ACCEPTED.getSerializedName());
        assertThat(finalPassport.get().acceptedAt()).isEqualTo(tAccept);
        assertThat(finalPassport.get().acceptedBy()).isEqualTo(acceptorName);

        List<AuditEntry> trail = readAuditTargetAsc(uuid);
        assertThat(trail)
                .extracting(AuditEntry::action)
                .containsExactly("REGISTER", "CAPTCHA_OK", "RPNAME_SET",
                        "CHOOSE_FACTION", "ACCEPT");
        assertThat(trail)
                .extracting(AuditEntry::tsUtc)
                .containsExactly(t1, tCaptchaOk, tRpName, tFaction, tAccept);

        for (AuditEntry e : trail) {
            String reason = e.reason() == null ? "" : e.reason();
            String extra = e.extraJson() == null ? "" : e.extraJson();
            assertThat(reason).doesNotContain("$2a$");
            assertThat(extra).doesNotContain("$2a$");
            assertThat(extra.toLowerCase(Locale.ROOT)).doesNotContain("\"code\"");
        }
    }

    @Test
    @DisplayName("Scenario 2: capture → ransom round-trip leaves the passport restored and writes both audit rows")
    void scenario2_captureAndRansom() {
        String initiatorUuid = UUID.randomUUID().toString();
        String initiatorName = "Captor";
        String targetUuid = UUID.randomUUID().toString();
        String targetName = "Prisoner";
        String passportId = "CHN-000042";
        long t0 = 2_000_000L;

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
                    t0, "Commander", null, null, false, t0));
        });

        assertThat(readPlayer(targetUuid).orElseThrow().captured()).isFalse();
        assertThat(passportsDao.findById(keepAlive, passportId).orElseThrow().capturedByUuid())
                .isNull();

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
        assertThat(afterCapture.capturedByUuid()).isEqualTo(initiatorUuid);
        assertThat(afterCapture.trophy()).isTrue();
        assertThat(readPlayer(targetUuid).orElseThrow().captured()).isTrue();

        long tRansom = 2_200_000L;
        database.transaction(conn -> {
            passportsDao.updateCaptureState(conn, passportId, null, false);
            playersDao.setCaptured(conn, targetUuid, false);
            auditLogDao.insert(conn, tRansom,
                    initiatorUuid, initiatorName,
                    targetUuid, targetName,
                    "RANSOM_COMPLETE", null, null);
        });

        PassportsDao.Passport afterRansom =
                passportsDao.findById(keepAlive, passportId).orElseThrow();
        assertThat(afterRansom.capturedByUuid()).isNull();
        assertThat(afterRansom.trophy()).isFalse();
        assertThat(afterRansom.ownerUuid()).isEqualTo(targetUuid);
        assertThat(readPlayer(targetUuid).orElseThrow().captured()).isFalse();

        List<AuditEntry> trail = readAuditTargetAsc(targetUuid);
        assertThat(trail)
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

        SubdivisionService.Result<Subdivision> created =
                service.create(faction, subdivisionName, creatorUuid, creatorName);
        assertThat(created.isSuccess()).isTrue();
        Subdivision subdivision = created.orThrow();
        assertThat(subdivision.faction()).isEqualTo(factionKey);
        assertThat(subdivision.name()).isEqualTo(subdivisionName);
        assertThat(subdivision.createdBy()).isEqualTo(creatorUuid);

        Optional<Subdivision> persistedAfterCreate =
                subdivisionsDao.findByFactionAndName(keepAlive, factionKey, subdivisionName);
        assertThat(persistedAfterCreate).isPresent();

        SubdivisionService.Result<Subdivision> invited =
                service.invite(subdivision.id(), memberUuid, memberName, creatorUuid, creatorName);
        assertThat(invited.isSuccess()).isTrue();

        PlayersDao.Player memberAfterInvite =
                playersDao.findByUuid(keepAlive, memberUuid).orElseThrow();
        assertThat(memberAfterInvite.subdivisionId()).isEqualTo(subdivision.id());

        List<SubdivisionMember> membersAfterInvite =
                subdivisionsDao.findMembers(keepAlive, subdivision.id());
        assertThat(membersAfterInvite).hasSize(1);
        assertThat(membersAfterInvite.get(0).playerUuid()).isEqualTo(memberUuid);

        String kickReason = "leaving the squad";
        SubdivisionService.Result<Subdivision> kicked = service.kick(
                subdivision.id(), memberUuid, memberName, creatorUuid, creatorName, kickReason);
        assertThat(kicked.isSuccess()).isTrue();

        PlayersDao.Player memberAfterKick =
                playersDao.findByUuid(keepAlive, memberUuid).orElseThrow();
        assertThat(memberAfterKick.subdivisionId()).isNull();
        assertThat(subdivisionsDao.findMembers(keepAlive, subdivision.id())).isEmpty();

        String deleteReason = "squad disbanded";
        SubdivisionService.Result<Subdivision> deleted = service.delete(
                subdivision.id(), creatorUuid, creatorName, deleteReason);
        assertThat(deleted.isSuccess()).isTrue();
        assertThat(subdivisionsDao.findById(keepAlive, subdivision.id())).isEmpty();
        assertThat(service.findByName(faction, subdivisionName)).isEmpty();

        List<AuditEntry> creatorActorTrail = readAuditActorAsc(creatorUuid);
        assertThat(creatorActorTrail)
                .extracting(AuditEntry::action)
                .containsExactly("SUBDIV_CREATE", "SUBDIV_INVITE", "SUBDIV_KICK", "SUBDIV_DELETE");

        List<AuditEntry> memberTargetTrail = readAuditTargetAsc(memberUuid);
        assertThat(memberTargetTrail)
                .extracting(AuditEntry::action)
                .containsExactly("SUBDIV_INVITE", "SUBDIV_KICK");
        AuditEntry kickRow = memberTargetTrail.get(1);
        assertThat(kickRow.reason()).isEqualTo(kickReason);

        AuditEntry deleteRow = creatorActorTrail.get(3);
        assertThat(deleteRow.reason()).isEqualTo(deleteReason);
    }

    private Optional<PlayersDao.Player> readPlayer(String uuid) {
        return playersDao.findByUuid(keepAlive, uuid);
    }

    private String currentStatus(String uuid) {
        return readPlayer(uuid).map(PlayersDao.Player::status).orElse(null);
    }

    private List<AuditEntry> readAuditTargetAsc(String targetUuid) {
        List<AuditEntry> desc = auditLogDao.findByTargetUuid(keepAlive, targetUuid, 1000, 0);
        List<AuditEntry> asc = new ArrayList<>(desc);
        java.util.Collections.reverse(asc);
        return asc;
    }

    private List<AuditEntry> readAuditActorAsc(String actorUuid) {
        List<AuditEntry> desc = auditLogDao.findByActorUuid(keepAlive, actorUuid, 1000, 0);
        List<AuditEntry> asc = new ArrayList<>(desc);
        java.util.Collections.reverse(asc);
        return asc;
    }

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
