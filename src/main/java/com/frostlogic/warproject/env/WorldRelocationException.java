package com.frostlogic.warproject.env;

/**
 * Thrown when the world relocation procedure fails.
 * If the failure occurred during the move phase, the world has been rolled back
 * from the zip backup to its original location.
 */
public class WorldRelocationException extends Exception {

    public WorldRelocationException(String message) {
        super(message);
    }

    public WorldRelocationException(String message, Throwable cause) {
        super(message, cause);
    }
}
