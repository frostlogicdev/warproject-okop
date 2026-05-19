package com.frostlogic.warproject.server.diplomacy;

import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.attachment.Role;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import com.frostlogic.warproject.persistence.Database;
import com.frostlogic.warproject.persistence.dao.AuditLogDao;
import com.frostlogic.warproject.persistence.dao.PassportsDao;
import com.frostlogic.warproject.persistence.dao.TrucesDao;
import com.frostlogic.warproject.server.captivity.CaptivityService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service managing truces, prisoner exchanges, and the negotiation channel
 * between factions.
 * <p>
 * All public methods follow the {@link Result} sealed interface pattern for
 * error handling. In-memory state (active truce, pending proposals) is
 * maintained as volatile fields and checked/expired via {@link #tickTruce}.
 * <p>
 * Requirements: 10.1–10.10, 11.1–11.7, 12.1–12.7
 */
public final class DiplomacyService {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Minimum truce duration in minutes. */
    private static final int MIN_TRUCE_DURATION_MINUTES = 5;
    /** Maximum truce duration in minutes. */
    private static final int MAX_TRUCE_DURATION_MINUTES = 120;
    /** Truce proposal timeout in milliseconds (60 seconds). */
    private static final long TRUCE_PROPOSAL_TIMEOUT_MS = 60_000L;
    /** Exchange proposal timeout in milliseconds (300 seconds). */
    private static final long EXCHANGE_PROPOSAL_TIMEOUT_MS = 300_000L;

    private final Database database;
    private final TrucesDao trucesDao;
    private final CaptivityService captivityService;
    private final PassportsDao passportsDao;
    private final AuditLogDao auditLogDao;

    // In-memory state
    private volatile @Nullable TruceState activeTruce;
    private volatile @Nullable TruceProposal pendingTruceProposal;
    private volatile @Nullable ExchangeProposal pendingExchange;

    public DiplomacyService(Database database, TrucesDao trucesDao,
                            CaptivityService captivityService, PassportsDao passportsDao,
                            AuditLogDao auditLogDao) {
        this.database = database;
        this.trucesDao = trucesDao;
        this.captivityService = captivityService;
        this.passportsDao = passportsDao;
        this.auditLogDao = auditLogDao;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Result type
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Outcome of a diplomacy operation. Sealed so callers can pattern-match.
     */
    public sealed interface Result<T> {

        default boolean isSuccess() {
            return this instanceof Success<T>;
        }

        @SuppressWarnings("unchecked")
        default T orThrow() {
            if (this instanceof Success<?> s) {
                return (T) s.value();
            }
            throw new IllegalStateException("DiplomacyService.Result was a Failure: "
                    + ((Failure<?>) this).errorKey());
        }

        static <T> Result<T> success(T value) {
            return new Success<>(value);
        }

        static <T> Result<T> failure(String errorKey) {
            return new Failure<>(errorKey);
        }

        record Success<T>(T value) implements Result<T> {}
        record Failure<T>(String errorKey) implements Result<T> {}
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Truce operations
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Proposes a truce to the opposing faction.
     * <p>
     * Validates:
     * <ul>
     *   <li>Duration is between 5 and 120 minutes</li>
     *   <li>No active truce exists</li>
     *   <li>No pending truce proposal exists</li>
     *   <li>The proposing player is a GENERAL</li>
     *   <li>At least one opposing general is online</li>
     * </ul>
     *
     * @param general         the proposing general
     * @param durationMinutes the proposed truce duration in minutes
     * @return success or failure with error key
     */
    public Result<Void> proposeTruce(ServerPlayer general, int durationMinutes) {
        // Validate duration
        if (durationMinutes < MIN_TRUCE_DURATION_MINUTES || durationMinutes > MAX_TRUCE_DURATION_MINUTES) {
            return Result.failure("wp.diplomacy.error.invalid_duration");
        }

        // Validate role
        Role role = general.getData(WpAttachmentTypes.ROLE.get());
        if (!role.atLeast(Role.GENERAL)) {
            return Result.failure("wp.diplomacy.error.insufficient_role");
        }

        // Validate no active truce
        if (activeTruce != null) {
            return Result.failure("wp.diplomacy.error.truce_already_active");
        }

        // Validate no pending proposal
        if (pendingTruceProposal != null) {
            return Result.failure("wp.diplomacy.error.proposal_already_pending");
        }

        // Get proposing faction
        Optional<FactionId> factionOpt = general.getData(WpAttachmentTypes.FACTION.get());
        if (factionOpt.isEmpty()) {
            return Result.failure("wp.diplomacy.error.no_faction");
        }
        FactionId proposingFaction = factionOpt.get();
        FactionId targetFaction = getOpposingFaction(proposingFaction);

        // Check opposing general is online
        MinecraftServer server = general.getServer();
        List<ServerPlayer> opposingGenerals = getOnlineGenerals(server, targetFaction);
        if (opposingGenerals.isEmpty()) {
            return Result.failure("wp.diplomacy.error.no_opposing_general_online");
        }

        // Create proposal
        TruceProposal proposal = new TruceProposal(
                proposingFaction, targetFaction, durationMinutes, System.currentTimeMillis()
        );
        this.pendingTruceProposal = proposal;

        // Audit log
        database.transaction(conn -> auditLogDao.insert(conn,
                System.currentTimeMillis(),
                general.getStringUUID(),
                general.getGameProfile().getName(),
                null, null,
                "TRUCE_PROPOSE",
                null,
                "{\"duration\":" + durationMinutes + "}"
        ));

        // Notify opposing generals
        Component notification = Component.literal(
                "Фракция " + proposingFaction.getSerializedName()
                        + " предлагает перемирие на " + durationMinutes + " минут. "
                        + "Используйте /wp diplomacy truce accept для принятия."
        );
        for (ServerPlayer opposingGeneral : opposingGenerals) {
            opposingGeneral.sendSystemMessage(notification);
        }

        LOGGER.info("Truce proposed by {} ({}) for {} minutes",
                general.getGameProfile().getName(), proposingFaction.getSerializedName(), durationMinutes);

        return Result.success(null);
    }

    /**
     * Accepts a pending truce proposal.
     * <p>
     * Validates:
     * <ul>
     *   <li>A pending proposal exists</li>
     *   <li>The accepting player is a GENERAL of the target faction</li>
     * </ul>
     *
     * @param general the accepting general
     * @return success or failure with error key
     */
    public Result<Void> acceptTruce(ServerPlayer general) {
        // Validate role
        Role role = general.getData(WpAttachmentTypes.ROLE.get());
        if (!role.atLeast(Role.GENERAL)) {
            return Result.failure("wp.diplomacy.error.insufficient_role");
        }

        // Validate pending proposal exists
        TruceProposal proposal = this.pendingTruceProposal;
        if (proposal == null) {
            return Result.failure("wp.diplomacy.error.no_pending_proposal");
        }

        // Validate accepting general is from the target faction
        Optional<FactionId> factionOpt = general.getData(WpAttachmentTypes.FACTION.get());
        if (factionOpt.isEmpty()) {
            return Result.failure("wp.diplomacy.error.no_faction");
        }
        FactionId acceptingFaction = factionOpt.get();
        if (acceptingFaction != proposal.targetFaction()) {
            return Result.failure("wp.diplomacy.error.wrong_faction");
        }

        // Activate truce
        long now = System.currentTimeMillis();
        int dbId = database.inTx(conn -> {
            TrucesDao.Truce truceRow = new TrucesDao.Truce(
                    0,
                    proposal.proposingFaction().getSerializedName(),
                    proposal.targetFaction().getSerializedName(),
                    proposal.durationMinutes(),
                    now,
                    null,
                    "ACTIVE"
            );
            int id = trucesDao.insert(conn, truceRow);

            auditLogDao.insert(conn, now,
                    general.getStringUUID(),
                    general.getGameProfile().getName(),
                    null, null,
                    "TRUCE_ACCEPT",
                    null,
                    "{\"duration\":" + proposal.durationMinutes() + "}"
            );
            return id;
        });

        TruceState truce = new TruceState(
                proposal.proposingFaction(),
                proposal.targetFaction(),
                proposal.durationMinutes(),
                now,
                dbId
        );
        this.activeTruce = truce;
        this.pendingTruceProposal = null;

        // Broadcast to all online players
        MinecraftServer server = general.getServer();
        Component broadcast = Component.literal(
                "Перемирие между фракциями установлено на " + proposal.durationMinutes() + " минут!"
        );
        broadcastToAll(server, broadcast);

        LOGGER.info("Truce accepted by {} ({}), duration {} minutes, dbId={}",
                general.getGameProfile().getName(), acceptingFaction.getSerializedName(),
                proposal.durationMinutes(), dbId);

        return Result.success(null);
    }

    /**
     * Breaks the currently active truce immediately.
     *
     * @param general the general breaking the truce
     * @return success or failure with error key
     */
    public Result<Void> breakTruce(ServerPlayer general) {
        // Validate role
        Role role = general.getData(WpAttachmentTypes.ROLE.get());
        if (!role.atLeast(Role.GENERAL)) {
            return Result.failure("wp.diplomacy.error.insufficient_role");
        }

        // Validate active truce exists
        TruceState truce = this.activeTruce;
        if (truce == null) {
            return Result.failure("wp.diplomacy.error.no_active_truce");
        }

        // Get breaking faction
        Optional<FactionId> factionOpt = general.getData(WpAttachmentTypes.FACTION.get());
        if (factionOpt.isEmpty()) {
            return Result.failure("wp.diplomacy.error.no_faction");
        }
        FactionId breakingFaction = factionOpt.get();

        // End truce in DB
        long now = System.currentTimeMillis();
        database.transaction(conn -> {
            trucesDao.updateStatus(conn, truce.dbId(), "BROKEN", now);
            auditLogDao.insert(conn, now,
                    general.getStringUUID(),
                    general.getGameProfile().getName(),
                    null, null,
                    "TRUCE_BREAK",
                    null,
                    "{\"faction\":\"" + breakingFaction.getSerializedName() + "\"}"
            );
        });

        this.activeTruce = null;

        // Broadcast
        MinecraftServer server = general.getServer();
        Component broadcast = Component.literal(
                "Перемирие нарушено фракцией " + breakingFaction.getSerializedName() + "!"
        );
        broadcastToAll(server, broadcast);

        LOGGER.info("Truce broken by {} ({})", general.getGameProfile().getName(),
                breakingFaction.getSerializedName());

        return Result.success(null);
    }

    /**
     * Returns {@code true} if a truce is currently active (not expired).
     */
    public boolean isTruceActive() {
        TruceState truce = this.activeTruce;
        return truce != null && !truce.isExpired();
    }

    /**
     * Called each server tick to check for proposal timeouts and truce expiration.
     * <p>
     * Handles:
     * <ul>
     *   <li>Truce proposal timeout (60s) — expires and notifies proposing faction</li>
     *   <li>Exchange proposal timeout (300s) — expires and notifies proposing faction</li>
     *   <li>Active truce expiration — deactivates and broadcasts</li>
     * </ul>
     *
     * @param server the Minecraft server instance
     */
    public void tickTruce(MinecraftServer server) {
        // Check truce proposal timeout
        TruceProposal proposal = this.pendingTruceProposal;
        if (proposal != null && proposal.isExpired()) {
            this.pendingTruceProposal = null;
            // Notify proposing faction generals
            List<ServerPlayer> proposingGenerals = getOnlineGenerals(server, proposal.proposingFaction());
            Component notification = Component.literal(
                    "Предложение перемирия истекло — не было принято в течение 60 секунд."
            );
            for (ServerPlayer gen : proposingGenerals) {
                gen.sendSystemMessage(notification);
            }
            LOGGER.debug("Truce proposal from {} expired", proposal.proposingFaction().getSerializedName());
        }

        // Check exchange proposal timeout
        ExchangeProposal exchange = this.pendingExchange;
        if (exchange != null && exchange.isExpired()) {
            this.pendingExchange = null;
            // Notify proposing faction generals
            List<ServerPlayer> proposingGenerals = getOnlineGenerals(server, exchange.proposingFaction());
            Component notification = Component.literal(
                    "Предложение обмена пленными истекло — не было принято в течение 300 секунд."
            );
            for (ServerPlayer gen : proposingGenerals) {
                gen.sendSystemMessage(notification);
            }

            // Audit log for exchange expiry
            database.transaction(conn -> auditLogDao.insert(conn,
                    System.currentTimeMillis(),
                    null, null, null, null,
                    "EXCHANGE_EXPIRE",
                    null,
                    "{\"own\":\"" + exchange.ownPrisoner() + "\",\"enemy\":\"" + exchange.enemyPrisoner() + "\"}"
            ));

            LOGGER.debug("Exchange proposal from {} expired", exchange.proposingFaction().getSerializedName());
        }

        // Check truce expiration
        TruceState truce = this.activeTruce;
        if (truce != null && truce.isExpired()) {
            this.activeTruce = null;

            // Update DB
            long now = System.currentTimeMillis();
            database.transaction(conn -> {
                trucesDao.updateStatus(conn, truce.dbId(), "EXPIRED", now);
                auditLogDao.insert(conn, now,
                        null, null, null, null,
                        "TRUCE_EXPIRE",
                        null, null
                );
            });

            // Broadcast
            Component broadcast = Component.literal("Перемирие завершено!");
            broadcastToAll(server, broadcast);

            LOGGER.info("Truce (dbId={}) expired after {} minutes", truce.dbId(), truce.durationMinutes());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Prisoner Exchange operations
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Proposes a prisoner exchange with the opposing faction.
     * <p>
     * Validates:
     * <ul>
     *   <li>The proposing player is a GENERAL</li>
     *   <li>No pending exchange already exists</li>
     *   <li>The proposing faction holds the enemy prisoner's captured passport</li>
     *   <li>The opposing faction holds the own prisoner's captured passport</li>
     * </ul>
     *
     * @param general       the proposing general
     * @param ownPrisoner   passport ID of the prisoner the proposing faction wants back
     * @param enemyPrisoner passport ID of the prisoner the proposing faction holds
     * @return success or failure with error key
     */
    public Result<Void> proposeExchange(ServerPlayer general, String ownPrisoner, String enemyPrisoner) {
        // Validate role
        Role role = general.getData(WpAttachmentTypes.ROLE.get());
        if (!role.atLeast(Role.GENERAL)) {
            return Result.failure("wp.diplomacy.error.insufficient_role");
        }

        // Validate no pending exchange
        if (pendingExchange != null) {
            return Result.failure("wp.diplomacy.error.exchange_already_pending");
        }

        // Get proposing faction
        Optional<FactionId> factionOpt = general.getData(WpAttachmentTypes.FACTION.get());
        if (factionOpt.isEmpty()) {
            return Result.failure("wp.diplomacy.error.no_faction");
        }
        FactionId proposingFaction = factionOpt.get();
        FactionId opposingFaction = getOpposingFaction(proposingFaction);

        // Validate prisoner ownership:
        // - The proposing faction must hold the enemy prisoner's passport (captured_by belongs to proposing faction member)
        // - The opposing faction must hold the own prisoner's passport (captured_by belongs to opposing faction member)
        Optional<PassportsDao.Passport> enemyPassportOpt = database.inTx(conn ->
                passportsDao.findById(conn, enemyPrisoner)
        );
        if (enemyPassportOpt.isEmpty()) {
            return Result.failure("wp.diplomacy.error.enemy_prisoner_not_found");
        }
        PassportsDao.Passport enemyPassport = enemyPassportOpt.get();
        if (enemyPassport.capturedByUuid() == null || !enemyPassport.trophy()) {
            return Result.failure("wp.diplomacy.error.enemy_prisoner_not_captured");
        }
        // Verify the enemy prisoner's passport belongs to the opposing faction (they are the prisoner)
        if (!opposingFaction.getSerializedName().equals(enemyPassport.faction())) {
            return Result.failure("wp.diplomacy.error.enemy_prisoner_wrong_faction");
        }

        Optional<PassportsDao.Passport> ownPassportOpt = database.inTx(conn ->
                passportsDao.findById(conn, ownPrisoner)
        );
        if (ownPassportOpt.isEmpty()) {
            return Result.failure("wp.diplomacy.error.own_prisoner_not_found");
        }
        PassportsDao.Passport ownPassport = ownPassportOpt.get();
        if (ownPassport.capturedByUuid() == null || !ownPassport.trophy()) {
            return Result.failure("wp.diplomacy.error.own_prisoner_not_captured");
        }
        // Verify the own prisoner's passport belongs to the proposing faction (they are the prisoner)
        if (!proposingFaction.getSerializedName().equals(ownPassport.faction())) {
            return Result.failure("wp.diplomacy.error.own_prisoner_wrong_faction");
        }

        // Create exchange proposal
        ExchangeProposal proposal = new ExchangeProposal(
                proposingFaction, ownPrisoner, enemyPrisoner, System.currentTimeMillis()
        );
        this.pendingExchange = proposal;

        // Audit log
        database.transaction(conn -> auditLogDao.insert(conn,
                System.currentTimeMillis(),
                general.getStringUUID(),
                general.getGameProfile().getName(),
                null, null,
                "EXCHANGE_PROPOSE",
                null,
                "{\"own\":\"" + ownPrisoner + "\",\"enemy\":\"" + enemyPrisoner + "\"}"
        ));

        // Notify opposing generals
        MinecraftServer server = general.getServer();
        List<ServerPlayer> opposingGenerals = getOnlineGenerals(server, opposingFaction);
        Component notification = Component.literal(
                "Предложение обмена пленными: " + ownPrisoner + " ↔ " + enemyPrisoner
                        + ". Используйте /wp diplomacy exchange accept или reject."
        );
        for (ServerPlayer opposingGeneral : opposingGenerals) {
            opposingGeneral.sendSystemMessage(notification);
        }

        LOGGER.info("Exchange proposed by {} ({}): own={}, enemy={}",
                general.getGameProfile().getName(), proposingFaction.getSerializedName(),
                ownPrisoner, enemyPrisoner);

        return Result.success(null);
    }

    /**
     * Accepts a pending prisoner exchange proposal.
     * <p>
     * Executes the exchange via CaptivityService, releasing both prisoners.
     *
     * @param general the accepting general
     * @return success or failure with error key
     */
    public Result<Void> acceptExchange(ServerPlayer general) {
        // Validate role
        Role role = general.getData(WpAttachmentTypes.ROLE.get());
        if (!role.atLeast(Role.GENERAL)) {
            return Result.failure("wp.diplomacy.error.insufficient_role");
        }

        // Validate pending exchange exists
        ExchangeProposal exchange = this.pendingExchange;
        if (exchange == null) {
            return Result.failure("wp.diplomacy.error.no_pending_exchange");
        }

        // Validate accepting general is from the opposing faction
        Optional<FactionId> factionOpt = general.getData(WpAttachmentTypes.FACTION.get());
        if (factionOpt.isEmpty()) {
            return Result.failure("wp.diplomacy.error.no_faction");
        }
        FactionId acceptingFaction = factionOpt.get();
        FactionId opposingFaction = getOpposingFaction(exchange.proposingFaction());
        if (acceptingFaction != opposingFaction) {
            return Result.failure("wp.diplomacy.error.wrong_faction");
        }

        // Re-validate that both prisoners are still captured
        Optional<PassportsDao.Passport> enemyPassportOpt = database.inTx(conn ->
                passportsDao.findById(conn, exchange.enemyPrisoner())
        );
        Optional<PassportsDao.Passport> ownPassportOpt = database.inTx(conn ->
                passportsDao.findById(conn, exchange.ownPrisoner())
        );

        if (enemyPassportOpt.isEmpty() || ownPassportOpt.isEmpty()) {
            this.pendingExchange = null;
            return Result.failure("wp.diplomacy.error.exchange_prisoner_released");
        }

        PassportsDao.Passport enemyPassport = enemyPassportOpt.get();
        PassportsDao.Passport ownPassport = ownPassportOpt.get();

        if (enemyPassport.capturedByUuid() == null || !enemyPassport.trophy()
                || ownPassport.capturedByUuid() == null || !ownPassport.trophy()) {
            this.pendingExchange = null;
            return Result.failure("wp.diplomacy.error.exchange_prisoner_released");
        }

        // Execute exchange via CaptivityService: release both prisoners
        // Release enemy prisoner (held by proposing faction) — return to original owner
        CaptivityService.RansomResult enemyResult = captivityService.ransom(
                exchange.enemyPrisoner(),
                enemyPassport.capturedByUuid(),
                enemyPassport.ownerUuid()
        );
        if (!(enemyResult instanceof CaptivityService.RansomResult.Success)) {
            this.pendingExchange = null;
            return Result.failure("wp.diplomacy.error.exchange_failed");
        }

        // Release own prisoner (held by opposing faction) — return to original owner
        CaptivityService.RansomResult ownResult = captivityService.ransom(
                exchange.ownPrisoner(),
                ownPassport.capturedByUuid(),
                ownPassport.ownerUuid()
        );
        if (!(ownResult instanceof CaptivityService.RansomResult.Success)) {
            // Partial failure — first prisoner already released
            LOGGER.warn("Exchange partially failed: enemy prisoner released but own prisoner release failed");
            this.pendingExchange = null;
            return Result.failure("wp.diplomacy.error.exchange_partial_failure");
        }

        this.pendingExchange = null;

        // Audit log
        database.transaction(conn -> auditLogDao.insert(conn,
                System.currentTimeMillis(),
                general.getStringUUID(),
                general.getGameProfile().getName(),
                null, null,
                "EXCHANGE_ACCEPT",
                null,
                "{\"own\":\"" + exchange.ownPrisoner() + "\",\"enemy\":\"" + exchange.enemyPrisoner() + "\"}"
        ));

        // Notify both factions' generals
        MinecraftServer server = general.getServer();
        Component notification = Component.literal(
                "Обмен пленными выполнен: " + exchange.ownPrisoner() + " ↔ " + exchange.enemyPrisoner()
        );
        List<ServerPlayer> allGenerals = getOnlineGenerals(server, exchange.proposingFaction());
        allGenerals.addAll(getOnlineGenerals(server, opposingFaction));
        for (ServerPlayer gen : allGenerals) {
            gen.sendSystemMessage(notification);
        }

        LOGGER.info("Exchange accepted by {} ({}): own={}, enemy={}",
                general.getGameProfile().getName(), acceptingFaction.getSerializedName(),
                exchange.ownPrisoner(), exchange.enemyPrisoner());

        return Result.success(null);
    }

    /**
     * Rejects a pending prisoner exchange proposal.
     *
     * @param general the rejecting general
     * @return success or failure with error key
     */
    public Result<Void> rejectExchange(ServerPlayer general) {
        // Validate role
        Role role = general.getData(WpAttachmentTypes.ROLE.get());
        if (!role.atLeast(Role.GENERAL)) {
            return Result.failure("wp.diplomacy.error.insufficient_role");
        }

        // Validate pending exchange exists
        ExchangeProposal exchange = this.pendingExchange;
        if (exchange == null) {
            return Result.failure("wp.diplomacy.error.no_pending_exchange");
        }

        // Validate rejecting general is from the target faction
        Optional<FactionId> factionOpt = general.getData(WpAttachmentTypes.FACTION.get());
        if (factionOpt.isEmpty()) {
            return Result.failure("wp.diplomacy.error.no_faction");
        }
        FactionId rejectingFaction = factionOpt.get();
        FactionId targetFaction = getOpposingFaction(exchange.proposingFaction());
        if (rejectingFaction != targetFaction) {
            return Result.failure("wp.diplomacy.error.wrong_faction");
        }

        this.pendingExchange = null;

        // Audit log
        database.transaction(conn -> auditLogDao.insert(conn,
                System.currentTimeMillis(),
                general.getStringUUID(),
                general.getGameProfile().getName(),
                null, null,
                "EXCHANGE_REJECT",
                null,
                "{\"own\":\"" + exchange.ownPrisoner() + "\",\"enemy\":\"" + exchange.enemyPrisoner() + "\"}"
        ));

        // Notify proposing faction generals
        MinecraftServer server = general.getServer();
        List<ServerPlayer> proposingGenerals = getOnlineGenerals(server, exchange.proposingFaction());
        Component notification = Component.literal(
                "Предложение обмена пленными отклонено."
        );
        for (ServerPlayer gen : proposingGenerals) {
            gen.sendSystemMessage(notification);
        }

        LOGGER.info("Exchange rejected by {} ({})", general.getGameProfile().getName(),
                rejectingFaction.getSerializedName());

        return Result.success(null);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Negotiation Channel
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Sends a diplomacy message to all eligible players (GENERAL or OP of both factions).
     * <p>
     * Validates:
     * <ul>
     *   <li>A truce is currently active</li>
     *   <li>The sender's role is GENERAL or OP</li>
     *   <li>The message is 1–200 characters after trimming</li>
     * </ul>
     *
     * @param sender  the player sending the message
     * @param message the message text
     * @return success or failure with error key
     */
    public Result<Void> sendDiplomacyMessage(ServerPlayer sender, String message) {
        // Validate truce is active
        if (!isTruceActive()) {
            return Result.failure("wp.diplomacy.error.channel_requires_truce");
        }

        // Validate role (GENERAL or OP)
        Role role = sender.getData(WpAttachmentTypes.ROLE.get());
        if (!role.atLeast(Role.GENERAL)) {
            return Result.failure("wp.diplomacy.error.insufficient_role");
        }

        // Validate message length
        String trimmed = message.trim();
        if (trimmed.isEmpty() || trimmed.length() > 200) {
            return Result.failure("wp.diplomacy.error.invalid_message_length");
        }

        // Audit log (no message body recorded per Req. 12.4)
        String senderUuid = sender.getStringUUID();
        String senderName = sender.getGameProfile().getName();
        database.transaction(conn -> auditLogDao.insert(conn,
                System.currentTimeMillis(),
                senderUuid,
                senderName,
                null, null,
                "DIPLOMACY_SEND",
                null,
                "{\"len\":" + trimmed.length() + "}"
        ));

        // Deliver to all eligible players (GENERAL or OP of both factions)
        MinecraftServer server = sender.getServer();
        List<ServerPlayer> recipients = getEligibleDiplomacyRecipients(server);

        Component formattedMessage = Component.literal("[Дипломатия] " + senderName + ": " + trimmed);
        for (ServerPlayer recipient : recipients) {
            recipient.sendSystemMessage(formattedMessage);
        }

        // If no other recipients besides sender, indicate that
        if (recipients.size() <= 1) {
            Component noOthers = Component.literal("Нет других дипломатов онлайн.");
            sender.sendSystemMessage(noOthers);
        }

        LOGGER.debug("[Diplomacy] {} sent message (len={})", senderName, trimmed.length());

        return Result.success(null);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PvP Hook
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Determines whether PvP damage should be cancelled between two players.
     * <p>
     * Returns {@code true} if a truce is active AND the two players belong to
     * different factions.
     *
     * @param attacker the attacking player
     * @param victim   the victim player
     * @return {@code true} if PvP should be cancelled
     */
    public boolean shouldCancelPvP(ServerPlayer attacker, ServerPlayer victim) {
        if (!isTruceActive()) {
            return false;
        }

        Optional<FactionId> attackerFaction = attacker.getData(WpAttachmentTypes.FACTION.get());
        Optional<FactionId> victimFaction = victim.getData(WpAttachmentTypes.FACTION.get());

        if (attackerFaction.isEmpty() || victimFaction.isEmpty()) {
            return false;
        }

        // Cancel PvP only if players are from different factions
        return attackerFaction.get() != victimFaction.get();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Internal helpers
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Returns the opposing faction for the given faction.
     */
    private static FactionId getOpposingFaction(FactionId faction) {
        return faction == FactionId.ZARNAVIA ? FactionId.CHERNOGRYAD : FactionId.ZARNAVIA;
    }

    /**
     * Returns all online players with GENERAL or OP role belonging to the specified faction.
     */
    private List<ServerPlayer> getOnlineGenerals(MinecraftServer server, FactionId faction) {
        List<ServerPlayer> generals = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Optional<FactionId> playerFaction = player.getData(WpAttachmentTypes.FACTION.get());
            if (playerFaction.isPresent() && playerFaction.get() == faction) {
                Role role = player.getData(WpAttachmentTypes.ROLE.get());
                if (role.atLeast(Role.GENERAL)) {
                    generals.add(player);
                }
            }
        }
        return generals;
    }

    /**
     * Returns all online players eligible for the diplomacy channel
     * (GENERAL or OP role, any faction).
     */
    private List<ServerPlayer> getEligibleDiplomacyRecipients(MinecraftServer server) {
        List<ServerPlayer> recipients = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Role role = player.getData(WpAttachmentTypes.ROLE.get());
            if (role.atLeast(Role.GENERAL)) {
                recipients.add(player);
            }
        }
        return recipients;
    }

    /**
     * Broadcasts a message to all online players.
     */
    private void broadcastToAll(MinecraftServer server, Component message) {
        server.getPlayerList().broadcastSystemMessage(message, false);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // State accessors (for testing and commands)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Returns the currently active truce, or {@code null} if none.
     */
    public @Nullable TruceState getActiveTruce() {
        return activeTruce;
    }

    /**
     * Returns the pending truce proposal, or {@code null} if none.
     */
    public @Nullable TruceProposal getPendingTruceProposal() {
        return pendingTruceProposal;
    }

    /**
     * Returns the pending exchange proposal, or {@code null} if none.
     */
    public @Nullable ExchangeProposal getPendingExchange() {
        return pendingExchange;
    }
}
