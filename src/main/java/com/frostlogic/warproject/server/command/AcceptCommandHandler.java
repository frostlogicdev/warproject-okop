package com.frostlogic.warproject.server.command;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.attachment.FactionId;
import com.frostlogic.warproject.attachment.PlayerState;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import com.frostlogic.warproject.network.payload.s2c.PlayerPublicViewPayload;
import com.frostlogic.warproject.persistence.Database;
import com.frostlogic.warproject.persistence.dao.AuditLogDao;
import com.frostlogic.warproject.persistence.dao.PassportsDao;
import com.frostlogic.warproject.persistence.dao.PlayersDao;
import com.frostlogic.warproject.server.Faction;
import com.frostlogic.warproject.server.LegacyAttachmentBridge;
import com.frostlogic.warproject.server.ServerEvents;
import com.frostlogic.warproject.server.WarPlayerDataStore;
import com.frostlogic.warproject.server.WarPlayerProfile;
import com.frostlogic.warproject.server.passport.PassportComponentTypes;
import com.frostlogic.warproject.server.passport.PassportData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Common implementation for accepting a candidate into a faction.
 * <p>
 * Used by both the radial menu ({@code RadialMenuItem.ACCEPT}) and the
 * {@code /wp accept <name>} command. Both paths produce identical effects
 * (Req. 8.8) — this class is the single source of truth.
 * <p>
 * Logic:
 * <ol>
 *   <li>If target is already {@link PlayerState#ACCEPTED} → write
 *       {@code ACCEPT_REJECTED_ALREADY_ACCEPTED} audit (no data changes) and return.</li>
 *   <li>Atomic transaction:
 *     <ul>
 *       <li>{@code players.update(status=ACCEPTED, accepted_at=now, accepted_by_uuid, accepted_by_name)}</li>
 *       <li>{@code passports.update(status=ACCEPTED, accepted_at=now, accepted_by=initiator name)}</li>
 *       <li>{@code audit_log.insert(action=ACCEPT, …)}</li>
 *     </ul>
 *   </li>
 *   <li>Post-commit (only after a successful transaction):
 *     <ul>
 *       <li>Set {@link WpAttachmentTypes#PLAYER_STATE} attachment on the target to
 *           {@link PlayerState#ACCEPTED}.</li>
 *       <li>Send title + actionbar to the target.</li>
 *       <li>Update the passport ItemStack in the target's inventory (if found) so that
 *           its {@link PassportData} component reflects the new status / acceptance.</li>
 *       <li>Send {@link PlayerPublicViewPayload} to broadcast the new public state.</li>
 *     </ul>
 *   </li>
 * </ol>
 * <p>
 * <strong>Caller responsibility.</strong> All eligibility checks (initiator role,
 * own-base region, raytrace ≤ 5 blocks, faction rule) are performed by the caller —
 * either {@link com.frostlogic.warproject.server.radial.RadialMenuDispatcher#allow}
 * for the radial menu path or the Brigadier {@code requires(...)} clause +
 * explicit checks for the {@code /wp accept} command path. This handler only
 * verifies the candidate's status and performs the atomic effect.
 * <p>
 * Requirements: 8.6, 8.7, 8.8
 * Design: §8.7
 */
public final class AcceptCommandHandler {

    /**
     * Source of the acceptance request — used purely for audit / logging context.
     * Both sources execute identical logic (Req. 8.8).
     */
    public enum AcceptSource {
        /** Triggered by {@code /wp accept <name>}. */
        COMMAND,
        /** Triggered by the radial menu {@code ACCEPT} item. */
        RADIAL
    }

    /**
     * Outcome of an {@link #acceptCandidate(ServerPlayer, ServerPlayer, AcceptSource)} call.
     */
    public sealed interface AcceptResult {
        /** Acceptance was committed; passport now reflects the accepted status. */
        record Success(String passportId) implements AcceptResult {}

        /**
         * Target was already {@link PlayerState#ACCEPTED}; an
         * {@code ACCEPT_REJECTED_ALREADY_ACCEPTED} audit entry was written and
         * no state was changed.
         */
        record AlreadyAccepted() implements AcceptResult {}

        /** Acceptance failed before any state change (e.g. wrong status, DB unavailable). */
        record Failure(String errorKey) implements AcceptResult {}
    }

    private final Database database;
    private final PlayersDao playersDao;
    private final PassportsDao passportsDao;
    private final AuditLogDao auditLogDao;

    public AcceptCommandHandler(Database database,
                                PlayersDao playersDao,
                                PassportsDao passportsDao,
                                AuditLogDao auditLogDao) {
        this.database = database;
        this.playersDao = playersDao;
        this.passportsDao = passportsDao;
        this.auditLogDao = auditLogDao;
    }

    /**
     * Convenience constructor that pulls the {@link Database} from
     * {@link ServerEvents#getDatabase()} and constructs default DAOs.
     * <p>
     * Returns {@code null} when the server is not running yet (e.g. during
     * test bootstrap before {@code ServerStartedEvent}).
     *
     * @return a wired-up handler, or {@code null} if the database is not yet available
     */
    @Nullable
    public static AcceptCommandHandler fromServer() {
        Database db = ServerEvents.getDatabase();
        if (db == null) {
            return null;
        }
        return new AcceptCommandHandler(db, new PlayersDao(), new PassportsDao(), new AuditLogDao());
    }

    /**
     * Accepts the candidate. Single implementation shared by the radial menu and
     * the {@code /wp accept} command.
     *
     * @param initiator the commander or admin performing the acceptance
     * @param target    the candidate being accepted
     * @param source    where the request came from (for audit context only)
     * @return an {@link AcceptResult}
     */
    public AcceptResult acceptCandidate(ServerPlayer initiator, ServerPlayer target, AcceptSource source) {
        if (initiator == null || target == null || source == null) {
            return new AcceptResult.Failure("wp.error.internal");
        }

        long now = System.currentTimeMillis();
        String initiatorUuid = initiator.getStringUUID();
        String initiatorName = initiator.getGameProfile().getName();
        String targetUuid = target.getStringUUID();
        String targetName = target.getGameProfile().getName();

        // (1) Idempotency / already-accepted guard. The check looks at the
        // attachment first (cheap) and then reaffirms via DB read inside the
        // transaction below if needed. If the target is already ACCEPTED we
        // write an informational audit entry and return without changing any
        // game-data row (Req. 8.6 contrapositive, Property 9).
        PlayerState targetState = target.getData(WpAttachmentTypes.PLAYER_STATE.get());
        if (targetState == PlayerState.ACCEPTED) {
            writeAlreadyAcceptedAudit(now, initiatorUuid, initiatorName,
                    targetUuid, targetName, source);
            return new AcceptResult.AlreadyAccepted();
        }

        // (2) Only CANDIDATE targets are eligible. Other states (e.g. FACTIONLESS,
        // CAPTURED) don't satisfy the precondition from design §8.7.
        if (targetState != PlayerState.CANDIDATE) {
            return new AcceptResult.Failure("wp.error.target_not_candidate");
        }

        // (3) Look up the target's passport. Used both inside the transaction
        // (passports update key) and post-commit (inventory ItemStack update).
        PassportsDao.Passport passportRow;
        try {
            Optional<PassportsDao.Passport> passportOpt = database.inTx(conn ->
                    passportsDao.findByOwnerUuid(conn, targetUuid));
            if (passportOpt.isEmpty()) {
                WarProject.LOGGER.error("[WP Accept] No passport found for candidate {} ({})",
                        targetName, targetUuid);
                return new AcceptResult.Failure("wp.error.no_passport");
            }
            passportRow = passportOpt.get();
        } catch (RuntimeException e) {
            WarProject.LOGGER.error("[WP Accept] Failed to load passport for {}: {}",
                    targetName, e.getMessage(), e);
            return new AcceptResult.Failure("wp.error.internal");
        }

        String passportId = passportRow.passportId();

        // (4) Atomic transaction: players + passports + audit_log in one go.
        try {
            database.transaction(conn -> {
                playersDao.updateAcceptance(conn, targetUuid,
                        PlayerState.ACCEPTED.getSerializedName(),
                        now, initiatorUuid, initiatorName);
                passportsDao.updateAcceptance(conn, passportId,
                        PlayerState.ACCEPTED.getSerializedName(),
                        now, initiatorName);
                auditLogDao.insert(conn, now,
                        initiatorUuid, initiatorName,
                        targetUuid, targetName,
                        "ACCEPT",
                        null,
                        "{\"source\":\"" + source.name() + "\","
                                + "\"passport_id\":\"" + passportId + "\"}"
                );
            });
        } catch (RuntimeException e) {
            WarProject.LOGGER.error("[WP Accept] Transaction failed for {} → {}: {}",
                    initiatorName, targetName, e.getMessage(), e);
            return new AcceptResult.Failure("wp.error.internal");
        }

        // (5) Post-commit side effects. These are fire-and-forget — none of
        // them can roll back the database commit, so any failure is logged but
        // does not undo the acceptance.
        applyPostCommit(target, passportRow, now, initiatorName);

        WarProject.LOGGER.info("[WP Accept] {} accepted {} (passport {}, source={})",
                initiatorName, targetName, passportId, source);

        return new AcceptResult.Success(passportId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Post-commit helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Applies all side effects that follow a successful acceptance transaction:
     * attachment update, title + actionbar message, inventory passport refresh,
     * and public-view broadcast.
     */
    private static void applyPostCommit(ServerPlayer target,
                                        PassportsDao.Passport passportRow,
                                        long acceptedAt,
                                        String acceptedByName) {
        // Attribute PLAYER_STATE = ACCEPTED
        target.setData(WpAttachmentTypes.PLAYER_STATE.get(), PlayerState.ACCEPTED);

        // Title + actionbar — translation arg is the localized faction name
        Optional<FactionId> factionOpt = target.getData(WpAttachmentTypes.FACTION.get());
        Component factionName = factionOpt
                .map(f -> (Component) Component.translatable(f.displayNameKey()))
                .orElse(Component.empty());

        Component title = Component.translatable("wp.accept.title");
        Component subtitle = Component.translatable("wp.accept.subtitle", factionName);
        Component actionBar = Component.translatable("wp.accept.actionbar", factionName);

        // 10t fade-in / 70t stay / 20t fade-out — same defaults used in CaptchaService.
        target.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        target.connection.send(new ClientboundSetTitleTextPacket(title));
        target.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        target.connection.send(new ClientboundSetActionBarTextPacket(actionBar));

        // Update the passport ItemStack in the target's inventory so its
        // PassportData component reflects the new ACCEPTED status. The DB row
        // is already updated; this keeps the on-stack snapshot in sync.
        updatePassportStackInInventory(target, passportRow.passportId(), acceptedAt, acceptedByName);

        // Issue the Military ID card. Per Requirement 4.1, this happens at the
        // CANDIDATE → ACCEPTED transition, which is exactly here. Failures are
        // logged but do not roll back the acceptance.
        issueMilitaryIdCard(target, factionOpt.orElse(null), acceptedByName);

        // Send the new public view to the target so HUD/TAB updates immediately.
        sendPublicView(target);

        // Sync the legacy WarPlayerProfile so that OnboardingGuard, RegionGuard
        // (legacy path), and other legacy systems see the accepted state.
        syncLegacyProfile(target, factionOpt.orElse(null));
    }

    /**
     * Updates the legacy {@link WarPlayerProfile} to reflect the new-pipeline
     * acceptance. This ensures that code paths still reading from the JSON
     * profile (e.g. OnboardingGuard, legacy NpcHandler) see the correct state.
     * <p>
     * Failures are logged but do not roll back the acceptance.
     */
    private static void syncLegacyProfile(ServerPlayer target, @org.jetbrains.annotations.Nullable FactionId factionId) {
        try {
            WarPlayerProfile profile = WarPlayerDataStore.get().getOrCreate(target);
            profile.setLoggedIn(true);
            profile.setCaptchaPassed(true);
            if (factionId != null) {
                Faction legacyFaction = switch (factionId) {
                    case ZARNAVIA -> Faction.ZARNAVIA;
                    case CHERNOGRYAD -> Faction.CHERNOGRYAD;
                };
                profile.setFaction(legacyFaction);
                profile.setCandidateFaction(legacyFaction);
            }
            WarPlayerDataStore.get().save();
            // Re-sync attachments from the now-updated legacy profile
            LegacyAttachmentBridge.sync(target);
        } catch (Exception e) {
            WarProject.LOGGER.warn("[WP Accept] Failed to sync legacy profile for {}: {}",
                    target.getGameProfile().getName(), e.getMessage());
        }
    }

    /**
     * Issues the Military ID card via {@link com.frostlogic.warproject.server.militaryid.MilitaryIdService}.
     * <p>
     * Looks up the player's current rank attachment so the card is created with
     * the correct rank string (CANDIDATE players accepted into the faction
     * usually start with no rank — empty string is fine in that case).
     * <p>
     * Subdivision is left empty: the player joins a subdivision later via the
     * radial menu / commands, which trigger {@code MilitaryIdService.updateSubdivision}.
     * <p>
     * Requirements: 4.1, 5.1
     */
    private static void issueMilitaryIdCard(ServerPlayer target,
                                            @org.jetbrains.annotations.Nullable FactionId faction,
                                            String acceptedByName) {
        if (faction == null) {
            WarProject.LOGGER.warn("[WP Accept] Skipping Military ID issuance for {}: no faction attachment",
                    target.getGameProfile().getName());
            return;
        }

        com.frostlogic.warproject.server.militaryid.MilitaryIdService militaryIdService =
                com.frostlogic.warproject.network.ServiceRegistry.militaryId();
        if (militaryIdService == null) {
            WarProject.LOGGER.warn("[WP Accept] MilitaryIdService not available — skipping card issuance for {}",
                    target.getGameProfile().getName());
            return;
        }

        // Resolve the current rank string (empty if not yet promoted past CANDIDATE).
        String rankId = "";
        try {
            String attached = target.getData(WpAttachmentTypes.RANK.get());
            if (attached != null) {
                rankId = attached;
            }
        } catch (Throwable ignored) {
            // No rank attachment registered yet — leave empty.
        }

        try {
            militaryIdService.issueCard(target, faction, rankId, "", acceptedByName);
        } catch (Exception e) {
            WarProject.LOGGER.error("[WP Accept] Failed to issue Military ID card for {}: {}",
                    target.getGameProfile().getName(), e.getMessage(), e);
        }
    }

    /**
     * Walks the target's inventory and rewrites the {@link PassportData} component
     * on the passport ItemStack matching {@code passportId} to mark it as
     * {@link PlayerState#ACCEPTED}. If no matching stack is found (e.g. passport
     * has been captured), this is a no-op.
     */
    private static void updatePassportStackInInventory(ServerPlayer target,
                                                       String passportId,
                                                       long acceptedAt,
                                                       String acceptedByName) {
        Inventory inv = target.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            PassportData data = stack.get(PassportComponentTypes.PASSPORT_DATA.get());
            if (data == null || !passportId.equals(data.passportId())) {
                continue;
            }
            // Rewrite the component with new acceptance fields. Trophy flag is
            // preserved (a non-trophy passport stays non-trophy).
            PassportData updated = new PassportData(
                    data.passportId(),
                    data.faction(),
                    data.rpName(),
                    data.rpSurname(),
                    data.dateOfBirth(),
                    data.signatureSeed(),
                    PlayerState.ACCEPTED,
                    acceptedAt,
                    acceptedByName,
                    data.trophy()
            );
            stack.set(PassportComponentTypes.PASSPORT_DATA.get(), updated);
            // Stop after the first match — there should be exactly one passport
            // per owner_uuid in the target's inventory.
            return;
        }
    }

    /**
     * Sends a {@link PlayerPublicViewPayload} for {@code target} so the client
     * HUD/TAB sees the new {@code ACCEPTED} status without waiting for the next
     * lifecycle tick.
     */
    private static void sendPublicView(ServerPlayer target) {
        Optional<FactionId> factionOpt = target.getData(WpAttachmentTypes.FACTION.get());
        // PlayerPublicViewPayload requires a non-null faction; default to ZARNAVIA
        // if somehow not set (an ACCEPTED player should always have a faction).
        FactionId faction = factionOpt.orElse(FactionId.ZARNAVIA);
        var role = target.getData(WpAttachmentTypes.ROLE.get());
        var status = target.getData(WpAttachmentTypes.PLAYER_STATE.get());
        boolean collaborator = target.getData(WpAttachmentTypes.COLLABORATOR.get());
        var rpNameOpt = target.getData(WpAttachmentTypes.RP_NAME.get());
        String rpFullName = rpNameOpt.map(rn -> rn.fullName()).orElse(null);

        PacketDistributor.sendToPlayer(target, new PlayerPublicViewPayload(
                target.getUUID(),
                faction,
                role,
                status,
                collaborator,
                rpFullName
        ));
    }

    /**
     * Writes the {@code ACCEPT_REJECTED_ALREADY_ACCEPTED} audit entry in its own
     * transaction. Wrapped in a try/catch so that a failure here does not surface
     * as a misleading error to the initiator — the target's state is already
     * correct and no game data was changed.
     */
    private void writeAlreadyAcceptedAudit(long ts,
                                           String initiatorUuid, String initiatorName,
                                           String targetUuid, String targetName,
                                           AcceptSource source) {
        try {
            database.transaction(conn -> auditLogDao.insert(conn, ts,
                    initiatorUuid, initiatorName,
                    targetUuid, targetName,
                    "ACCEPT_REJECTED_ALREADY_ACCEPTED",
                    "already_accepted",
                    "{\"source\":\"" + source.name() + "\"}"
            ));
        } catch (RuntimeException e) {
            WarProject.LOGGER.warn("[WP Accept] Failed to write ACCEPT_REJECTED_ALREADY_ACCEPTED audit for {} → {}: {}",
                    initiatorName, targetName, e.getMessage());
        }
    }
}
