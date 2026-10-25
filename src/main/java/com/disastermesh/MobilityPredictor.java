package com.disastermesh;

/**
 * Estimates when a moving network node will leave
 * the communication range of another node.
 *
 * Assumptions:
 * 1. Both nodes move with constant velocity.
 * 2. Positions and velocities use consistent units.
 * 3. The communication range is circular.
 */
public class MobilityPredictor {

    private static final double EPSILON = 1.0e-9;

    /**
     * Calculates time until a node reaches or crosses
     * the communication boundary.
     *
     * @param currentPosition current position of the node
     * @param velocityX velocity along the X axis
     * @param velocityY velocity along the Y axis
     * @param relayPosition fixed position of the reference relay
     * @param communicationRange maximum communication distance
     * @return estimated time to leave the communication range;
     *         positive infinity if no exit is predicted
     */
    public double predictTimeToDisconnect(
            Position currentPosition,
            double velocityX,
            double velocityY,
            Position relayPosition,
            double communicationRange) {

        return predictTimeToDisconnect(
                currentPosition, velocityX, velocityY,
                relayPosition, 0.0, 0.0, communicationRange);
    }

    /**
     * Predicts disconnect time using relative motion of both nodes.
     * Velocities use coordinate units per second; result is seconds.
     */
    public double predictTimeToDisconnect(
            Position currentPosition,
            double velocityX,
            double velocityY,
            Position relayPosition,
            double relayVelocityX,
            double relayVelocityY,
            double communicationRange) {

        validateInputs(
                currentPosition,
                velocityX,
                velocityY,
                relayPosition,
                communicationRange
        );

        if (!Double.isFinite(relayVelocityX)
                || !Double.isFinite(relayVelocityY)) {
            throw new IllegalArgumentException(
                    "Relay velocities must be finite.");
        }

        double dx = currentPosition.x() - relayPosition.x();
        double dy = currentPosition.y() - relayPosition.y();

        // Predict separation using candidate velocity relative to relay velocity.
        velocityX -= relayVelocityX;
        velocityY -= relayVelocityY;

        double distanceSquared = dx * dx + dy * dy;
        double rangeSquared =
                communicationRange * communicationRange;

        double speedSquared =
                velocityX * velocityX + velocityY * velocityY;

        // A stationary node will not leave the range.
        if (speedSquared <= EPSILON) {
            return Double.POSITIVE_INFINITY;
        }

        // The node is already outside the communication range.
        if (distanceSquared > rangeSquared + EPSILON) {
            return 0.0;
        }

        /*
         * Relative position:
         * r(t) = (dx + velocityX * t, dy + velocityY * t)
         *
         * Disconnect boundary:
         * |r(t)|^2 = communicationRange^2
         *
         * This produces:
         * a*t^2 + b*t + c = 0
         */
        double a = speedSquared;

        double b = 2.0 * (
                dx * velocityX + dy * velocityY
        );

        double c = distanceSquared - rangeSquared;

        double discriminant = b * b - 4.0 * a * c;

        if (discriminant < 0) {
            return Double.POSITIVE_INFINITY;
        }

        double sqrtDiscriminant = Math.sqrt(discriminant);

        double t1 = (-b - sqrtDiscriminant) / (2.0 * a);
        double t2 = (-b + sqrtDiscriminant) / (2.0 * a);

        double result = Double.POSITIVE_INFINITY;

        if (t1 >= 0.0) {
            result = t1;
        }

        if (t2 >= 0.0 && t2 < result) {
            result = t2;
        }

        // Ignore a boundary contact at the current instant
        // when the node is moving inward and will remain connected.
        if (result <= EPSILON && c < 0.0 && b < 0.0) {
            double futureExit = t2;

            if (futureExit > EPSILON) {
                return futureExit;
            }

            return Double.POSITIVE_INFINITY;
        }

        return result;
    }

    private void validateInputs(
            Position currentPosition,
            double velocityX,
            double velocityY,
            Position relayPosition,
            double communicationRange) {

        if (currentPosition == null || relayPosition == null) {
            throw new IllegalArgumentException(
                    "Positions cannot be null."
            );
        }

        if (!Double.isFinite(velocityX)
                || !Double.isFinite(velocityY)) {

            throw new IllegalArgumentException(
                    "Velocities must be finite."
            );
        }

        if (!Double.isFinite(communicationRange)
                || communicationRange <= 0.0) {

            throw new IllegalArgumentException(
                    "Communication range must be positive and finite."
            );
        }
    }
}