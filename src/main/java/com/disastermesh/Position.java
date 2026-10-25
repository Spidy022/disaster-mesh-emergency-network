package com.disastermesh;

public record Position(double x, double y) {

    public Position {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException(
                    "Position coordinates must be finite.");
        }
    }

    public double distanceTo(Position other) {
        if (other == null) {
            throw new IllegalArgumentException(
                    "Other position cannot be null.");
        }

        double dx = this.x - other.x();
        double dy = this.y - other.y();

        return Math.hypot(dx, dy);
    }
}