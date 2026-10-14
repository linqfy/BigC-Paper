package dev.linqfy.bigCasares.modules.cosmicniv;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CosmicNivCurveTest {
    private static final double EPSILON = 1e-9;

    private static List<CosmicNivCurve> everyCurve() {
        return List.of(
            new CosmicNivCurve.Linear(),
            new CosmicNivCurve.Exponential(3.0),
            new CosmicNivCurve.Exponential(-3.0),
            new CosmicNivCurve.Logarithmic(9.0),
            new CosmicNivCurve.Power(2.0),
            new CosmicNivCurve.Power(0.5),
            new CosmicNivCurve.Sigmoid(10.0, 0.5),
            new CosmicNivCurve.Sigmoid(6.0, 0.3),
            new CosmicNivCurve.Points(List.of(
                new CosmicNivCurve.Point(0.0, 0.0),
                new CosmicNivCurve.Point(0.5, 0.2),
                new CosmicNivCurve.Point(1.0, 1.0)))
        );
    }

    @Test
    void everyCurveMapsZeroToZeroAndOneToOne() {
        for (CosmicNivCurve curve : everyCurve()) {
            assertEquals(0.0, curve.apply(0.0), EPSILON, curve.toString());
            assertEquals(1.0, curve.apply(1.0), EPSILON, curve.toString());
        }
    }

    @Test
    void everyCurveIsNonDecreasingAndStaysInsideTheUnitRange() {
        for (CosmicNivCurve curve : everyCurve()) {
            double previous = 0.0;
            for (int step = 0; step <= 1_000; step++) {
                double value = curve.apply(step / 1_000.0);
                assertTrue(value >= previous - EPSILON, curve + " decreases at step " + step);
                assertTrue(value >= 0.0 && value <= 1.0, curve + " leaves [0,1] at step " + step);
                previous = value;
            }
        }
    }

    @Test
    void inputsOutsideTheUnitRangeAreClamped() {
        for (CosmicNivCurve curve : everyCurve()) {
            assertEquals(0.0, curve.apply(-5.0), EPSILON);
            assertEquals(1.0, curve.apply(7.0), EPSILON);
            assertEquals(0.0, curve.apply(Double.NaN), EPSILON);
        }
    }

    @Test
    void linearReturnsTheProgressUnchanged() {
        assertEquals(0.37, new CosmicNivCurve.Linear().apply(0.37), EPSILON);
    }

    @Test
    void exponentialMatchesItsFormula() {
        double expected = (Math.exp(1.5) - 1.0) / (Math.exp(3.0) - 1.0);
        assertEquals(expected, new CosmicNivCurve.Exponential(3.0).apply(0.5), EPSILON);
        assertTrue(new CosmicNivCurve.Exponential(3.0).apply(0.5) < 0.5);
        assertTrue(new CosmicNivCurve.Exponential(-3.0).apply(0.5) > 0.5);
    }

    @Test
    void logarithmicMatchesItsFormulaAndStartsFast() {
        double expected = Math.log(1.0 + 9.0 * 0.25) / Math.log(10.0);
        assertEquals(expected, new CosmicNivCurve.Logarithmic(9.0).apply(0.25), EPSILON);
        assertTrue(new CosmicNivCurve.Logarithmic(9.0).apply(0.25) > 0.25);
    }

    @Test
    void powerMatchesItsFormula() {
        assertEquals(0.25, new CosmicNivCurve.Power(2.0).apply(0.5), EPSILON);
        assertEquals(Math.sqrt(0.5), new CosmicNivCurve.Power(0.5).apply(0.5), EPSILON);
    }

    @Test
    void sigmoidIsHalfAtASymmetricMidpointAndSteepAroundIt() {
        CosmicNivCurve curve = new CosmicNivCurve.Sigmoid(10.0, 0.5);
        assertEquals(0.5, curve.apply(0.5), EPSILON);
        assertTrue(curve.apply(0.3) < 0.15);
        assertTrue(curve.apply(0.7) > 0.85);
    }

    @Test
    void pointsInterpolateLinearlyBetweenNeighbours() {
        CosmicNivCurve curve = new CosmicNivCurve.Points(List.of(
            new CosmicNivCurve.Point(0.0, 0.0),
            new CosmicNivCurve.Point(0.5, 0.2),
            new CosmicNivCurve.Point(1.0, 1.0)));
        assertEquals(0.1, curve.apply(0.25), EPSILON);
        assertEquals(0.2, curve.apply(0.5), EPSILON);
        assertEquals(0.6, curve.apply(0.75), EPSILON);
    }

    @Test
    void typesAreReportedForEveryCurve() {
        assertEquals(CosmicNivCurveType.LINEAR, new CosmicNivCurve.Linear().type());
        assertEquals(CosmicNivCurveType.EXPONENTIAL, new CosmicNivCurve.Exponential(2.0).type());
        assertEquals(CosmicNivCurveType.LOGARITHMIC, new CosmicNivCurve.Logarithmic(2.0).type());
        assertEquals(CosmicNivCurveType.POWER, new CosmicNivCurve.Power(2.0).type());
        assertEquals(CosmicNivCurveType.SIGMOID, new CosmicNivCurve.Sigmoid(2.0, 0.5).type());
        assertEquals(CosmicNivCurveType.POINTS, new CosmicNivCurve.Points(List.of(
            new CosmicNivCurve.Point(0.0, 0.0), new CosmicNivCurve.Point(1.0, 1.0))).type());
    }

    @Test
    void invalidParametersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new CosmicNivCurve.Exponential(0.0));
        assertThrows(IllegalArgumentException.class, () -> new CosmicNivCurve.Exponential(51.0));
        assertThrows(IllegalArgumentException.class, () -> new CosmicNivCurve.Exponential(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new CosmicNivCurve.Logarithmic(0.0));
        assertThrows(IllegalArgumentException.class, () -> new CosmicNivCurve.Logarithmic(-0.5));
        assertThrows(IllegalArgumentException.class, () -> new CosmicNivCurve.Power(0.0));
        assertThrows(IllegalArgumentException.class, () -> new CosmicNivCurve.Sigmoid(0.0, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new CosmicNivCurve.Sigmoid(5.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new CosmicNivCurve.Sigmoid(5.0, 1.0));
    }

    @Test
    void strengthsTooCloseToZeroAreRejectedInsteadOfCollapsingTheCurve() {
        assertThrows(IllegalArgumentException.class, () -> new CosmicNivCurve.Sigmoid(1e-17, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new CosmicNivCurve.Logarithmic(4.9e-324));
        assertThrows(IllegalArgumentException.class, () -> new CosmicNivCurve.Exponential(-1e-9));
        assertEquals(0.5, new CosmicNivCurve.Sigmoid(1e-6, 0.5).apply(0.5), 1e-6);
        assertEquals(0.5, new CosmicNivCurve.Logarithmic(1e-6).apply(0.5), 1e-6);
    }

    @Test
    void invalidPointListsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new CosmicNivCurve.Points(List.of(
            new CosmicNivCurve.Point(0.0, 0.0))));
        assertThrows(IllegalArgumentException.class, () -> new CosmicNivCurve.Points(List.of(
            new CosmicNivCurve.Point(0.1, 0.0), new CosmicNivCurve.Point(1.0, 1.0))));
        assertThrows(IllegalArgumentException.class, () -> new CosmicNivCurve.Points(List.of(
            new CosmicNivCurve.Point(0.0, 0.0), new CosmicNivCurve.Point(0.9, 1.0))));
        assertThrows(IllegalArgumentException.class, () -> new CosmicNivCurve.Points(List.of(
            new CosmicNivCurve.Point(0.0, 0.1), new CosmicNivCurve.Point(1.0, 1.0))));
        assertThrows(IllegalArgumentException.class, () -> new CosmicNivCurve.Points(List.of(
            new CosmicNivCurve.Point(0.0, 0.0), new CosmicNivCurve.Point(1.0, 0.8))));
        assertThrows(IllegalArgumentException.class, () -> new CosmicNivCurve.Points(List.of(
            new CosmicNivCurve.Point(0.0, 0.0), new CosmicNivCurve.Point(0.5, 0.6),
            new CosmicNivCurve.Point(0.5, 0.7), new CosmicNivCurve.Point(1.0, 1.0))));
        assertThrows(IllegalArgumentException.class, () -> new CosmicNivCurve.Points(List.of(
            new CosmicNivCurve.Point(0.0, 0.0), new CosmicNivCurve.Point(0.5, 0.6),
            new CosmicNivCurve.Point(0.7, 0.4), new CosmicNivCurve.Point(1.0, 1.0))));
    }
}
