package com.disastermesh;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Strategy interface for candidate relay selection in disaster mesh networks.
 * Enables repeatable scientific comparison between baseline routing approaches
 * and the proposed dual-trajectory predictive handover mechanism.
 */
public interface RelaySelectionStrategy {

    String getStrategyName();

    String getDescription();

    /**
     * Selects the most eligible candidate relay among available neighbors.
     *
     * @param candidates available network nodes
     * @param currentRelayId ID of current relay to exclude from replacements
     * @param sourcePosition position of source/forwarding node
     * @param destinationPosition optional destination position for directional routing
     * @param timeToUnsuitable battery unsuitability predictions (minutes)
     * @param timeToDisconnect link disconnection predictions (minutes)
     * @param communicationRange RF range threshold
     * @param minimumBattery battery threshold percentage
     * @return selected relay, or empty if no candidate qualifies
     */
    Optional<NetworkNode> selectRelay(
            List<NetworkNode> candidates,
            String currentRelayId,
            Position sourcePosition,
            Position destinationPosition,
            Map<String, Double> timeToUnsuitable,
            Map<String, Double> timeToDisconnect,
            double communicationRange,
            double minimumBattery
    );
}
