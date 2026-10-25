package com.disastermesh;

/**
 * Stores configurable simulation parameters.
 */
public record SimulationConfig(
        int totalSteps,
        double timeStepSeconds,
        double communicationRange,
        double minimumRelayBattery,
        double handoverRiskThreshold,
        double batteryWeight,
        double mobilityWeight,
        double predictionHorizonMinutes) {

    public SimulationConfig {

        if (totalSteps <= 0) {
            throw new IllegalArgumentException(
                    "Total steps must be positive.");
        }

        if (!Double.isFinite(timeStepSeconds)
                || timeStepSeconds <= 0) {
            throw new IllegalArgumentException(
                    "Time step must be positive.");
        }

        if (!Double.isFinite(communicationRange)
                || communicationRange <= 0) {
            throw new IllegalArgumentException(
                    "Communication range must be positive.");
        }

        if (!Double.isFinite(minimumRelayBattery)
                || minimumRelayBattery < 0
                || minimumRelayBattery > 100) {
            throw new IllegalArgumentException(
                    "Minimum battery must be between 0 and 100.");
        }

        if (!Double.isFinite(handoverRiskThreshold)
                || handoverRiskThreshold < 0
                || handoverRiskThreshold > 1) {
            throw new IllegalArgumentException(
                    "Handover threshold must be between 0 and 1.");
        }

        if (!Double.isFinite(batteryWeight)
                || !Double.isFinite(mobilityWeight)
                || batteryWeight < 0
                || mobilityWeight < 0
                || Math.abs(
                        batteryWeight + mobilityWeight - 1.0) > 1e-9) {
            throw new IllegalArgumentException(
                    "Risk weights must be non-negative and sum to 1.");
        }

        if (!Double.isFinite(predictionHorizonMinutes)
                || predictionHorizonMinutes <= 0) {
            throw new IllegalArgumentException(
                    "Prediction horizon must be positive.");
        }
    }

    public static SimulationConfig defaultConfig() {
        return new SimulationConfig(
                300,
                1.0,
                50.0,
                20.0,
                0.40,
                0.50,
                0.50,
                2.0
        );
    }

    public static SimulationConfig defaultWebConfig() {
        return new SimulationConfig(
                600,
                1.0,
                38.0,
                20.0,
                0.40,
                0.50,
                0.50,
                2.0
        );
    }
}
