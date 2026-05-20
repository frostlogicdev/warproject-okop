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
 * Server-wide announcement channel reserved for {@link Role#OP} (Req. 15,
 * re-tuned for the open-beta UX brief).
 * <p>
 * Two design changes vs. the original "closed COMMANDER+ radio" implementation:
 * <ol>
 *   <li><strong>Sender gate</strong>: only {@link Role#OP} may post. Commanders
 *       and generals can still coordinate inside the faction chat; the global
 *       channel is now strictly for admin announcements visible to every
 *       online player.</li>
 *   <li><strong>Audience</strong>: {@link #recipients(MinecraftServer)} returns
 *       every online {@link ServerPlayer}, regardless of role or faction, so
 *       announcements reach the whole server.</li>
 * </ol>
 * <p>
 * Cooldown stays at {@link #COOLDOWN_MILLIS} = 3s to keep rapid-fire spam in
 * check even from OPs; persistence and audit semantics are unchanged.
 * <p>
 * Atomicity (design §13.1): the post-send DB writes — the cooldown upsert
 * and the {@code GENERALCHAT_SEND} audit row — are committed together inside
 * a single {@link Database#transaction(java.util.function.Consumer)}.
 * Recipient broadcasting only fires after the commit succeeds.
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
     * Tuned to 3 seconds so admins can fire a quick burst of related
     * announcements without 30s waits while spam is still throttled.
     */
    public static final long COOLDOWN_MILLIS = 3_000L;

    /** Audit action recorded in {@code audit_log} for every successful send (Req. 20.3). */
    public static final String AUDIT_ACTION = "GENERALCHAT_SEND";

    /**
     * Minimum role required to broadcast on the announcement channel.
     * Currently {@link Role#OP} — admin-only.
     */
    public static final Role REQUIRED_SENDER_ROLE = Role.OP;

    private final Database database;
    private final CooldownsDao cooldownsDao;
    private final AuditLogDao auditLogDao;

    public GeneralChatService(Database database, CooldownsDao cooldownsDao, AuditLogDao auditLogDao) {
        this.database = Objects.requireNonNull(database, "database");
        this.cooldownsDao = Objects.requireNonNull(cooldownsDao, "cooldownsDao");
        this.auditLogDao = Objects.requireNonNull(auditLogDao, "auditLogDao");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Returns {@code true} iff the initiator is allowed to send an
     * announcement at the given wall-clock instant.
     * <p>
     * Equivalent to: {@code role ≥ OP ∧ cooldowns(uuid, GC).expires_at ≤ now}.
     */
    public boolean canSend(ServerPlayer initiator, long nowMillis) {
        Objects.requireNonNull(initiator, "initiator");
        if (!hasRequiredRole(initiator)) {
            return false;
        }
        return !isOnCooldown(initiator.getStringUUID(), nowMillis);
    }

    /**
     * Returns every online player on the server — announcements reach all
     * connected clients regardless of faction or role.
     * <p>
     * The returned list is a fresh snapshot — callers may mutate it freely.
     */
    public List<ServerPlayer> recipients(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return new ArrayList<>(server.getPlayerList().getPlayers());
    }

    /**
     * Full send pipeline:
     * <ol>
     *   <li>Validate the message is non-empty.</li>
     *   <li>Re-check sender role gate ({@link #hasRequiredRole(ServerPlayer)}).</li>
     *   <li>Re-check cooldown.</li>
     *   <li>Compute recipients; if the set is empty (impossible while the
     *       sender is online, kept for safety), short-circuit.</li>
     *   <li>Atomically upsert cooldown + insert {@code GENERALCHAT_SEND} audit.</li>
     *   <li>After commit, broadcast {@code Component.translatable("wp.gc.line",
     *       actor, message)} to every online player.</li>
     * </ol>
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
                auditLogDao.insert(conn, now, actorUuid, actorName,
                        null, null, AUDIT_ACTION, null,
                        "{\"len\":" + len + "}");
            });
        } catch (RuntimeException e) {
            LOGGER.error("[WP GC] Transaction failed for {} ({}): {}",
                    actorName, actorUuid, e.getMessage(), e);
            return new SendResult.PersistenceFailure(e);
        }

        Component line = Component.translatable("wp.gc.line", actorName, trimmed);
        for (ServerPlayer recipient : recipients) {
            recipient.sendSystemMessage(line);
        }

        LOGGER.debug("[WP GC] announcement by {} ({}) reached {} players (len={})",
                actorName, actorUuid, recipients.size(), len);

        return new SendResult.Success(recipients.size(), newExpiresAt);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Returns {@code true} iff the player's WP role attachment is at least
     * {@link #REQUIRED_SENDER_ROLE}. Visible for tests and for
     * {@link ChatScopeFilter}.
     */
    public static boolean hasRequiredRole(ServerPlayer player) {
        Role role = player.getData(WpAttachmentTypes.ROLE.get());
        return role.atLeast(REQUIRED_SENDER_ROLE);
    }

    private long lookupCooldownExpiry(String uuid) {
        return database.inTx(conn -> cooldownsDao.findByKey(conn, uuid, COOLDOWN_TYPE)
                .map(CooldownsDao.Cooldown::expiresAt)
                .orElse(0L));
    }

    private boolean isOnCooldown(String uuid, long nowMillis) {
        return database.inTx(conn -> cooldownsDao.isActive(conn, uuid, COOLDOWN_TYPE, nowMillis));
    }

    private static String displayName(ServerPlayer player) {
        Optional<RpName> rp = player.getData(WpAttachmentTypes.RP_NAME.get());
        return rp.map(RpName::fullName).orElseGet(() -> player.getGameProfile().getName());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Result types
    // ═══════════════════════════════════════════════════════════════════════════

    public sealed interface SendResult {
        record Success(int recipientCount, long expiresAtMillis) implements SendResult {}
        record InsufficientRole() implements SendResult {}
        record CooldownActive(long remainingMillis) implements SendResult {}
        record EmptyMessage() implements SendResult {}
        record NoRecipients() implements SendResult {}
        record PersistenceFailure(Throwable cause) implements SendResult {}
    }
}
