package com.frostlogic.warproject.server.collab;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.WpConfig;
import com.frostlogic.warproject.attachment.EnemyTickCounter;
import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.attachment.PlayerState;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import com.frostlogic.warproject.persistence.Database;
import com.frostlogic.warproject.persistence.dao.AuditLogDao;
import com.frostlogic.warproject.persistence.dao.PlayersDao;
import com.frostlogic.warproject.server.region.BaseRegion;
import com.frostlogic.warproject.server.region.RegionCacheHandler;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * Service responsible for marking players as collaborators (Req. 11) — both via
 * the manual {@code /wp collab} / {@code /wp uncollab} admin commands and via
 * the automatic detection rule that flags any {@code ACCEPTED} player who has
 * spent {@link WpConfig#COLLABORATOR_THRESHOLD_TICKS} continuous ticks inside
 * an enemy faction's base region.
 *
 * <h2>Atomicity model</h2>
 * Every state-changing entrypoint ({@link #set}, {@link #unset},
 * {@link #setAuto}) performs <em>two</em> persistent writes inside a single
 * {@link Database#transaction(java.util.function.Consumer)} so the
 * {@code players.collaborator}/{@code players.collab_reason} update and the
 * accompanying {@code audit_log} row commit together (Req. 11.1, 11.3,
 * 20.3 / design §13.1). Post-commit the {@link WpAttachmentTypes#COLLABORATOR}
 * attachment on the live {@link ServerPlayer} is refreshed so subsequent
 * {@code PlayerPublicViewPayload} broadcasts and TAB-prefix renders see the
 * new value without waiting for a relog.
 *
 * <h2>Auto-tracking ({@code PlayerTickEvent.Post})</h2>
 * The {@link #onPlayerTick(PlayerTickEvent.Post)} subscriber implements the
 * pseudocode from design §8.5:
 * <pre>
 *   on PlayerTickEvent.Post(player):
 *       if player.isSpectator() or player.isDeadOrDying(): return
 *       region = regionService.regionAt(player.level().dimension(), player.position())
 *       if region != null and region.faction != player.faction:
 *           counter = player.getData(ENEMY_TICKS).increment()
 *           if counter.ticks &ge; config.collaborator.thresholdTicks:
 *               collaboratorService.setAuto(player, "auto: presence in enemy region")
 *               counter.reset()
 *       else:
 *           player.getData(ENEMY_TICKS).reset()
 * </pre>
 * The counter is stored in the {@link WpAttachmentTypes#ENEMY_TICKS}
 * {@link EnemyTickCounter} attachment (NBT-serialised) so it survives relog,
 * dimension changes, and respawn — only an explicit reset (leaving the enemy
 * region or hitting the threshold) zeroes it.
 *
 * <p>To keep the per-tick path cheap, the subscriber short-circuits early for
 * players who are not subject to the rule (not ACCEPTED, no faction set, in
 * spectator, dead/dying, or already flagged as collaborator).
 *
 * <p><strong>Lifecycle.</strong> A single instance is constructed and registered
 * via {@link #install(CollaboratorService)} during
 * {@code WpCommandRoot.onServerStarted}. The static {@link #onPlayerTick}
 * subscriber resolves the active instance through {@link #current()}; when no
 * instance is registered (server stopping / not yet started) the tick handler
 * is a no-op. {@link #uninstall()} clears the reference so a stopped server
 * does not retain stale state on the next start.
 *
 * <p>Requirements: 11.1, 11.3, 11.4
 * <br>Design: §8.5, §13.1
 */
@EventBusSubscriber(modid = WarProject.MOD_ID)
public final class CollaboratorService {

    /** Audit action recorded when a player is flagged as collaborator. */
    public static final String AUDIT_ACTION_COLLAB = "COLLAB";

    /** Audit action recorded when a player is unflagged. */
    public static final String AUDIT_ACTION_UNCOLLAB = "UNCOLLAB";

    /**
     * Reason text written to {@code players.collab_reason} and
     * {@code audit_log.reason} when the auto-detector flags a player.
     */
    public static final String AUTO_REASON = "auto: presence in enemy region";

    /**
     * Currently installed instance for the static event subscriber. Set by
     * {@link #install(CollaboratorService)}, cleared by {@link #uninstall()}.
     */
    private static volatile CollaboratorService current;

    private final Database database;
    private final PlayersDao playersDao;
    private final AuditLogDao auditLogDao;

    public CollaboratorService(Database database, PlayersDao playersDao, AuditLogDao auditLogDao) {
        this.database = Objects.requireNonNull(database, "database");
        this.playersDao = Objects.requireNonNull(playersDao, "playersDao");
        this.auditLogDao = Objects.requireNonNull(auditLogDao, "auditLogDao");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Singleton wiring (used by the static @SubscribeEvent below)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Installs the given service as the active instance for the static tick
     * subscriber. Called from {@code WpCommandRoot.onServerStarted}.
     */
    public static void install(CollaboratorService service) {
        current = Objects.requireNonNull(service, "service");
    }

    /** Clears the installed instance (call on server stopping). */
    public static void uninstall() {
        current = null;
    }

    /** Returns the installed instance, or {@code null} if none is installed. */
    @Nullable
    public static CollaboratorService current() {
        return current;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Marks the target as a collaborator on behalf of the given actor.
     * <p>
     * In a single transaction:
     * <ol>
     *   <li>{@code UPDATE players SET collaborator=1, collab_reason=? WHERE uuid=?}</li>
     *   <li>{@code INSERT INTO audit_log(... action='COLLAB', reason=?, ...)}</li>
     * </ol>
     * Post-commit, the {@link WpAttachmentTypes#COLLABORATOR} attachment on
     * the target is set to {@code true}.
     *
     * @param target     the player to flag
     * @param actorUuid  UUID of the initiator (may be {@code null} for console)
     * @param actorName  display name of the initiator (may be {@code null} for console)
     * @param reason     human-readable reason; persisted in both the
     *                   {@code players} row and the audit row
     * @return {@link Result#Success} on commit, {@link Result#Failure} on
     *         persistence error (already logged)
     */
    public Result set(ServerPlayer target, @Nullable String actorUuid,
                      @Nullable String actorName, String reason) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(reason, "reason");
        return runTransaction(target, actorUuid, actorName, reason, true);
    }

    /**
     * Clears the collaborator flag on behalf of the given actor.
     * <p>
     * Symmetric to {@link #set}: writes {@code collaborator=0} and
     * {@code collab_reason=NULL}, plus an {@code UNCOLLAB} audit row carrying
     * {@code reason}. Post-commit the {@link WpAttachmentTypes#COLLABORATOR}
     * attachment is reset to {@code false}.
     *
     * @param target    the player to unflag
     * @param actorUuid UUID of the initiator (may be {@code null} for console)
     * @param actorName display name of the initiator (may be {@code null} for console)
     * @param reason    human-readable reason for the audit row
     */
    public Result unset(ServerPlayer target, @Nullable String actorUuid,
                        @Nullable String actorName, String reason) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(reason, "reason");
        return runTransaction(target, actorUuid, actorName, reason, false);
    }

    /**
     * Auto-flags the target as a collaborator (Req. 11.4). Equivalent to
     * {@link #set} with {@code actor=null} and the canonical
     * {@link #AUTO_REASON} string. Used by the per-tick auto-detector when the
     * configured threshold of consecutive ticks in an enemy region is reached.
     */
    public Result setAuto(ServerPlayer target, String reason) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(reason, "reason");
        return set(target, null, null, reason);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Per-tick auto-detection
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Routes {@link PlayerTickEvent.Post} to the active service instance.
     * No-op when no service is installed (server stopping / not yet started).
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        CollaboratorService service = current;
        if (service == null) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        service.tickPlayer(player);
    }

    /**
     * Per-tick auto-detection logic for a single player. Implements the
     * pseudocode from design §8.5.
     */
    private void tickPlayer(ServerPlayer player) {
        // Only ACCEPTED players are subject to the auto-flag rule. Earlier
        // states (NEW, REGISTERED_PENDING, …, CANDIDATE) either cannot be
        // outside their faction's base (CANDIDATE is region-locked by
        // CandidateRulesHandler) or have no faction at all.
        PlayerState state = player.getData(WpAttachmentTypes.PLAYER_STATE.get());
        if (state != PlayerState.ACCEPTED) {
            return;
        }

        // Spectator / dead players cannot meaningfully "occupy" a region.
        if (player.isSpectator() || player.isDeadOrDying()) {
            return;
        }

        Optional<FactionId> factionOpt = player.getData(WpAttachmentTypes.FACTION.get());
        if (factionOpt.isEmpty()) {
            // Defensive: an ACCEPTED player without a faction is an inconsistent
            // state. Skip silently — the auto-detector should not patch it up.
            return;
        }
        FactionId playerFaction = factionOpt.get();

        // Already a collaborator? Nothing more to do — the counter would only
        // be re-armed if an admin uncollabs the player (which itself resets
        // the attachment via unset()).
        if (player.getData(WpAttachmentTypes.COLLABORATOR.get())) {
            return;
        }

        @Nullable BaseRegion region = RegionCacheHandler.regionService()
                .regionAt(player.serverLevel().dimension(), player.position());
        boolean inEnemyRegion = region != null && region.faction() != playerFaction;

        EnemyTickCounter counter = player.getData(WpAttachmentTypes.ENEMY_TICKS.get());

        if (inEnemyRegion) {
            EnemyTickCounter incremented = counter.increment();
            int threshold = WpConfig.COLLABORATOR_THRESHOLD_TICKS.get();
            if (incremented.ticks() >= threshold) {
                // Threshold reached → mark and reset counter so we don't
                // re-trigger every subsequent tick.
                Result result = setAuto(player, AUTO_REASON);
                if (result instanceof Result.Failure failure) {
                    WarProject.LOGGER.error(
                            "[WP Collab] Auto-flag persistence failed for {}: {}",
                            player.getGameProfile().getName(),
                            failure.cause().getMessage());
                    // Keep the counter at the threshold value so we retry on
                    // the next tick rather than spinning back from zero.
                    return;
                }
                player.setData(WpAttachmentTypes.ENEMY_TICKS.get(), EnemyTickCounter.ZERO);
                WarProject.LOGGER.info(
                        "[WP Collab] Auto-flagged {} after {} ticks in enemy region (faction={}, region.faction={}).",
                        player.getGameProfile().getName(), incremented.ticks(),
                        playerFaction.getSerializedName(), region.faction().getSerializedName());
            } else {
                player.setData(WpAttachmentTypes.ENEMY_TICKS.get(), incremented);
            }
        } else if (counter.ticks() != 0) {
            // Not in an enemy region → reset (Req. 11.4, design §8.5: counter
            // resets on leaving enemy territory to prevent accumulation).
            player.setData(WpAttachmentTypes.ENEMY_TICKS.get(), EnemyTickCounter.ZERO);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Internals
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Shared transactional core for {@link #set}, {@link #unset}, and
     * {@link #setAuto}. Performs the {@code players} update + {@code audit_log}
     * insert atomically; refreshes the {@link WpAttachmentTypes#COLLABORATOR}
     * attachment only on commit.
     */
    private Result runTransaction(ServerPlayer target, @Nullable String actorUuid,
                                  @Nullable String actorName, String reason, boolean mark) {
        long now = System.currentTimeMillis();
        String targetUuid = target.getStringUUID();
        String targetName = target.getGameProfile().getName();
        String action = mark ? AUDIT_ACTION_COLLAB : AUDIT_ACTION_UNCOLLAB;

        try {
            database.transaction(conn -> {
                playersDao.setCollaborator(conn, targetUuid, mark, mark ? reason : null);
                auditLogDao.insert(conn, now, actorUuid, actorName,
                        targetUuid, targetName, action, reason, null);
            });
        } catch (RuntimeException e) {
            WarProject.LOGGER.error(
                    "[WP Collab] {} transaction failed for actor={} target={}: {}",
                    action, actorName, targetName, e.getMessage(), e);
            return new Result.Failure(e);
        }

        // Post-commit: refresh in-memory attachment so the rest of the system
        // (TAB renderer, public-view payload, radial-menu visibility) sees
        // the new value immediately.
        target.setData(WpAttachmentTypes.COLLABORATOR.get(), mark);

        return Result.SUCCESS;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Result types
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Outcome of a {@link #set} / {@link #unset} / {@link #setAuto} call.
     * Sealed so callers can pattern-match exhaustively.
     */
    public sealed interface Result {

        /** Singleton success value (no fields). */
        Success SUCCESS = new Success();

        /** Successful commit. */
        record Success() implements Result {}

        /** Persistence failure; {@link #cause()} carries the original throwable. */
        record Failure(Throwable cause) implements Result {}
    }
}
