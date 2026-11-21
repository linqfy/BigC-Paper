package dev.linqfy.bigCasares.modules.mythicdungeons.boss.iblis;

/** A point on the arena floor (world x, z), without server types. */
public record IblisPoint(double x, double z) {

    public double distance(IblisPoint other) {
        return Math.hypot(x - other.x, z - other.z);
    }

    /** Distance from this point to the segment {@code a}-{@code b}. */
    public double distanceToSegment(IblisPoint a, IblisPoint b) {
        double dx = b.x - a.x;
        double dz = b.z - a.z;
        double lengthSquared = dx * dx + dz * dz;
        if (lengthSquared < 1.0e-9) {
            return distance(a);
        }
        double t = ((x - a.x) * dx + (z - a.z) * dz) / lengthSquared;
        t = Math.max(0.0, Math.min(1.0, t));
        return Math.hypot(x - (a.x + dx * t), z - (a.z + dz * t));
    }

    /** The point {@code t} of the way from this one to {@code to} (0 = here, 1 = there). */
    public IblisPoint toward(IblisPoint to, double t) {
        return new IblisPoint(x + (to.x - x) * t, z + (to.z - z) * t);
    }
}
