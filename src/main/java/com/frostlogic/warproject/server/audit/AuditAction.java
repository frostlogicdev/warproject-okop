package com.frostlogic.warproject.server.audit;

/**
 * Canonical enumeration of audit actions persisted in the {@code audit_log.action} column.
 * <p>
 * Each enum constant's {@link #name()} value is the exact string written to the database,
 * so renaming a constant is a breaking schema change. New audit events MUST be added as
 * new constants — never reuse existing identifiers for different semantics.
 * <p>
 * The set mirrors the events listed in <strong>Requirement 20.3</strong> and the
 * action-key conventions referenced throughout {@code design.md} §13.1.
 *
 * <h2>Categories</h2>
 * <ul>
 *   <li><b>Authentication / captcha</b>: {@link #REGISTER}, {@link #LOGIN_FAIL},
 *       {@link #CAPTCHA_OK}, {@link #CAPTCHA_FAIL}.</li>
 *   <li><b>Faction lifecycle</b>: {@link #CHOOSE_FACTION},
 *       {@link #CHOOSE_FACTION_REJECTED_INVENTORY}, {@link #ACCEPT},
 *       {@link #ACCEPT_REJECTED_ALREADY_ACCEPTED}.</li>
 *   <li><b>Ranks</b>: {@link #RANK_UP}, {@link #RANK_DOWN}.</li>
 *   <li><b>Moderation</b>: {@link #BAN}, {@link #KICK}, {@link #MUTE},
 *       {@link #UNBAN}, {@link #UNMUTE}, {@link #WARN}.</li>
 *   <li><b>Captivity</b>: {@link #CAPTURE_PASSPORT}, {@link #RANSOM_COMPLETE}.</li>
 *   <li><b>Collaborators</b>: {@link #COLLAB}, {@link #UNCOLLAB}.</li>
 *   <li><b>Subdivisions</b>: {@link #SUBDIV_CREATE}, {@link #SUBDIV_DELETE},
 *       {@link #SUBDIV_INVITE}, {@link #SUBDIV_KICK}.</li>
 *   <li><b>Server</b>: {@link #RELOAD}, {@link #GENERALCHAT_SEND}.</li>
 *   <li><b>RP names</b>: {@link #RPNAME_SET}, {@link #RPNAME_OVERRIDE}.</li>
 *   <li><b>Infrastructure</b>: {@link #DB_UNAVAILABLE} (file-fallback marker per §13.2).</li>
 * </ul>
 *
 * <p>Requirements: 20.1, 20.2, 20.3, 20.4
 * <br>Design: §13.1
 */
public enum AuditAction {
    /** WGuard registration succeeded; password hash committed to {@code accounts}. */
    REGISTER,
    /** Login attempt failed (wrong password or expired session). Never includes the password. */
    LOGIN_FAIL,
    /** Captcha round completed successfully. The active code is never written to the entry. */
    CAPTCHA_OK,
    /** Captcha round failed (3 wrong attempts or 3-minute timeout). The active code is never written. */
    CAPTCHA_FAIL,
    /** Player chose a faction via NPC interaction; passport issued atomically. */
    CHOOSE_FACTION,
    /**
     * Faction-choice transaction rolled back because the passport could not be placed
     * (inventory full). Recorded in a separate transaction per design §13.3.
     */
    CHOOSE_FACTION_REJECTED_INVENTORY,
    /** Candidate accepted into faction (radial menu or {@code /wp accept}). */
    ACCEPT,
    /**
     * Acceptance attempted on an already-accepted target; no state change.
     * Diagnostic record per design §8.7 / §13.4.
     */
    ACCEPT_REJECTED_ALREADY_ACCEPTED,
    /** Rank promoted ({@code /wp up} above current rank). */
    RANK_UP,
    /** Rank demoted ({@code /wp up} below current rank). */
    RANK_DOWN,
    /** {@code /wp ban}. */
    BAN,
    /** {@code /wp kick}. */
    KICK,
    /** {@code /wp mute}. */
    MUTE,
    /** {@code /wp unban}. */
    UNBAN,
    /** {@code /wp unmute}. */
    UNMUTE,
    /** {@code /wp warn}. */
    WARN,
    /** Passport captured from an enemy; trophy flag set. */
    CAPTURE_PASSPORT,
    /** Ransom completed via {@code RansomTradeMenu}; passport returned to its owner. */
    RANSOM_COMPLETE,
    /** Collaborator flag set ({@code /wp collab} or auto-detection). */
    COLLAB,
    /** Collaborator flag cleared ({@code /wp uncollab}). */
    UNCOLLAB,
    /** Subdivision created. */
    SUBDIV_CREATE,
    /** Subdivision deleted. */
    SUBDIV_DELETE,
    /** Player invited into a subdivision. */
    SUBDIV_INVITE,
    /** Player removed from a subdivision. */
    SUBDIV_KICK,
    /** {@code /wp reload} executed; configuration re-read without DB reconnection. */
    RELOAD,
    /** GeneralChat message broadcast to commander/general/op recipients. */
    GENERALCHAT_SEND,
    /** RP name set by the player via {@code /wp rpname}. */
    RPNAME_SET,
    /** RP name overridden by an admin via {@code /wp rpname <player> ...}. */
    RPNAME_OVERRIDE,
    /**
     * Database unavailability marker written into the file-fallback log when a
     * critical operation could not commit (design §13.2).
     */
    DB_UNAVAILABLE
}
