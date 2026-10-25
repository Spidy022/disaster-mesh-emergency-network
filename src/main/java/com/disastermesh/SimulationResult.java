package com.disastermesh;

/**
 * Immutable record of one simulation step.
 */
public record SimulationResult(
        int step,
        double simulationTimeSeconds,
        String currentRelayId,
        double relayBattery,
        double relayRisk,
        int handoverCount,
        String event) {
}