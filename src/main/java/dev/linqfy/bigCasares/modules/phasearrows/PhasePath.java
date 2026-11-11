package dev.linqfy.bigCasares.modules.phasearrows;

import java.util.OptionalDouble;
import java.util.function.DoubleFunction;

/** Finds where a projectile comes out after crossing a wall along its flight line. */
final class PhasePath {

    enum Cell {
        /** Air, water, grass: the projectile can fly here. */
        OPEN,
        /** A block the projectile can phase through. */
        SOLID,
        /** A block nothing phases through (bedrock, barriers): the hit happens normally. */
        BLOCKED
    }

    static final double STEP = 0.2;
    /** Distance past the wall's far side, so the projectile does not collide with it again. */
    static final double CLEARANCE = 0.35;

    private PhasePath() {
    }

    /**
     * Walks from the hit point along the flight line.
     *
     * @param cellAt       what is at the given distance from the hit point
     * @param maxThickness thickest wall (in blocks) that can be crossed
     * @return distance from the hit point to a safe exit, or empty when the wall is too thick, unphaseable
     *     or there is nothing solid ahead
     */
    static OptionalDouble exitDistance(DoubleFunction<Cell> cellAt, double maxThickness) {
        if (maxThickness <= 0.0) {
            return OptionalDouble.empty();
        }
        double entered = -1.0;
        // Look a little ahead first: the hit point sits just in front of the block face.
        double limit = 1.0 + maxThickness + CLEARANCE;
        for (double distance = 0.0; distance <= limit; distance += STEP) {
            Cell cell = cellAt.apply(distance);
            if (cell == Cell.BLOCKED) {
                return OptionalDouble.empty();
            }
            if (cell == Cell.SOLID) {
                if (entered < 0.0) {
                    entered = distance;
                }
            } else if (entered >= 0.0) {
                if (distance - entered > maxThickness + STEP) {
                    return OptionalDouble.empty();
                }
                double exit = distance + CLEARANCE;
                return cellAt.apply(exit) == Cell.OPEN ? OptionalDouble.of(exit) : OptionalDouble.empty();
            }
        }
        return OptionalDouble.empty();
    }
}
