package com.disastermesh;

import java.time.Instant;

/**
 * Event object published during simulation lifecycle.
 */
public record SimulationEvent(
        long eventId,
        int step,
        double timestampSeconds,
        SimulationEventType type,
        String sourceNodeId,
        String targetNodeId,
        String description,
        double riskScore
) {
    public static SimulationEvent of(long id, int step, double time, SimulationEventType type, String desc) {
        return new SimulationEvent(id, step, time, type, null, null, desc, 0.0);
    }

    public static SimulationEvent of(long id, int step, double time, SimulationEventType type, String src, String tgt, String desc, double risk) {
        return new SimulationEvent(id, step, time, type, src, tgt, desc, risk);
    }
}
