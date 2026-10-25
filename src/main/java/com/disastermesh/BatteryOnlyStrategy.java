package com.disastermesh;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Baseline 2: Battery-Only Relay Selection.
 * Selects candidate with highest remaining battery / longest battery lifespan,
 * completely ignoring mobility or link disconnection risk.
 */
public class BatteryOnlyStrategy implements RelaySelectionStrategy {

    @Override
    public String getStrategyName() {
        return RelayStrategyType.BATTERY_ONLY_BASELINE.getTitle();
    }

    @Override
    public String getDescription() {
        return RelayStrategyType.BATTERY_ONLY_BASELINE.getDescription();
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

        return candidates.stream()
                .filter(node -> node != null && node.isActive())
                .filter(node -> !node.getNodeId().equals(currentRelayId))
                .filter(node -> !node.getNodeId().equals("N1") && !node.getNodeId().equals("N8"))
                .filter(node -> node.getBatteryLevel() > minimumBattery)
                .filter(node -> sourcePosition.distanceTo(node.getPosition()) <= communicationRange)
                .max(Comparator.comparingDouble(node -> {
                    if (timeToUnsuitable != null && timeToUnsuitable.containsKey(node.getNodeId())) {
                        Double t = timeToUnsuitable.get(node.getNodeId());
                        if (t != null && !Double.isNaN(t)) {
                            return t;
                        }
                    }
                    return node.getBatteryLevel();
                }));
    }
}
