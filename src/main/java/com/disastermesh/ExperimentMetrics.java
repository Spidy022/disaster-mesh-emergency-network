package com.disastermesh;

/**
 * Performance metrics captured during experimental benchmark runs.
 */
public record ExperimentMetrics(
        String strategyName,
        int totalMessagesGenerated,
        int totalMessagesDelivered,
        int totalMessagesFailed,
        double packetDeliveryRatio,
        double averageEndToEndDelaySteps,
        int totalHandovers,
        int routeFailures,
        double routeFailureRate,
        double routeInterruptionSteps,
        double totalBatteryEnergyDissipated,
        double executionTimeMillis
) {
    public String toCsvLine() {
        return String.format(
                "\"%s\",%d,%d,%d,%.2f%%,%.2f,%d,%d,%.2f%%,%.2f,%.2f,%.1f ms",
                strategyName,
                totalMessagesGenerated,
                totalMessagesDelivered,
                totalMessagesFailed,
                packetDeliveryRatio * 100.0,
                averageEndToEndDelaySteps,
                totalHandovers,
                routeFailures,
                routeFailureRate * 100.0,
                routeInterruptionSteps,
                totalBatteryEnergyDissipated,
                executionTimeMillis
        );
    }

    public static String csvHeader() {
        return "Strategy,Messages Generated,Delivered,Failed,PDR,Avg Delay (Steps),Handovers,Route Failures,Route Failure Rate,Interruption (Steps),Energy Dissipated (%),Run Time";
    }
}
