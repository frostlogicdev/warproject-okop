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
     *
     * @param password the plaintext password as a char array; will be zeroed after use
     * @param hash     the BCrypt hash to verify against
     * @return {@code true} if the password matches the hash
     * @throws IllegalArgumentException if {@code password} or {@code hash} is null
     */
    public static boolean verify(char[] password, String hash) {
        if (password == null) {
            throw new IllegalArgumentException("password must not be null");
        }
        if (hash == null) {
            throw new IllegalArgumentException("hash must not be null");
        }
        try {
            String pwd = new String(password);
            return BCrypt.checkpw(pwd, hash);
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
