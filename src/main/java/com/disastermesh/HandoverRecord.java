package com.disastermesh;

import java.time.Instant;

/**
 * Immutable record capturing details of a relay handover event.
 */
public record HandoverRecord(
        long handoverId,
        int step,
        double timestampSeconds,
        String previousRelayId,
        String replacementRelayId,
        String triggerReason,
        double batteryRisk,
        double mobilityRisk,
        double combinedRisk,
        int affectedMessagesCount,
        boolean successful
) {
}
