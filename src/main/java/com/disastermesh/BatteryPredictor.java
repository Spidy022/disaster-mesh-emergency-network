package com.disastermesh;

/**
 * Predicts how long a network node can remain suitable
 * based on its current battery level and estimated drain rate.
 */
public class BatteryPredictor {

    private final double minimumRelayBattery;

    public BatteryPredictor(double minimumRelayBattery) {

        if (!Double.isFinite(minimumRelayBattery)
                || minimumRelayBattery < 0
                || minimumRelayBattery > 100) {

            throw new IllegalArgumentException(
                    "Minimum relay battery must be between 0 and 100.");
        }

        this.minimumRelayBattery = minimumRelayBattery;
    }

    /**
     * Estimates time until the node reaches the minimum
     * acceptable battery threshold.
     *
     * @param currentBattery current battery percentage
     * @param drainRate battery percentage lost per second
     * @return estimated seconds, or positive infinity if
     *         the node is not expected to reach the threshold
     */
    public double predictTimeToUnsuitable(
            double currentBattery,
            double drainRate) {

        if (!Double.isFinite(currentBattery)
                || currentBattery < 0
                || currentBattery > 100) {

            throw new IllegalArgumentException(
                    "Current battery must be between 0 and 100.");
        }

        if (!Double.isFinite(drainRate) || drainRate < 0) {
            throw new IllegalArgumentException(
                    "Drain rate must be finite and non-negative.");
        }

        if (currentBattery <= minimumRelayBattery) {
            return 0.0;
        }

        if (drainRate == 0.0) {
            return Double.POSITIVE_INFINITY;
        }

        return (currentBattery - minimumRelayBattery) / drainRate;
    }

    public double getMinimumRelayBattery() {
        return minimumRelayBattery;
    }
}