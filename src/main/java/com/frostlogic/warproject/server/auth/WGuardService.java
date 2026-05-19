package com.frostlogic.warproject.server.auth;

import com.frostlogic.warproject.WpConfig;
import com.frostlogic.warproject.attachment.PlayerState;
import com.frostlogic.warproject.attachment.WpAttachmentTypes;
import com.frostlogic.warproject.persistence.Database;
import com.frostlogic.warproject.persistence.dao.AccountsDao;
import com.frostlogic.warproject.persistence.dao.CooldownsDao;
import com.frostlogic.warproject.persistence.dao.PlayersDao;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Main authentication service facade for the WGuard subsystem.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Validate registration input (password match + complexity)</li>
 *   <li>Register a new account (BCrypt hash, DB insert, state transition)</li>
 *   <li>Log in an existing account (BCrypt verify, cooldown/attempt tracking)</li>
 * </ul>
 * <p>
 * This service is server-authoritative: all decisions are made here,
 * never on the client. Passwords are handled as {@code char[]} and zeroed
 * after use. No password content is ever logged.
 * <p>
 * Requirements: 2.3, 2.4, 2.5, 3.3, 3.4
 * Design: §3, §6
 */
public final class WGuardService {

    private static final Logger LOGGER = LogUtils.getLogger();
    /**
     * Production BCrypt work factor for cracked-server passwords.
     * Keep this aligned with docs/PRODUCTION_READY.md and docs/DEPLOY.md.
     */
    private static final int BCRYPT_COST = 12;
    private static final String COOLDOWN_TYPE_LOGIN = "LOGIN";

    private final Database database;
    private final AccountsDao accountsDao;
    private final PlayersDao playersDao;
    private final CooldownsDao cooldownsDao;
    private final LoginAttemptTracker loginAttemptTracker;

    public WGuardService(Database database,
                         AccountsDao accountsDao,
                         PlayersDao playersDao,
                         CooldownsDao cooldownsDao,
                         LoginAttemptTracker loginAttemptTracker) {
        this.database = database;
        this.accountsDao = accountsDao;
        this.playersDao = playersDao;
        this.cooldownsDao = cooldownsDao;
        this.loginAttemptTracker = loginAttemptTracker;
    }

    /**
     * Validates a registration form submission without side effects.
     * <p>
     * Checks:
     * <ol>
     *   <li>Passwords match (char-by-char comparison)</li>
     *   <li>Password meets complexity requirements from {@code cfg}:
     *       length within [minLen, maxLen] and all characters match allowedChars</li>
     * </ol>
     * <p>
     * The input arrays are <strong>not</strong> zeroed by this method — the caller
     * retains ownership and is responsible for cleanup.
     *
     * @param pwd     the password entered by the player
     * @param confirm the confirmation password
     * @param cfg     authentication configuration snapshot
     * @return {@link AuthResult.Ok} if valid, {@link AuthResult.Error} with i18n key otherwise
     */
    public static AuthResult validateRegistration(char[] pwd, char[] confirm, AuthCfg cfg) {
        // 1. Check passwords match
        if (!Arrays.equals(pwd, confirm)) {
            return new AuthResult.Error("wp.auth.error.passwords_mismatch");
        }

        // 2. Check length constraints
        if (pwd.length < cfg.minLen()) {
            return new AuthResult.Error("wp.auth.error.password_too_short");
        }
        if (pwd.length > cfg.maxLen()) {
            return new AuthResult.Error("wp.auth.error.password_too_long");
        }

        // 3. Check allowed characters
        Pattern allowedPattern = Pattern.compile("^" + cfg.allowedChars() + "+$");
        String pwdStr = new String(pwd);
        try {
            if (!allowedPattern.matcher(pwdStr).matches()) {
                return new AuthResult.Error("wp.auth.error.password_invalid_chars");
            }
        } finally {
            // Clear the temporary string reference (best-effort; JVM may retain interned copy)
            pwdStr = null;
        }

        return new AuthResult.Ok();
    }

    /**
     * Registers a new player account.
     * <p>
     * Preconditions (checked by caller / packet handler):
     * <ul>
     *   <li>Player is in state {@link PlayerState#NEW}</li>
     *   <li>No existing account in DB for this UUID</li>
     *   <li>Password has been validated via {@link #validateRegistration}</li>
     * </ul>
     * <p>
     * Effects:
     * <ul>
     *   <li>Hashes password with BCrypt (cost &ge; 12)</li>
     *   <li>Inserts account record into {@code accounts} table</li>
     *   <li>Inserts player record into {@code players} table with status {@code REGISTERED_PENDING}</li>
     *   <li>Transitions player attachment to {@link PlayerState#REGISTERED_PENDING}</li>
     * </ul>
     * <p>
     * The password array is zeroed by {@link PasswordHasher#hash} internally.
     *
     * @param player the server player registering
     * @param pwd    the validated password (will be zeroed after hashing)
     * @return {@link AuthResult.Ok} on success, {@link AuthResult.Error} if account already exists
     */
    public AuthResult register(ServerPlayer player, char[] pwd) {
        String uuid = player.getStringUUID();
        long now = System.currentTimeMillis();

        // Hash password — PasswordHasher zeroes the array
        String hash = PasswordHasher.hash(pwd, BCRYPT_COST);

        try {
            database.transaction(conn -> {
                // Check no existing account (defensive — should be checked before)
                if (accountsDao.findByUuid(conn, uuid).isPresent()) {
                    throw new AccountAlreadyExistsException(uuid);
                }

                // Insert account
                AccountsDao.Account account = new AccountsDao.Account(
                        uuid, hash, now, null, 0, 0
                );
                accountsDao.insert(conn, account);

                // Insert player record
                PlayersDao.Player playerRecord = new PlayersDao.Player(
                        uuid,
                        null,                   // faction — not yet chosen
                        "CANDIDATE",            // default role
                        null,                   // rank
                        "REGISTERED_PENDING",   // status
                        null,                   // rpName
                        null,                   // rpSurname
                        false,                  // collaborator
                        null,                   // collabReason
                        false,                  // captured
                        0,                      // enemyRegionTicks
                        now,                    // joinedAt
                        null,                   // acceptedAt
                        null,                   // acceptedByUuid
                        null,                   // acceptedByName
                        null                    // subdivisionId
                );
                playersDao.insert(conn, playerRecord);
            });

            // Update attachment state
            player.setData(WpAttachmentTypes.PLAYER_STATE.get(), PlayerState.REGISTERED_PENDING);

            // The legacy bridge caches pipeline ownership per session — invalidate
            // so the next sync correctly sees this player as new-pipeline-owned.
            try {
                com.frostlogic.warproject.server.LegacyAttachmentBridge.invalidateCache(player.getUUID());
            } catch (Throwable ignored) {}

            LOGGER.info("Player {} registered successfully", uuid);
            return new AuthResult.Ok();

        } catch (AccountAlreadyExistsException e) {
            LOGGER.warn("Registration rejected: account already exists for {}", uuid);
            return new AuthResult.Error("wp.auth.error.account_exists");
        }
    }

    /**
     * Attempts to log in a player with an existing account.
     * <p>
     * Preconditions (checked by caller / packet handler):
     * <ul>
     *   <li>Player is in state {@link PlayerState#LOGIN_PENDING}</li>
     *   <li>Account exists in DB</li>
     * </ul>
     * <p>
     * Effects on success:
     * <ul>
     *   <li>Resets failed login counter</li>
     *   <li>Updates {@code last_login_at} in DB</li>
     *   <li>Resets login attempt tracker</li>
     *   <li>Transitions player to appropriate state based on DB {@code players.status}</li>
     * </ul>
     * <p>
     * Effects on failure:
     * <ul>
     *   <li>Increments failed login counter in DB</li>
     *   <li>Records failure in {@link LoginAttemptTracker}</li>
     *   <li>May trigger cooldown or kick via tracker</li>
     * </ul>
     * <p>
     * The password array is zeroed by {@link PasswordHasher#verify} internally.
     *
     * @param player the server player attempting to log in
     * @param pwd    the password to verify (will be zeroed after verification)
     * @return {@link AuthResult.Ok} on success, {@link AuthResult.Error} on failure
     */
    public AuthResult login(ServerPlayer player, char[] pwd) {
        String uuid = player.getStringUUID();
        long now = System.currentTimeMillis();

        // Check cooldown
        if (loginAttemptTracker.isOnCooldown(player.getUUID())) {
            // Zero the password since PasswordHasher won't get to do it
            Arrays.fill(pwd, '\0');
            return new AuthResult.Error("wp.auth.error.login_cooldown");
        }

        // Fetch account from DB
        AccountsDao.Account account = database.inTx(conn ->
                accountsDao.findByUuid(conn, uuid).orElse(null)
        );

        if (account == null) {
            Arrays.fill(pwd, '\0');
            return new AuthResult.Error("wp.auth.error.no_account");
        }

        // Verify password — PasswordHasher zeroes the array
        boolean matches = PasswordHasher.verify(pwd, account.passwordHash());

        if (!matches) {
            // Record failure
            loginAttemptTracker.recordFailure(player.getUUID());

            // Update failed count in DB
            database.transaction(conn -> {
                AccountsDao.Account updated = new AccountsDao.Account(
                        account.uuid(),
                        account.passwordHash(),
                        account.registeredAt(),
                        account.lastLoginAt(),
                        account.failedLoginCnt() + 1,
                        account.cooldownUntil()
                );
                accountsDao.update(conn, updated);
            });

            LOGGER.info("Failed login attempt for player {}", uuid);
            return new AuthResult.Error("wp.auth.error.invalid_credentials");
        }

        // Success — reset counters and update last login
        loginAttemptTracker.resetFailures(player.getUUID());

        database.transaction(conn -> {
            AccountsDao.Account updated = new AccountsDao.Account(
                    account.uuid(),
                    account.passwordHash(),
                    account.registeredAt(),
                    now,
                    0,
                    0
            );
            accountsDao.update(conn, updated);
        });

        // Resolve the correct state to transition to based on players table
        PlayerState targetState = resolvePostLoginState(uuid);
        player.setData(WpAttachmentTypes.PLAYER_STATE.get(), targetState);

        LOGGER.info("Player {} logged in successfully, state -> {}", uuid, targetState);
        return new AuthResult.Ok();
    }

    /**
     * Determines the appropriate {@link PlayerState} after a successful login
     * based on the player's current status in the {@code players} table.
     */
    private PlayerState resolvePostLoginState(String uuid) {
        PlayersDao.Player playerRecord = database.inTx(conn ->
                playersDao.findByUuid(conn, uuid).orElse(null)
        );

        if (playerRecord == null) {
            // Should not happen if registration was successful, but handle gracefully
            return PlayerState.CAPTCHA;
        }

        return switch (playerRecord.status()) {
            case "REGISTERED_PENDING" -> PlayerState.CAPTCHA;
            case "CAPTCHA" -> PlayerState.CAPTCHA;
            case "RPNAME_REQUIRED" -> PlayerState.RPNAME_REQUIRED;
            case "FACTIONLESS" -> PlayerState.FACTIONLESS;
            case "CANDIDATE" -> PlayerState.CANDIDATE;
            case "ACCEPTED" -> PlayerState.ACCEPTED;
            case "CAPTURED" -> PlayerState.CAPTURED;
            default -> PlayerState.CAPTCHA;
        };
    }

    /**
     * Creates an {@link AuthCfg} snapshot from the current {@link WpConfig} values.
     */
    public static AuthCfg configFromWpConfig() {
        return new AuthCfg(
                WpConfig.AUTH_PASSWORD_MIN_LEN.get(),
                WpConfig.AUTH_PASSWORD_MAX_LEN.get(),
                WpConfig.AUTH_PASSWORD_ALLOWED_CHARS.get()
        );
    }

    /**
     * Internal exception used to signal account-already-exists within a transaction.
     */
    private static final class AccountAlreadyExistsException extends RuntimeException {
        AccountAlreadyExistsException(String uuid) {
            super("Account already exists for UUID: " + uuid);
        }
    }
}
