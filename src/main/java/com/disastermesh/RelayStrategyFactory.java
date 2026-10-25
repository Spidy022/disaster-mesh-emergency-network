package com.disastermesh;

/**
 * Factory for creating relay selection strategies.
 * Implements the Factory Design Pattern to decouple simulation configuration
 * from concrete routing algorithm instantiations.
 */
public final class RelayStrategyFactory {

    private RelayStrategyFactory() {
    }

    public static RelaySelectionStrategy createStrategy(RelayStrategyType type, RiskCalculator riskCalculator) {
        if (type == null) {
            type = RelayStrategyType.DUAL_TRAJECTORY_PREDICTIVE;
        }

        return switch (type) {
            case DUAL_TRAJECTORY_PREDICTIVE -> new DualRiskPredictiveStrategy(riskCalculator);
            case SHORTEST_PATH_BASELINE -> new ShortestPathStrategy();
            case BATTERY_ONLY_BASELINE -> new BatteryOnlyStrategy();
            case MOBILITY_ONLY_BASELINE -> new MobilityOnlyStrategy();
        };
    }
}
