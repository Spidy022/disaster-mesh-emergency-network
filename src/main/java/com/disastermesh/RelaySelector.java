package com.disastermesh;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Selects the most suitable replacement relay using pluggable routing strategies.
 * Defaults to the proposed Dual-Trajectory Predictive mechanism.
 */
public class RelaySelector {

    private final RiskCalculator riskCalculator;
    private final double minimumBattery;
    private final double communicationRange;
    private RelaySelectionStrategy strategy;

    public RelaySelector(
            RiskCalculator riskCalculator,
            double minimumBattery,
            double communicationRange) {

        if (riskCalculator == null) {
            throw new IllegalArgumentException("RiskCalculator cannot be null.");
        }

        if (!Double.isFinite(minimumBattery) || minimumBattery < 0 || minimumBattery > 100) {
            throw new IllegalArgumentException("Minimum battery must be between 0 and 100.");
        }

        if (!Double.isFinite(communicationRange) || communicationRange <= 0) {
            throw new IllegalArgumentException("Communication range must be positive.");
        }

        this.riskCalculator = riskCalculator;
        this.minimumBattery = minimumBattery;
        this.communicationRange = communicationRange;
        this.strategy = new DualRiskPredictiveStrategy(riskCalculator);
    }

    public RelaySelector(
            RelaySelectionStrategy strategy,
            RiskCalculator riskCalculator,
            double minimumBattery,
            double communicationRange) {

        this(riskCalculator, minimumBattery, communicationRange);
        if (strategy != null) {
            this.strategy = strategy;
        }
    }

    /**
     * Selects the eligible relay according to the active strategy.
     */
    public Optional<NetworkNode> selectRelay(
            List<NetworkNode> candidates,
            String currentRelayId,
            Position sourcePosition,
            Map<String, Double> timeToUnsuitable,
            Map<String, Double> timeToDisconnect) {

        return selectRelay(candidates, currentRelayId, sourcePosition, null, timeToUnsuitable, timeToDisconnect);
    }

    /**
     * Selects the eligible relay with destination awareness for directional progress.
     */
    public Optional<NetworkNode> selectRelay(
            List<NetworkNode> candidates,
            String currentRelayId,
            Position sourcePosition,
            Position destinationPosition,
            Map<String, Double> timeToUnsuitable,
            Map<String, Double> timeToDisconnect) {

        if (candidates == null || sourcePosition == null || timeToUnsuitable == null || timeToDisconnect == null) {
            throw new IllegalArgumentException("Selection inputs cannot be null.");
        }

        return strategy.selectRelay(
                candidates,
                currentRelayId,
                sourcePosition,
                destinationPosition,
                timeToUnsuitable,
                timeToDisconnect,
                communicationRange,
                minimumBattery
        );
    }

    public void setStrategy(RelaySelectionStrategy strategy) {
        if (strategy == null) {
            throw new IllegalArgumentException("Strategy cannot be null.");
        }
        this.strategy = strategy;
    }

    public RelaySelectionStrategy getStrategy() {
        return strategy;
    }

    public RiskCalculator getRiskCalculator() {
        return riskCalculator;
    }

    public double getMinimumBattery() {
        return minimumBattery;
    }

    public double getCommunicationRange() {
        return communicationRange;
    }
}
