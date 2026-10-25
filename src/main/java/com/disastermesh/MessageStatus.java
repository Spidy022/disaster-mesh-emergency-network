package com.disastermesh;

/**
 * Lifecycle states of an emergency message in the disaster mesh network.
 */
public enum MessageStatus {
    CREATED,
    QUEUED,
    IN_TRANSIT,
    REROUTING,
    DELIVERED,
    FAILED
}
