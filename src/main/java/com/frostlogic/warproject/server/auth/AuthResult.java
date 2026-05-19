package com.frostlogic.warproject.server.auth;

/**
 * Sealed result type for authentication operations.
 * <p>
 * Used by {@link WGuardService#validateRegistration} and other auth methods
 * to communicate success or a localized error key back to the caller.
 */
public sealed interface AuthResult permits AuthResult.Ok, AuthResult.Error {

    /**
     * Successful result — operation completed without issues.
     */
    record Ok() implements AuthResult {}

    /**
     * Error result — operation rejected.
     *
     * @param messageKey i18n key for the error message (e.g. {@code "wp.auth.error.passwords_mismatch"})
     */
    record Error(String messageKey) implements AuthResult {}

    /**
     * Convenience check for success.
     */
    default boolean isOk() {
        return this instanceof Ok;
    }

    /**
     * Convenience check for failure.
     */
    default boolean isError() {
        return this instanceof Error;
    }
}
