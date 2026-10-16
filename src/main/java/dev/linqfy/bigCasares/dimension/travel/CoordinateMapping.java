package dev.linqfy.bigCasares.dimension.travel;

/**
 * Maps horizontal coordinates between a source world and a dimension:
 * {@code dimension = source * scale + offset}. The inverse is used for returns.
 * Results are clamped inside the destination world border.
 */
public record CoordinateMapping(double scale, double offsetX, double offsetZ) {

    public static final CoordinateMapping IDENTITY = new CoordinateMapping(1.0, 0.0, 0.0);

    public CoordinateMapping {
        if (!(scale > 0.0) || !Double.isFinite(scale) || !Double.isFinite(offsetX) || !Double.isFinite(offsetZ)) {
            throw new IllegalArgumentException("Invalid coordinate mapping");
        }
    }

    public double[] toDimension(double x, double z) {
        return new double[]{x * scale + offsetX, z * scale + offsetZ};
    }

    public double[] fromDimension(double x, double z) {
        return new double[]{(x - offsetX) / scale, (z - offsetZ) / scale};
    }

    /** Clamps a point to a square border of {@code diameter} centred on (cx, cz), keeping a margin. */
    public static double[] clampToBorder(double x, double z, double centerX, double centerZ, double diameter, double margin) {
        double half = Math.max(0.0, diameter / 2.0 - margin);
        return new double[]{
            Math.max(centerX - half, Math.min(centerX + half, x)),
            Math.max(centerZ - half, Math.min(centerZ + half, z))
        };
    }
}
