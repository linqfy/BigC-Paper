package dev.linqfy.bigCasares.modules.cosmicniv;

import java.util.List;
import java.util.Objects;

/**
 * Bends the combined progress {@code t} in [0, 1] into the final fraction of the maximum level. Every curve maps
 * 0 to 0 and 1 to 1, so the zero and max references always keep their levels.
 */
public sealed interface CosmicNivCurve permits CosmicNivCurve.Linear, CosmicNivCurve.Exponential,
    CosmicNivCurve.Logarithmic, CosmicNivCurve.Power, CosmicNivCurve.Sigmoid, CosmicNivCurve.Points {

    double MAXIMUM_EXPONENTIAL_STRENGTH = 50.0;
    double MAXIMUM_LOGARITHMIC_STRENGTH = 1_000_000.0;
    double MAXIMUM_EXPONENT = 100.0;
    double MAXIMUM_SIGMOID_STRENGTH = 100.0;
    double MINIMUM_STRENGTH = 1e-6;

    CosmicNivCurveType type();

    double shape(double progress);

    default double apply(double progress) {
        if (Double.isNaN(progress) || progress <= 0.0) {
            return 0.0;
        }
        if (progress >= 1.0) {
            return 1.0;
        }
        double value = shape(progress);
        if (Double.isNaN(value)) {
            return 0.0;
        }
        return Math.clamp(value, 0.0, 1.0);
    }

    record Linear() implements CosmicNivCurve {
        @Override
        public CosmicNivCurveType type() {
            return CosmicNivCurveType.LINEAR;
        }

        @Override
        public double shape(double progress) {
            return progress;
        }
    }

    record Exponential(double strength) implements CosmicNivCurve {
        public Exponential {
            if (!Double.isFinite(strength) || Math.abs(strength) < MINIMUM_STRENGTH
                || Math.abs(strength) > MAXIMUM_EXPONENTIAL_STRENGTH) {
                throw new IllegalArgumentException("EXPONENTIAL strength must be between " + MINIMUM_STRENGTH
                    + " and " + MAXIMUM_EXPONENTIAL_STRENGTH + " in absolute value (positive or negative), got "
                    + strength);
            }
        }

        @Override
        public CosmicNivCurveType type() {
            return CosmicNivCurveType.EXPONENTIAL;
        }

        @Override
        public double shape(double progress) {
            return Math.expm1(strength * progress) / Math.expm1(strength);
        }
    }

    record Logarithmic(double strength) implements CosmicNivCurve {
        public Logarithmic {
            if (!Double.isFinite(strength) || strength < MINIMUM_STRENGTH || strength > MAXIMUM_LOGARITHMIC_STRENGTH) {
                throw new IllegalArgumentException("LOGARITHMIC strength must be between " + MINIMUM_STRENGTH
                    + " and " + MAXIMUM_LOGARITHMIC_STRENGTH + ", got " + strength);
            }
        }

        @Override
        public CosmicNivCurveType type() {
            return CosmicNivCurveType.LOGARITHMIC;
        }

        @Override
        public double shape(double progress) {
            return Math.log1p(strength * progress) / Math.log1p(strength);
        }
    }

    record Power(double exponent) implements CosmicNivCurve {
        public Power {
            if (!Double.isFinite(exponent) || exponent <= 0.0 || exponent > MAXIMUM_EXPONENT) {
                throw new IllegalArgumentException("POWER exponent must be above 0 and at most "
                    + MAXIMUM_EXPONENT + ", got " + exponent);
            }
        }

        @Override
        public CosmicNivCurveType type() {
            return CosmicNivCurveType.POWER;
        }

        @Override
        public double shape(double progress) {
            return Math.pow(progress, exponent);
        }
    }

    record Sigmoid(double strength, double midpoint) implements CosmicNivCurve {
        public Sigmoid {
            if (!Double.isFinite(strength) || strength < MINIMUM_STRENGTH || strength > MAXIMUM_SIGMOID_STRENGTH) {
                throw new IllegalArgumentException("SIGMOID strength must be between " + MINIMUM_STRENGTH
                    + " and " + MAXIMUM_SIGMOID_STRENGTH + ", got " + strength);
            }
            if (!Double.isFinite(midpoint) || midpoint <= 0.0 || midpoint >= 1.0) {
                throw new IllegalArgumentException("SIGMOID midpoint must be strictly between 0 and 1, got "
                    + midpoint);
            }
        }

        @Override
        public CosmicNivCurveType type() {
            return CosmicNivCurveType.SIGMOID;
        }

        @Override
        public double shape(double progress) {
            double low = logistic(0.0);
            double high = logistic(1.0);
            return (logistic(progress) - low) / (high - low);
        }

        private double logistic(double progress) {
            return 1.0 / (1.0 + Math.exp(-strength * (progress - midpoint)));
        }
    }

    record Points(List<Point> points) implements CosmicNivCurve {
        public Points {
            Objects.requireNonNull(points, "points");
            points = List.copyOf(points);
            if (points.size() < 2) {
                throw new IllegalArgumentException("POINTS needs at least 2 points");
            }
            Point first = points.getFirst();
            Point last = points.getLast();
            if (first.x() != 0.0 || first.y() != 0.0) {
                throw new IllegalArgumentException("POINTS must start at [0, 0], got " + first);
            }
            if (last.x() != 1.0 || last.y() != 1.0) {
                throw new IllegalArgumentException("POINTS must end at [1, 1], got " + last);
            }
            for (int index = 1; index < points.size(); index++) {
                Point previous = points.get(index - 1);
                Point current = points.get(index);
                if (current.x() <= previous.x()) {
                    throw new IllegalArgumentException("POINTS x values must strictly increase: " + previous
                        + " then " + current);
                }
                if (current.y() < previous.y()) {
                    throw new IllegalArgumentException("POINTS y values must never decrease: " + previous
                        + " then " + current);
                }
            }
        }

        @Override
        public CosmicNivCurveType type() {
            return CosmicNivCurveType.POINTS;
        }

        @Override
        public double shape(double progress) {
            for (int index = 1; index < points.size(); index++) {
                Point right = points.get(index);
                if (progress <= right.x()) {
                    Point left = points.get(index - 1);
                    double fraction = (progress - left.x()) / (right.x() - left.x());
                    return left.y() + fraction * (right.y() - left.y());
                }
            }
            return 1.0;
        }
    }

    record Point(double x, double y) {
        public Point {
            if (!Double.isFinite(x) || !Double.isFinite(y) || x < 0.0 || x > 1.0 || y < 0.0 || y > 1.0) {
                throw new IllegalArgumentException("Curve points must be inside [0, 1], got [" + x + ", " + y + "]");
            }
        }

        @Override
        public String toString() {
            return "[" + x + ", " + y + "]";
        }
    }
}
