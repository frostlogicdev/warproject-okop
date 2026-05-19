package com.frostlogic.warproject.server.audit;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Immutable description of a single row to be written to {@code audit_log}.
 * <p>
 * The record carries every column except the auto-generated {@code id}.
 * {@link AuditLogger} converts it to a {@link com.frostlogic.warproject.persistence.dao.AuditLogDao}
 * insert call (transactional path) or a JSON-serialised line in {@code logs/audit.log}
 * (non-transactional / file-fallback path).
 *
 * <h2>Field semantics (mirrors §4.1 schema)</h2>
 * <ul>
 *   <li>{@code tsUtc} — UTC epoch milliseconds of the event. The caller is responsible
 *       for choosing a clock; tests inject deterministic values.</li>
 *   <li>{@code actorUuid} / {@code actorName} — initiator of the event. Both may be
 *       {@code null} for system / console actions (e.g. auto-collab tick handler).</li>
 *   <li>{@code targetUuid} / {@code targetName} — recipient of the action. May be
 *       {@code null} for events that have no specific target (e.g. {@code RELOAD},
 *       {@code GENERALCHAT_SEND}).</li>
 *   <li>{@code action} — typed enum from {@link AuditAction}. Persisted as
 *       {@link AuditAction#name()}.</li>
 *   <li>{@code reason} — free-form, human-readable reason supplied by the operator
 *       (e.g. ban reason). Optional.</li>
 *   <li>{@code extraJson} — structured machine-readable detail (e.g. {@code {"days":7}}
 *       for a ban). Must NOT contain plaintext passwords, BCrypt hashes, or active
 *       captcha codes (Req. 20.4); {@link AuditLogger} enforces this.</li>
 * </ul>
 *
 * <p>Requirements: 20.2, 20.3
 * <br>Design: §4.1, §13.1
 */
public record AuditEntry(
        long tsUtc,
        @Nullable String actorUuid,
        @Nullable String actorName,
        @Nullable String targetUuid,
        @Nullable String targetName,
        AuditAction action,
        @Nullable String reason,
        @Nullable String extraJson
) {
    public AuditEntry {
        Objects.requireNonNull(action, "action");
    }

    /**
     * Convenience constructor that uses {@link System#currentTimeMillis()} for the
     * timestamp — primarily for production callers. Tests should pass an explicit
     * {@code tsUtc} for determinism.
     */
    public static AuditEntry now(@Nullable String actorUuid,
                                 @Nullable String actorName,
                                 @Nullable String targetUuid,
                                 @Nullable String targetName,
                                 AuditAction action,
                                 @Nullable String reason,
                                 @Nullable String extraJson) {
        return new AuditEntry(System.currentTimeMillis(),
                actorUuid, actorName, targetUuid, targetName,
                action, reason, extraJson);
    }
}
