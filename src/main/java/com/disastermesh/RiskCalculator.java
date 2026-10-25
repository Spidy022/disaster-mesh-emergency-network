package com.disastermesh;

/**
 * Combines battery and mobility predictions to calculate
 * a relay's suitability risk.
 *
 * Higher risk means the relay may become unsuitable sooner.
 */
public class RiskCalculator {

    private final double batteryWeight;
    private final double mobilityWeight;
    private final double predictionHorizonMinutes;

    public RiskCalculator(
            double batteryWeight,
            double mobilityWeight,
            double predictionHorizonMinutes) {

        if (!Double.isFinite(batteryWeight)
                || !Double.isFinite(mobilityWeight)
                || batteryWeight < 0
                || mobilityWeight < 0
                || Math.abs(batteryWeight + mobilityWeight - 1.0) > 1e-9) {

            throw new IllegalArgumentException(
                    "Weights must be non-negative and sum to 1.");
        }

        if (!Double.isFinite(predictionHorizonMinutes)
                || predictionHorizonMinutes <= 0) {

            throw new IllegalArgumentException(
                    "Prediction horizon must be positive.");
        }

        this.batteryWeight = batteryWeight;
        this.mobilityWeight = mobilityWeight;
        this.predictionHorizonMinutes = predictionHorizonMinutes;
    }

    /**
     * Calculates risk using the predicted time until battery
     * unsuitability and the predicted time until disconnection.
     *
     * Both times must use the same unit: minutes.
     *
     * @return risk score between 0 and 1
     */
    public double calculateRisk(
            double timeToUnsuitableMinutes,
            double timeToDisconnectMinutes) {

        validatePrediction(timeToUnsuitableMinutes);
        validatePrediction(timeToDisconnectMinutes);

        double batteryRisk =
                calculateIndividualRisk(timeToUnsuitableMinutes);

        double mobilityRisk =
                calculateIndividualRisk(timeToDisconnectMinutes);

        double combinedRisk =
                batteryWeight * batteryRisk
                        + mobilityWeight * mobilityRisk;

        return Math.max(0.0, Math.min(1.0, combinedRisk));
    }

    private double calculateIndividualRisk(double predictedMinutes) {

        if (predictedMinutes == Double.POSITIVE_INFINITY) {
            return 0.0;
        }

        if (predictedMinutes == 0.0) {
            return 1.0;
        }

        return Math.max(
                0.0,
                Math.min(
                        1.0,
                        1.0 - predictedMinutes / predictionHorizonMinutes
                )
        );
    }

    private void validatePrediction(double value) {

        if (Double.isNaN(value)
                || value == Double.NEGATIVE_INFINITY
                || value < 0) {

            throw new IllegalArgumentException(
                    "Predicted times must be non-negative.");
        }
    }

    public double getBatteryWeight() {
        return batteryWeight;
    }

    public double getMobilityWeight() {
        return mobilityWeight;
    }

    public double getPredictionHorizonMinutes() {
        return predictionHorizonMinutes;
    }
}