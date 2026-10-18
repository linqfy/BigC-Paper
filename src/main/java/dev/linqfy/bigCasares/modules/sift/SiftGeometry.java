package dev.linqfy.bigCasares.modules.sift;

/** Vectors and boxes for the Sift's creature logic, so it can run without a server. */
final class SiftGeometry {

    private SiftGeometry() {
    }

    record Vec(double x, double y, double z) {

        static final Vec ZERO = new Vec(0.0, 0.0, 0.0);

        Vec plus(Vec other) {
            return new Vec(x + other.x, y + other.y, z + other.z);
        }

        Vec minus(Vec other) {
            return new Vec(x - other.x, y - other.y, z - other.z);
        }

        Vec scale(double factor) {
            return new Vec(x * factor, y * factor, z * factor);
        }

        double length() {
            return Math.sqrt(x * x + y * y + z * z);
        }

        double distanceSquared(Vec other) {
            double dx = x - other.x;
            double dy = y - other.y;
            double dz = z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    /** An axis-aligned box; {@link #overlaps} is strict like Minecraft's {@code AABB.intersects}. */
    record Aabb(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

        /** The box spanned by two points ({@code new AABB(Vec3, Vec3)}). */
        static Aabb between(Vec a, Vec b) {
            return new Aabb(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z()),
                Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z()));
        }

        Aabb inflate(double amount) {
            return new Aabb(minX - amount, minY - amount, minZ - amount, maxX + amount, maxY + amount, maxZ + amount);
        }

        boolean overlaps(Aabb other) {
            return minX < other.maxX && maxX > other.minX && minY < other.maxY && maxY > other.minY
                && minZ < other.maxZ && maxZ > other.minZ;
        }

        /** {@code AABB.intersects(Vec3, Vec3)}: overlap with the box spanned by the two points, not a segment test. */
        boolean intersects(Vec from, Vec to) {
            return overlaps(between(from, to));
        }
    }
}
