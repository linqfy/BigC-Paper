package dev.linqfy.bigCasares.dimension.travel;

/**
 * A detected rectangular portal: the interior spans
 * {@code [minX..maxX] x [minY..maxY] x [minZ..maxZ]}, one block thick along the
 * axis perpendicular to the frame plane.
 */
public record PortalShape(Axis axis, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    /** {@code X}: the frame lies in the X/Y plane; {@code Z}: in the Z/Y plane. */
    public enum Axis {
        X, Z
    }

    public PortalShape {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Inverted portal bounds");
        }
        if (axis == Axis.X && minZ != maxZ || axis == Axis.Z && minX != maxX) {
            throw new IllegalArgumentException("Portal interior must be one block thick");
        }
    }

    public int width() {
        return axis == Axis.X ? maxX - minX + 1 : maxZ - minZ + 1;
    }

    public int height() {
        return maxY - minY + 1;
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public double centerX() {
        return (minX + maxX + 1) / 2.0;
    }

    public double centerZ() {
        return (minZ + maxZ + 1) / 2.0;
    }
}
