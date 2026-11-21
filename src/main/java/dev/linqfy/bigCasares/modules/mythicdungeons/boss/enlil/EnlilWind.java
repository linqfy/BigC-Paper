package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enlil;

/** Vector math of the wind (no server types): gusts, tornado drag and arrow deflection. */
public final class EnlilWind {

    /** A velocity in blocks per tick. */
    public record Push(double x, double y, double z) {

        public double horizontal() {
            return Math.hypot(x, z);
        }
    }

    private EnlilWind() {
    }

    /**
     * The gust throws everyone away from Enlil, toward the walls. Sneaking braces against it: a third of the push
     * and almost no lift.
     */
    public static Push gust(double bossX, double bossZ, double playerX, double playerZ, boolean braced) {
        double dx = playerX - bossX;
        double dz = playerZ - bossZ;
        double length = Math.hypot(dx, dz);
        if (length < 1.0e-6) {
            dx = 1.0;
            dz = 0.0;
            length = 1.0;
        }
        double strength = EnlilTuning.GUST_PUSH * (braced ? 0.3 : 1.0);
        return new Push(dx / length * strength, braced ? 0.1 : 0.45, dz / length * strength);
    }

    /** Whether a point (relative to the tornado base) is inside the column, which widens with height. */
    public static boolean insideTornado(double dx, double dy, double dz) {
        if (dy < -0.5 || dy > EnlilTuning.TORNADO_HEIGHT) {
            return false;
        }
        double radius = EnlilTuning.TORNADO_RADIUS + Math.max(0.0, dy) * 0.12;
        return dx * dx + dz * dz <= radius * radius;
    }

    /**
     * Drag of a tornado on someone inside it: a swirl around the axis, a slight pull in and a lift that stops
     * near the top so nobody is flung out of the room (the fall from there costs a heart or two).
     */
    public static Push tornadoDrag(double dx, double dy, double dz) {
        double length = Math.hypot(dx, dz);
        double nx = length < 1.0e-6 ? 1.0 : dx / length;
        double nz = length < 1.0e-6 ? 0.0 : dz / length;
        double lift = dy < EnlilTuning.TORNADO_HEIGHT - 2.0 ? 0.42 : 0.04;
        return new Push(-nz * 0.32 - nx * 0.08, lift, nx * 0.32 - nz * 0.08);
    }

    /** One step of a tornado toward its target, never faster than {@link EnlilTuning#TORNADO_SPEED}. */
    public static double[] step(double x, double z, double targetX, double targetZ) {
        double dx = targetX - x;
        double dz = targetZ - z;
        double length = Math.hypot(dx, dz);
        if (length <= EnlilTuning.TORNADO_SPEED) {
            return new double[] {targetX, targetZ};
        }
        return new double[] {x + dx / length * EnlilTuning.TORNADO_SPEED, z + dz / length * EnlilTuning.TORNADO_SPEED};
    }

    /** {@code true} when a projectile at offset (dx, dy, dz) from Enlil flies toward him. */
    public static boolean incoming(double dx, double dy, double dz, double vx, double vy, double vz) {
        return dx * vx + dy * vy + dz * vz < 0.0;
    }

    /**
     * Bounces a projectile off the wind around Enlil: mirrored on the sphere normal at its offset, slowed down and
     * turned by {@code scatter} radians around the vertical so a volley sprays apart.
     */
    public static Push deflect(double dx, double dy, double dz, double vx, double vy, double vz, double scatter) {
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double nx = length < 1.0e-6 ? 0.0 : dx / length;
        double ny = length < 1.0e-6 ? 1.0 : dy / length;
        double nz = length < 1.0e-6 ? 0.0 : dz / length;
        double dot = vx * nx + vy * ny + vz * nz;
        double rx = (vx - 2.0 * dot * nx) * 0.7;
        double ry = (vy - 2.0 * dot * ny) * 0.7 + 0.15;
        double rz = (vz - 2.0 * dot * nz) * 0.7;
        double cos = Math.cos(scatter);
        double sin = Math.sin(scatter);
        return new Push(rx * cos - rz * sin, ry, rx * sin + rz * cos);
    }
}
