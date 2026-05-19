package com.frostlogic.warproject.server.event;

/**
 * Represents the lifecycle state of an event.
 * <p>
 * Requirements: 7.1, 8.1, 9.1
 * Design: §3 Event System — EventState
 */
public enum EventState {
    PENDING,
    ACTIVE,
    COMPLETED
}
