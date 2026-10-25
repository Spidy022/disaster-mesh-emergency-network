package com.disastermesh;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Proposed research contribution: Joint Dual-Trajectory Predictive Handover.
 * Evaluates both battery depletion rate and link survival horizon to minimize
 * network partitioning and packet loss before failure occurs.
 */
public class DualRiskPredictiveStrategy implements RelaySelectionStrategy {

    private final RiskCalculator riskCalculator;

    public DualRiskPredictiveStrategy(RiskCalculator riskCalculator) {
        if (riskCalculator == null) {
            throw new IllegalArgumentException("RiskCalculator cannot be null.");
        }
        this.riskCalculator = riskCalculator;
    }

    @Override
    public String getStrategyName() {
        return RelayStrategyType.DUAL_TRAJECTORY_PREDICTIVE.getTitle();
    }

    @Override
    public String getDescription() {
        return RelayStrategyType.DUAL_TRAJECTORY_PREDICTIVE.getDescription();
    }

    @Override
    public Optional<NetworkNode> selectRelay(
            List<NetworkNode> candidates,
            String currentRelayId,
            Position sourcePosition,
            Position destinationPosition,
            Map<String, Double> timeToUnsuitable,
            Map<String, Double> timeToDisconnect,
            double communicationRange,
            double minimumBattery) {

        if (candidates == null || sourcePosition == null || timeToUnsuitable == null || timeToDisconnect == null) {
            return Optional.empty();
        }

        return candidates.stream()
                .filter(node -> node != null && node.isActive())
                .filter(node -> !node.getNodeId().equals(currentRelayId))
                .filter(node -> !node.getNodeId().equals("N1") && !node.getNodeId().equals("N8"))
                .filter(node -> node.getBatteryLevel() > minimumBattery)
                .filter(node -> sourcePosition.distanceTo(node.getPosition()) <= communicationRange)
                .filter(node -> timeToUnsuitable.containsKey(node.getNodeId()) && timeToDisconnect.containsKey(node.getNodeId()))
                .filter(node -> isValidPrediction(timeToUnsuitable.get(node.getNodeId())) && isValidPrediction(timeToDisconnect.get(node.getNodeId())))
                .min(Comparator.comparingDouble(node -> {
                    double risk = riskCalculator.calculateRisk(
                            timeToUnsuitable.get(node.getNodeId()),
                            timeToDisconnect.get(node.getNodeId())
                    );
                    // Secondary tie-breaker: geographic distance to destination if known
                    double distWeight = 0.0;
                    if (destinationPosition != null) {
                        distWeight = node.getPosition().distanceTo(destinationPosition) * 0.001;
                    }
                    return risk + distWeight;
                }));
    }

    private boolean isValidPrediction(Double val) {
        return val != null && !Double.isNaN(val) && val >= 0 && val != Double.NEGATIVE_INFINITY;
    }

    public RiskCalculator getRiskCalculator() {
        return riskCalculator;
    }
}
