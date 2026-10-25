package com.disastermesh;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Baseline 3: Mobility-Only Relay Selection.
 * Selects candidate with greatest predicted link stability / disconnection horizon,
 * completely ignoring battery depletion rate.
 */
public class MobilityOnlyStrategy implements RelaySelectionStrategy {

    @Override
    public String getStrategyName() {
        return RelayStrategyType.MOBILITY_ONLY_BASELINE.getTitle();
    }

    @Override
    public String getDescription() {
        return RelayStrategyType.MOBILITY_ONLY_BASELINE.getDescription();
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

        if (candidates == null || sourcePosition == null || timeToDisconnect == null) {
            return Optional.empty();
        }

        return candidates.stream()
                .filter(node -> node != null && node.isActive())
                .filter(node -> !node.getNodeId().equals(currentRelayId))
                .filter(node -> !node.getNodeId().equals("N1") && !node.getNodeId().equals("N8"))
                .filter(node -> sourcePosition.distanceTo(node.getPosition()) <= communicationRange)
                .filter(node -> timeToDisconnect.containsKey(node.getNodeId()))
                .max(Comparator.comparingDouble(node -> {
                    Double t = timeToDisconnect.get(node.getNodeId());
                    return (t != null && !Double.isNaN(t)) ? t : 0.0;
                }));
    }
}
