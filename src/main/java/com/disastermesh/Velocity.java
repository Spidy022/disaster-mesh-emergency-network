package com.disastermesh;

/**
 * Velocity vector expressed in coordinate units per second.
 */
public record Velocity(double x, double y) {

    public Velocity {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException(
                    "Velocity components must be finite.");
        }
    }

    public double speed() {
        return Math.sqrt(x * x + y * y);
    }
}