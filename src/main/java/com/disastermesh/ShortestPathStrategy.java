package com.disastermesh;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Baseline 1: Standard Shortest Path / Greedy Geographic Relay Selection.
 * Selects candidate closest to destination without considering battery or mobility risks.
 */
public class ShortestPathStrategy implements RelaySelectionStrategy {

    @Override
    public String getStrategyName() {
        return RelayStrategyType.SHORTEST_PATH_BASELINE.getTitle();
    }

    @Override
    public String getDescription() {
        return RelayStrategyType.SHORTEST_PATH_BASELINE.getDescription();
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

        if (candidates == null || sourcePosition == null) {
            return Optional.empty();
        }

        final Position target = destinationPosition != null ? destinationPosition : sourcePosition;

        return candidates.stream()
                .filter(node -> node != null && node.isActive())
                .filter(node -> !node.getNodeId().equals(currentRelayId))
                .filter(node -> !node.getNodeId().equals("N1") && !node.getNodeId().equals("N8"))
                .filter(node -> sourcePosition.distanceTo(node.getPosition()) <= communicationRange)
                .min(Comparator.comparingDouble(node -> node.getPosition().distanceTo(target)));
    }
}
