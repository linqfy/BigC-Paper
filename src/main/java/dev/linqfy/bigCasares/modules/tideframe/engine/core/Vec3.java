package dev.linqfy.bigCasares.modules.tideframe.engine.core;

/** Small immutable, finite vector used by the server independent geometry. */
public record Vec3(double x, double y, double z) {
    public Vec3 {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
            throw new IllegalArgumentException("Vector coordinates must be finite");
        // A geometric zero has one canonical representation for value equality.
        x = x == 0 ? 0 : x; y = y == 0 ? 0 : y; z = z == 0 ? 0 : z;
    }

    public Vec3 add(Vec3 v) { return new Vec3(x + v.x, y + v.y, z + v.z); }
    public Vec3 multiply(double n) { return new Vec3(x * n, y * n, z * n); }
    public double dot(Vec3 v) { return x * v.x + y * v.y + z * v.z; }
    public Vec3 cross(Vec3 v) {
        return new Vec3(y * v.z - z * v.y, z * v.x - x * v.z, x * v.y - y * v.x);
    }
    public Vec3 normalized() {
        // Scale before squaring so very large and tiny finite vectors stay valid.
        double largest = Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z)));
        if (largest == 0) throw new IllegalArgumentException("A zero vector has no direction");
        double sx = x / largest, sy = y / largest, sz = z / largest;
        double length = Math.sqrt(sx * sx + sy * sy + sz * sz);
        return new Vec3(sx / length, sy / length, sz / length);
    }
}
