package com.frostlogic.warproject.server.auth;

/**
 * Represents a single captcha round for a player.
 * <p>
 * Holds the expected code, the number of failed attempts so far,
 * and the server tick at which the session started (used for timeout calculation).
 * <p>
 * This is a mutable session object stored in {@link CaptchaService}'s session map.
 * <p>
 * Requirements: 5.1–5.10
 * Design: §5.2
 */
public final class CaptchaSession {

    private final String code;
    private int attempts;
    private final long startTickServer;

    /**
     * Creates a new captcha session.
     *
     * @param code            the expected captcha code the player must enter
     * @param attempts        initial attempt count (normally 0)
     * @param startTickServer the server tick at which this session was created
     */
    public CaptchaSession(String code, int attempts, long startTickServer) {
        this.code = code;
        this.attempts = attempts;
        this.startTickServer = startTickServer;
    }

    /**
     * Returns the expected captcha code.
     */
    public String code() {
        return code;
    }

    /**
     * Returns the number of failed attempts so far.
     */
    public int attempts() {
        return attempts;
    }

    /**
     * Increments the failed attempt counter and returns the new value.
     */
    public int incrementAttempts() {
        return ++attempts;
    }

    /**
     * Returns the server tick at which this session was started.
     */
    public long startTickServer() {
        return startTickServer;
    }
}
