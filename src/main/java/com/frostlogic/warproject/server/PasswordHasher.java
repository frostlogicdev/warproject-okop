package com.frostlogic.warproject.server;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * SHA-256 + per-account salt password hashing.
 * <p>
 * Salts are 16 random bytes generated with {@link SecureRandom}; both the salt
 * and the resulting digest are stored as Base64 strings in the player profile.
 * This is good enough for an in-game registration flow where the network is
 * already TLS-protected by the Mojang session and we just want to keep raw
 * passwords out of the persisted JSON (so an admin reading playerdata can't
 * trivially see them).
 */
public final class PasswordHasher {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int SALT_BYTES = 16;

    private PasswordHasher() {
    }

    /** Generates a new random salt encoded as Base64 (no padding stripped). */
    public static String newSalt() {
        byte[] bytes = new byte[SALT_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    /** Returns Base64(SHA-256(saltBytes || passwordUtf8)). */
    public static String hash(String password, String saltBase64) {
        if (password == null || saltBase64 == null) {
            throw new IllegalArgumentException("password and salt must be non-null");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(Base64.getDecoder().decode(saltBase64));
            digest.update(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable on this JVM", e);
        }
    }

    /** Constant-time comparison of two hashes to avoid timing side channels. */
    public static boolean verify(String password, String saltBase64, String expectedHashBase64) {
        if (password == null || saltBase64 == null || expectedHashBase64 == null) {
            return false;
        }
        String actual = hash(password, saltBase64);
        return MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.UTF_8),
                expectedHashBase64.getBytes(StandardCharsets.UTF_8));
    }
}
