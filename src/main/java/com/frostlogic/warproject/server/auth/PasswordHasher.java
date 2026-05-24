package com.frostlogic.warproject.server.auth;

import org.mindrot.jbcrypt.BCrypt;

import java.util.Arrays;

/**
 * BCrypt password hashing utility.
 * <p>
 * Provides secure password hashing with configurable cost factor (minimum 10).
 * The {@code char[]} password is always zeroed in a {@code finally} block to
 * minimize the window during which plaintext credentials reside in memory.
 * <p>
 * No method in this class logs or exposes the password content (SP-2).
 *
 * @see <a href="https://en.wikipedia.org/wiki/Bcrypt">BCrypt</a>
 */
public final class PasswordHasher {

    private static final int MIN_COST = 10;

    private PasswordHasher() {
    }

    /**
     * Hashes the given password using BCrypt with the specified cost factor.
     *
     * @param password the plaintext password as a char array; will be zeroed after use
     * @param cost     the BCrypt cost factor; must be &ge; 10
     * @return the BCrypt hash string (e.g. {@code $2a$10$...})
     * @throws IllegalArgumentException if {@code password} is null or {@code cost < 10}
     */
    public static String hash(char[] password, int cost) {
        if (password == null) {
            throw new IllegalArgumentException("password must not be null");
        }
        if (cost < MIN_COST) {
            throw new IllegalArgumentException("cost must be >= " + MIN_COST + ", got " + cost);
        }
        try {
            String pwd = new String(password);
            String salt = BCrypt.gensalt(cost);
            return BCrypt.hashpw(pwd, salt);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    /**
     * Verifies a plaintext password against a BCrypt hash.
     * <p>
     * Defensive against malformed hashes: if {@code hash} is null, empty, or
     * does not match a BCrypt salt format (e.g. legacy placeholders such as
     * {@code OP_NO_PASSWORD}, corrupted rows, or plain text), this method
     * returns {@code false} instead of propagating
     * {@link IllegalArgumentException}. This prevents
     * {@code NetworkRegistry: Failed to process a synchronized task of the
     * payload: warproject:login_request} crashes that previously kicked the
     * player on every reconnect when the {@code accounts.password_hash} value
     * was not a real BCrypt string.
     *
     * @param password the plaintext password as a char array; will be zeroed after use
     * @param hash     the BCrypt hash to verify against
     * @return {@code true} if the password matches a valid BCrypt hash, {@code false} otherwise
     */
    public static boolean verify(char[] password, String hash) {
        if (password == null) {
            throw new IllegalArgumentException("password must not be null");
        }
        try {
            if (hash == null || !isLikelyBcryptHash(hash)) {
                // Malformed / placeholder hash — treat as mismatch rather than crash.
                return false;
            }
            String pwd = new String(password);
            try {
                return BCrypt.checkpw(pwd, hash);
            } catch (IllegalArgumentException badHash) {
                // BCrypt couldn't parse the salt — same fallback.
                return false;
            }
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    /**
     * Quick syntactic check for a BCrypt hash. Real BCrypt hashes always
     * start with {@code $2a$}, {@code $2b$} or {@code $2y$} followed by a
     * two-digit cost and {@code $}, e.g. {@code $2a$10$...}. Anything else is
     * rejected up-front (saves the BCrypt parser from throwing).
     */
    public static boolean isLikelyBcryptHash(String hash) {
        if (hash == null || hash.length() < 7) return false;
        if (hash.charAt(0) != '$' || hash.charAt(1) != '2') return false;
        char v = hash.charAt(2);
        if (v != 'a' && v != 'b' && v != 'y') return false;
        return hash.charAt(3) == '$' && hash.charAt(6) == '$';
    }
}
