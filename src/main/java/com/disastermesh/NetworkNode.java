package com.disastermesh;

public class NetworkNode {

    private final String nodeId;
    private double batteryLevel;
    private Position position;
    private boolean active;

    public NetworkNode(
            String nodeId,
            double batteryLevel,
            Position position) {

        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException(
                    "Node ID cannot be empty.");
        }

        validateBattery(batteryLevel);

        if (position == null) {
            throw new IllegalArgumentException(
                    "Position cannot be null.");
        }

        this.nodeId = nodeId;
        this.batteryLevel = batteryLevel;
        this.position = position;
        this.active = batteryLevel > 0;
    }

    private void validateBattery(double battery) {
        if (!Double.isFinite(battery)
                || battery < 0
                || battery > 100) {

            throw new IllegalArgumentException(
                    "Battery must be between 0 and 100.");
        }
    }

    public String getNodeId() {
        return nodeId;
    }

    public double getBatteryLevel() {
        return batteryLevel;
    }

    public Position getPosition() {
        return position;
    }

    public boolean isActive() {
        return active;
    }

    public void updateBattery(double newBatteryLevel) {
        validateBattery(newBatteryLevel);

        batteryLevel = newBatteryLevel;
        active = batteryLevel > 0;
    }

    public void updatePosition(Position newPosition) {
        if (newPosition == null) {
            throw new IllegalArgumentException(
                    "Position cannot be null.");
        }

        position = newPosition;
    }

    @Override
    public String toString() {
        return String.format(
                "%s | Battery: %.1f%% | Position: (%.1f, %.1f) | Active: %s",
                nodeId,
                batteryLevel,
                position.x(),
                position.y(),
                active
        );
    }
}