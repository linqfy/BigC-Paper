package dev.linqfy.bigCasares.dimension.worldgen;

import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.dimension.worldgen.DensityFunction.Binary;
import dev.linqfy.bigCasares.dimension.worldgen.DensityFunction.BinaryOp;
import dev.linqfy.bigCasares.dimension.worldgen.DensityFunction.Unary;
import dev.linqfy.bigCasares.dimension.worldgen.DensityFunction.UnaryOp;

/** Static factory for readable density-function trees. */
public final class DensityFunctions {

    public static final DensityFunction ZERO = new DensityFunction.Constant(0.0);

    private DensityFunctions() {
    }

    public static DensityFunction constant(double value) {
        return new DensityFunction.Constant(value);
    }

    public static DensityFunction ref(NamespacedId id) {
        return new DensityFunction.Reference(id);
    }

    public static DensityFunction ref(String id) {
        return ref(NamespacedId.parse(id));
    }

    public static DensityFunction noise(NamespacedId noise, double xzScale, double yScale) {
        return new DensityFunction.Noise(noise, xzScale, yScale);
    }

    public static DensityFunction shiftedNoise(
        NamespacedId noise, double xzScale, double yScale, double shiftX, double shiftY, double shiftZ
    ) {
        return new DensityFunction.ShiftedNoise(noise, xzScale, yScale,
            constant(shiftX), constant(shiftY), constant(shiftZ));
    }

    public static DensityFunction add(DensityFunction a, DensityFunction b) {
        return new Binary(BinaryOp.ADD, a, b);
    }

    public static DensityFunction add(DensityFunction a, double b) {
        return add(a, constant(b));
    }

    /** Sums any number of terms left-to-right. */
    public static DensityFunction sum(DensityFunction first, DensityFunction... rest) {
        DensityFunction result = first;
        for (DensityFunction term : rest) {
            result = add(result, term);
        }
        return result;
    }

    public static DensityFunction mul(DensityFunction a, DensityFunction b) {
        return new Binary(BinaryOp.MUL, a, b);
    }

    public static DensityFunction mul(DensityFunction a, double b) {
        return mul(a, constant(b));
    }

    public static DensityFunction min(DensityFunction a, DensityFunction b) {
        return new Binary(BinaryOp.MIN, a, b);
    }

    public static DensityFunction max(DensityFunction a, DensityFunction b) {
        return new Binary(BinaryOp.MAX, a, b);
    }

    public static DensityFunction abs(DensityFunction argument) {
        return new Unary(UnaryOp.ABS, argument);
    }

    public static DensityFunction square(DensityFunction argument) {
        return new Unary(UnaryOp.SQUARE, argument);
    }

    public static DensityFunction cube(DensityFunction argument) {
        return new Unary(UnaryOp.CUBE, argument);
    }

    public static DensityFunction halfNegative(DensityFunction argument) {
        return new Unary(UnaryOp.HALF_NEGATIVE, argument);
    }

    public static DensityFunction quarterNegative(DensityFunction argument) {
        return new Unary(UnaryOp.QUARTER_NEGATIVE, argument);
    }

    /** Vanilla soft clamp: {@code x/2 - x^3/24} on {@code clamp(x, -1, 1)}. */
    public static DensityFunction squeeze(DensityFunction argument) {
        return new Unary(UnaryOp.SQUEEZE, argument);
    }

    public static DensityFunction interpolated(DensityFunction argument) {
        return new Unary(UnaryOp.INTERPOLATED, argument);
    }

    public static DensityFunction flatCache(DensityFunction argument) {
        return new Unary(UnaryOp.FLAT_CACHE, argument);
    }

    public static DensityFunction cache2d(DensityFunction argument) {
        return new Unary(UnaryOp.CACHE_2D, argument);
    }

    public static DensityFunction cacheOnce(DensityFunction argument) {
        return new Unary(UnaryOp.CACHE_ONCE, argument);
    }

    public static DensityFunction blendDensity(DensityFunction argument) {
        return new Unary(UnaryOp.BLEND_DENSITY, argument);
    }

    public static DensityFunction clamp(DensityFunction input, double min, double max) {
        return new DensityFunction.Clamp(input, min, max);
    }

    /** Linear gradient on Y, clamped outside {@code [fromY, toY]}. */
    public static DensityFunction yGradient(int fromY, int toY, double fromValue, double toValue) {
        return new DensityFunction.YClampedGradient(fromY, toY, fromValue, toValue);
    }

    public static DensityFunction rangeChoice(
        DensityFunction input, double minInclusive, double maxExclusive,
        DensityFunction whenInRange, DensityFunction whenOutOfRange
    ) {
        return new DensityFunction.RangeChoice(input, minInclusive, maxExclusive, whenInRange, whenOutOfRange);
    }

    public static DensityFunction spline(CubicSpline spline) {
        return new DensityFunction.Spline(spline);
    }

    public static DensityFunction beardifier() {
        return new DensityFunction.Beardifier();
    }

    public static DensityFunction findTopSurface(
        DensityFunction density, DensityFunction upperBound, int lowerBound, int cellHeight
    ) {
        return new DensityFunction.FindTopSurface(density, upperBound, lowerBound, cellHeight);
    }

    /** {@code a + (b - a) * t} built from vanilla primitives. */
    public static DensityFunction lerp(DensityFunction t, DensityFunction a, DensityFunction b) {
        return add(a, mul(t, add(b, mul(a, -1.0))));
    }
}
