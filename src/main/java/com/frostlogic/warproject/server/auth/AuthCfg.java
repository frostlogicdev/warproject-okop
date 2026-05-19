package com.frostlogic.warproject.server.auth;

/**
 * Immutable snapshot of authentication-related configuration values.
 * <p>
 * Extracted from {@link com.frostlogic.warproject.WpConfig} at call-site
 * so that validation logic remains pure and testable without NeoForge config infrastructure.
 *
 * @param minLen       minimum password length (inclusive)
 * @param maxLen       maximum password length (inclusive)
 * @param allowedChars regex character class for allowed password characters,
 *                     e.g. {@code "[A-Za-z0-9!@#$%^&*()_\\-+=]"}
 */
public record AuthCfg(
        int minLen,
        int maxLen,
        String allowedChars
) {

    public AuthCfg {
        if (minLen < 1) throw new IllegalArgumentException("minLen must be >= 1");
        if (maxLen < minLen) throw new IllegalArgumentException("maxLen must be >= minLen");
        if (allowedChars == null || allowedChars.isEmpty()) {
            throw new IllegalArgumentException("allowedChars must not be null or empty");
        }
    }
}
