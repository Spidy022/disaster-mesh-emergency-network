package com.disastermesh;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Manages predictive and reactive relay handover decisions in the disaster mesh.
 * Implements hysteresis / minimum dwell time to suppress ping-pong switching,
 * tracks an audit trail of handover records, and protects message forwarding continuity.
 */
public class HandoverManager {

    private final RelaySelector relaySelector;
    private final double riskThreshold;
    private final int minDwellSteps;

    private NetworkNode currentRelay;
    private int handoverCount;
    private boolean lastAttemptFailed;
    private int lastHandoverStep;
    private final List<HandoverRecord> handoverHistory;
    private long nextHandoverId = 1;

    public HandoverManager(
            NetworkNode initialRelay,
            RelaySelector relaySelector,
            double riskThreshold) {

        this(initialRelay, relaySelector, riskThreshold, 2);
    }

    public HandoverManager(
            NetworkNode initialRelay,
            RelaySelector relaySelector,
            double riskThreshold,
            int minDwellSteps) {

        if (initialRelay == null || relaySelector == null) {
            throw new IllegalArgumentException("Initial relay and selector cannot be null.");
        }

        if (!Double.isFinite(riskThreshold) || riskThreshold < 0 || riskThreshold > 1) {
            throw new IllegalArgumentException("Risk threshold must be between 0 and 1.");
        }

        this.currentRelay = initialRelay;
        this.relaySelector = relaySelector;
        this.riskThreshold = riskThreshold;
        this.minDwellSteps = Math.max(0, minDwellSteps);
        this.handoverCount = 0;
        this.lastAttemptFailed = false;
        this.lastHandoverStep = -minDwellSteps;
        this.handoverHistory = new ArrayList<>();
    }

    /**
     * Backward-compatible evaluation signature.
     */
    public boolean evaluateAndHandover(
            List<NetworkNode> candidates,
            Position sourcePosition,
            Map<String, Double> timeToUnsuitable,
            Map<String, Double> timeToDisconnect,
            double currentRelayRisk) {

        return evaluateAndHandover(
                candidates,
                sourcePosition,
                null,
                timeToUnsuitable,
                timeToDisconnect,
                currentRelayRisk,
                0,
                0.0,
                0
        );
    }

    /**
     * Full evaluation supporting hysteresis, step, simulation time, destination position,
     * and affected messages.
     */
    public boolean evaluateAndHandover(
            List<NetworkNode> candidates,
            Position sourcePosition,
            Position destinationPosition,
            Map<String, Double> timeToUnsuitable,
            Map<String, Double> timeToDisconnect,
            double currentRelayRisk,
            int currentStep,
            double currentSimTimeSeconds,
            int activeMessagesCount) {

        if (!Double.isFinite(currentRelayRisk) || currentRelayRisk < 0 || currentRelayRisk > 1) {
            throw new IllegalArgumentException("Current relay risk must be between 0 and 1.");
        }

        boolean emergencyTrigger = !currentRelay.isActive() || currentRelay.getBatteryLevel() <= relaySelector.getMinimumBattery();
        boolean riskTrigger = currentRelayRisk >= riskThreshold;

        if (!emergencyTrigger && !riskTrigger) {
            lastAttemptFailed = false;
            return false;
        }

        // Hysteresis: enforce minimum dwell time unless the current relay is critically dead
        if (!emergencyTrigger && (currentStep - lastHandoverStep < minDwellSteps)) {
            return false;
        }

        String triggerReason = emergencyTrigger
                ? (!currentRelay.isActive() ? "NODE_OFFLINE" : "BATTERY_CRITICAL_DEPLETION")
                : String.format("PREDICTED_RISK_THRESHOLD (risk=%.3f >= threshold=%.2f)", currentRelayRisk, riskThreshold);

        Optional<NetworkNode> replacement = relaySelector.selectRelay(
                candidates,
                currentRelay.getNodeId(),
                sourcePosition,
                destinationPosition,
                timeToUnsuitable,
                timeToDisconnect
        );

        if (replacement.isEmpty()) {
            if (!lastAttemptFailed) {
                System.out.println("HANDOVER FAILED: No eligible replacement relay.");
                handoverHistory.add(new HandoverRecord(
                        nextHandoverId++,
                        currentStep,
                        currentSimTimeSeconds,
                        currentRelay.getNodeId(),
                        "NONE",
                        triggerReason,
                        timeToUnsuitable.getOrDefault(currentRelay.getNodeId(), 0.0),
                        timeToDisconnect.getOrDefault(currentRelay.getNodeId(), 0.0),
                        currentRelayRisk,
                        activeMessagesCount,
                        false
                ));
            }
            lastAttemptFailed = true;
            return false;
        }

        lastAttemptFailed = false;
        NetworkNode nextRelay = replacement.get();
        String previousRelayId = currentRelay.getNodeId();

        currentRelay = nextRelay;
        handoverCount++;
        lastHandoverStep = currentStep;

        handoverHistory.add(new HandoverRecord(
                nextHandoverId++,
                currentStep,
                currentSimTimeSeconds,
                previousRelayId,
                nextRelay.getNodeId(),
                triggerReason,
                timeToUnsuitable.getOrDefault(previousRelayId, 0.0),
                timeToDisconnect.getOrDefault(previousRelayId, 0.0),
                currentRelayRisk,
                activeMessagesCount,
                true
        ));

        System.out.println("\n========== RELAY HANDOVER ==========");
        System.out.println("Previous relay: " + previousRelayId);
        System.out.println("Replacement relay: " + nextRelay.getNodeId());
        System.out.println("Trigger: " + triggerReason);
        System.out.println("Handover completed successfully.");
        System.out.println("====================================\n");

        return true;
    }

    public NetworkNode getCurrentRelay() {
        return currentRelay;
    }

    public void setCurrentRelay(NetworkNode node) {
        if (node != null) {
            this.currentRelay = node;
        }
    }

    public int getHandoverCount() {
        return handoverCount;
    }

    public boolean wasLastAttemptFailed() {
        return lastAttemptFailed;
    }

    public double getRiskThreshold() {
        return riskThreshold;
    }

    public int getMinDwellSteps() {
        return minDwellSteps;
    }

    public List<HandoverRecord> getHandoverHistory() {
        return Collections.unmodifiableList(handoverHistory);
    }
}
