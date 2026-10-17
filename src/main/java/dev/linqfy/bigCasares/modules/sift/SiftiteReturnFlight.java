package dev.linqfy.bigCasares.modules.sift;

/**
 * The movement of a returning Siftite item ({@code SiftiteReturnEntity.tick}): it homes on the owner's chest
 * with a damped velocity, and while it must wait (the inventory is full, or an earlier item has not arrived
 * yet) it circles the owner instead. Pure arithmetic, so the flight is tested without a server.
 *
 * <p>Each tick the mod ages the entity, delivers the item if it is within 0.65 blocks of the target and
 * nothing is ahead of it, then moves: velocity = 0.65 x old + 0.35 x (0.38 x offset to the destination,
 * or 0.3 x when waiting), capped at 1.25 blocks per tick (0.45 when waiting), ignoring collisions. The
 * destination is the owner's chest ({@code 0.62} of the body height) or, when waiting, a point 1.65 blocks
 * around it on a bobbing circle whose phase depends on the item and on its age. Every other tick it leaves
 * an end-rod particle.</p>
 *
 * <p>Differences: the mod's phase offset is {@code entityId & 15}; here it is the index of the flight within
 * the recovery.</p>
 */
final class SiftiteReturnFlight {

    /** Squared distance to the target at which the item is handed over (0.65 blocks). */
    static final double ARRIVAL_DISTANCE_SQUARED = 0.65 * 0.65;
    static final double ORBIT_RADIUS = 1.65;
    static final double FLY_SPEED_LIMIT = 1.25;
    static final double WAIT_SPEED_LIMIT = 0.45;

    private double x;
    private double y;
    private double z;
    private double vx;
    private double vy;
    private double vz;
    private int age;
    private final int phase;

    SiftiteReturnFlight(double x, double y, double z, int phase) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.phase = phase & 0xF;
    }

    /** Ages the flight; first call of a tick. */
    void beginTick() {
        age++;
    }

    /** Whether the item is close enough to the target to be handed over. */
    boolean arrived(double targetX, double targetY, double targetZ) {
        double dx = targetX - x;
        double dy = targetY - y;
        double dz = targetZ - z;
        return dx * dx + dy * dy + dz * dz <= ARRIVAL_DISTANCE_SQUARED;
    }

    /** Moves one tick towards {@code target}, or around it when {@code waiting}. */
    void move(double targetX, double targetY, double targetZ, boolean waiting) {
        double destX = targetX;
        double destY = targetY;
        double destZ = targetZ;
        if (waiting) {
            double angle = age * 0.17 + phase * 0.39;
            destX += Math.cos(angle) * ORBIT_RADIUS;
            destY += 0.35 + Math.sin(angle * 1.7) * 0.35;
            destZ += Math.sin(angle) * ORBIT_RADIUS;
        }
        double gain = waiting ? 0.3 : 0.38;
        vx = vx * 0.65 + (destX - x) * gain * 0.35;
        vy = vy * 0.65 + (destY - y) * gain * 0.35;
        vz = vz * 0.65 + (destZ - z) * gain * 0.35;
        double limit = waiting ? WAIT_SPEED_LIMIT : FLY_SPEED_LIMIT;
        double lengthSquared = vx * vx + vy * vy + vz * vz;
        if (lengthSquared > limit * limit) {
            double scale = limit / Math.sqrt(lengthSquared);
            vx *= scale;
            vy *= scale;
            vz *= scale;
        }
        x += vx;
        y += vy;
        z += vz;
    }

    /** Whether this tick leaves a trail particle (every other tick). */
    boolean leavesTrail() {
        return (age & 1) == 0;
    }

    double x() {
        return x;
    }

    double y() {
        return y;
    }

    double z() {
        return z;
    }

    int age() {
        return age;
    }

    double speed() {
        return Math.sqrt(vx * vx + vy * vy + vz * vz);
    }
}
