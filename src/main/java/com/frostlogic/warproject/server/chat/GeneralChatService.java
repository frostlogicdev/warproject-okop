package com.frostlogic.warproject.server.chat;

import com.frostlogic.warproject.WarProject;
import com.frostlogic.warproject.attachment.Role;
import com.frostlogic.warproject.attachment.RpName;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import com.frostlogic.warproject.persistence.Database;
import com.frostlogic.warproject.persistence.dao.AuditLogDao;
import com.frostlogic.warproject.persistence.dao.CooldownsDao;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Closed-channel chat for {@code COMMANDER}, {@code GENERAL}, and {@code OP}
 * roles across both factions (Req. 15).
 * <p>
 * Implements <strong>Property 22</strong> (design §12):
 * <ul>
 *   <li>{@link #canSend(ServerPlayer, long)} returns {@code true} iff the
 *       initiator's role is at least {@link Role#COMMANDER} and there is no
 *       active {@code GC} cooldown (i.e. {@code expires_at <= now}).</li>
 *   <li>After a successful {@link #send(ServerPlayer, String)}, the
 *       {@code cooldowns(uuid, GC)} row is upserted with
 *       {@code expires_at = now + 3_000} ms (Req. 15.4, tuned to 3s for
 *       lively radio chatter).</li>
 *   <li>{@link #recipients(MinecraftServer)} returns the set of online players
 *       whose role is at least {@link Role#COMMANDER}, regardless of faction
 *       (Req. 15.3).</li>
 * </ul>
 * <p>
 * Atomicity (design §13.1): the post-send DB writes — the cooldown upsert and
 * the {@code GENERALCHAT_SEND} audit row — are committed together inside a
 * single {@link Database#transaction(java.util.function.Consumer)}. Recipient
 * broadcasting only fires after the commit succeeds, so a failed transaction
 * does not produce a "sent but no cooldown" state.
 * <p>
 * The per-message body is <em>not</em> recorded in {@code audit_log} or in
 * the general logger output (Req. 20.4) — only the message length is stashed
 * in {@code extra_json}.
 * <p>
 * Requirements: 15.1–15.4
 * Design: §7, §12 Property 22
 */
public final class GeneralChatService {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** {@code cd_type} value used in the {@code cooldowns} table for GeneralChat. */
    public static final String COOLDOWN_TYPE = "GC";

    /**
     * Cooldown duration in milliseconds (Req. 15.4).
     * <p>
     * Tuned to 3 seconds for an active wartime-radio feel: tactical updates
     * flow freely between commanders without 30s waits, but rapid-fire spam
     * is still throttled.
     */
    public static final long COOLDOWN_MILLIS = 3_000L;

    /** Audit action recorded in {@code audit_log} for every successful send (Req. 20.3). */
    public static final String AUDIT_ACTION = "GENERALCHAT_SEND";

    private final Database database;
    private final CooldownsDao cooldownsDao;
    private final AuditLogDao auditLogDao;

    public GeneralChatService(Database database, CooldownsDao cooldownsDao, AuditLogDao auditLogDao) {
        this.database = Objects.requireNonNull(database, "database");
        this.cooldownsDao = Objects.requireNonNull(cooldownsDao, "cooldownsDao");
        this.auditLogDao = Objects.requireNonNull(auditLogDao, "auditLogDao");
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Public API — Property 22 surface
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Returns {@code true} iff the initiator is allowed to send a GeneralChat
     * message at the given wall-clock instant.
     * <p>
     * Equivalent to: {@code role ≥ COMMANDER ∧ cooldowns(uuid, GC).expires_at ≤ now}.
     * <p>
     * The cooldown lookup is performed in its own short read-only transaction
     * (no write lock needed). A missing row is treated as "no active cooldown".
     *
     * @param initiator the would-be sender
     * @param nowMillis current wall-clock time, in milliseconds since epoch
     * @return {@code true} iff role and cooldown conditions both pass
     */
    public boolean canSend(ServerPlayer initiator, long nowMillis) {
        Objects.requireNonNull(initiator, "initiator");
        if (!hasRequiredRole(initiator)) {
            return false;
        }
        return !isOnCooldown(initiator.getStringUUID(), nowMillis);
    }

    /**
     * Returns all online players whose role qualifies them as GeneralChat
     * recipients (role ≥ {@link Role#COMMANDER}). Includes players from both
     * factions and OPs without a faction.
     * <p>
     * The returned list is a fresh snapshot — callers may mutate it freely.
     *
     * @param server the running server (must not be null)
     * @return mutable list of qualifying online players
     */
    public List<ServerPlayer> recipients(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (hasRequiredRole(p)) {
                out.add(p);
            }
        }
        return out;
    }

    /**
     * Full send pipeline:
     * <ol>
     *   <li>Validate the message is non-empty.</li>
     *   <li>Re-check role gate ({@link #hasRequiredRole(ServerPlayer)}).</li>
     *   <li>Re-check cooldown ({@code expires_at &gt; now} ⇒ reject with
     *       {@link SendResult.CooldownActive}).</li>
     *   <li>Compute recipients; if the set is empty, the cooldown is
     *       <em>not</em> set and the result is {@link SendResult.NoRecipients}
     *       so the initiator can retry without waiting the cooldown.</li>
     *   <li>Atomically upsert {@code cooldowns(uuid, GC, now + COOLDOWN_MILLIS)}
     *       and insert a {@code GENERALCHAT_SEND} audit row in a single
     *       transaction.</li>
     *   <li>After commit, broadcast {@code Component.translatable("wp.gc.line",
     *       actor, message)} to every recipient.</li>
     * </ol>
     * <p>
     * <strong>Note on the empty-recipient short-circuit.</strong> Property 22
     * specifies the cooldown is set "after a successful send". A send to an
     * empty audience is treated as not-successful here so the initiator is not
     * locked out by accident (e.g. they were the only COMMANDER+ online and
     * someone just disconnected). This is an implementation detail outside the
     * formal property; see design §12 Property 22.
     *
     * @param initiator the sender (must be online)
     * @param message   the raw message; will be trimmed before broadcast
     * @return a {@link SendResult} describing the outcome
     */
    public SendResult send(ServerPlayer initiator, String message) {
        Objects.requireNonNull(initiator, "initiator");
        Objects.requireNonNull(message, "message");

        String trimmed = message.trim();
        if (trimmed.isEmpty()) {
            return new SendResult.EmptyMessage();
        }

        if (!hasRequiredRole(initiator)) {
            return new SendResult.InsufficientRole();
        }

        long now = System.currentTimeMillis();
        String uuid = initiator.getStringUUID();

        // Re-read the cooldown: canSend(...) might have been called before
        // the transaction (e.g. by the command layer) but we re-check here so
        // two near-simultaneous /wp generalchat invocations from the same
        // initiator can't both succeed.
        long expiresAt = lookupCooldownExpiry(uuid);
        if (expiresAt > now) {
            return new SendResult.CooldownActive(expiresAt - now);
        }

        MinecraftServer server = initiator.server;
        List<ServerPlayer> recipients = recipients(server);
        if (recipients.isEmpty()) {
            return new SendResult.NoRecipients();
        }

        long newExpiresAt = now + COOLDOWN_MILLIS;
        String actorName = displayName(initiator);
        String actorUuid = uuid;
        int len = trimmed.length();

        try {
            database.transaction(conn -> {
                cooldownsDao.upsert(conn, uuid, COOLDOWN_TYPE, newExpiresAt);
                // extra_json carries only the message length (Req. 20.4 — no
                // message body in audit/log).
                auditLogDao.insert(conn, now, actorUuid, actorName,
                        null, null, AUDIT_ACTION, null,
                        "{\"len\":" + len + "}");
            });
        } catch (RuntimeException e) {
            LOGGER.error("[WP GC] Transaction failed for {} ({}): {}",
                    actorName, actorUuid, e.getMessage(), e);
            return new SendResult.PersistenceFailure(e);
        }

        // Post-commit broadcast — recipients see the line only once the
        // cooldown is durably persisted.
        Component line = Component.translatable("wp.gc.line", actorName, trimmed);
        for (ServerPlayer recipient : recipients) {
            recipient.sendSystemMessage(line);
        }

        LOGGER.debug("[WP GC] {} ({}) sent to {} recipients (len={})",
                actorName, actorUuid, recipients.size(), len);

        return new SendResult.Success(recipients.size(), newExpiresAt);
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Returns {@code true} iff the player's WP role attachment is at least
     * {@link Role#COMMANDER}. Visible for tests and for {@link ChatScopeFilter}.
     */
    public static boolean hasRequiredRole(ServerPlayer player) {
        Role role = player.getData(WpAttachmentTypes.ROLE.get());
        return role.atLeast(Role.COMMANDER);
    }

    /**
     * Looks up the current GC-cooldown expiry for the given uuid; returns
     * {@code 0L} if no row exists.
     */
    private long lookupCooldownExpiry(String uuid) {
        return database.inTx(conn -> cooldownsDao.findByKey(conn, uuid, COOLDOWN_TYPE)
                .map(CooldownsDao.Cooldown::expiresAt)
                .orElse(0L));
    }

    /**
     * Cheap read-only check of "is there an unexpired cooldown row?".
     */
    private boolean isOnCooldown(String uuid, long nowMillis) {
        return database.inTx(conn -> cooldownsDao.isActive(conn, uuid, COOLDOWN_TYPE, nowMillis));
    }

    /**
     * Computes the player's display name for the chat line. Prefers the RP
     * name (e.g. "Иван Петров") if set; falls back to the Mojang profile name.
     */
    private static String displayName(ServerPlayer player) {
        Optional<RpName> rp = player.getData(WpAttachmentTypes.RP_NAME.get());
        return rp.map(RpName::fullName).orElseGet(() -> player.getGameProfile().getName());
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Result types
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Outcome of a {@link #send(ServerPlayer, String)} call. Sealed so that
     * callers can pattern-match exhaustively.
     */
    public sealed interface SendResult {

        /**
         * Successful broadcast. The cooldown is now active until
         * {@link #expiresAtMillis()}.
         */
        record Success(int recipientCount, long expiresAtMillis) implements SendResult {}

        /** The initiator's role is below {@link Role#COMMANDER}. */
        record InsufficientRole() implements SendResult {}

        /**
         * The initiator's GC cooldown is still active. {@link #remainingMillis()}
         * is the time left until {@code expires_at}.
         */
        record CooldownActive(long remainingMillis) implements SendResult {}

        /** The trimmed message was empty. */
        record EmptyMessage() implements SendResult {}

        /**
         * No qualifying recipients were online. The cooldown was not set;
         * the initiator may retry immediately.
         */
        record NoRecipients() implements SendResult {}

        /** Database transaction failed; the underlying cause is preserved. */
        record PersistenceFailure(Throwable cause) implements SendResult {}
    }
}
