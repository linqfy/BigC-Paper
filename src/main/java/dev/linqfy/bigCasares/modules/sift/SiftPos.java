package dev.linqfy.bigCasares.modules.sift;

/**
 * An integer block position for the Bukkit-free creature logic (the mod's {@code BlockPos}).
 * {@link #asLong()} uses the same packing as {@code BlockPos.asLong}, so positions stored by the mod
 * and by this port are interchangeable.
 */
record SiftPos(int x, int y, int z) {

    private static final long XZ_MASK = 0x3FFFFFFL;
    private static final long Y_MASK = 0xFFFL;

    static SiftPos of(long packed) {
        return new SiftPos((int) (packed >> 38), (int) (packed << 52 >> 52), (int) (packed << 26 >> 38));
    }

    long asLong() {
        return ((long) x & XZ_MASK) << 38 | ((long) z & XZ_MASK) << 12 | (long) y & Y_MASK;
    }

    /** {@code BlockPos.containing}: the block that holds the point. */
    static SiftPos containing(double x, double y, double z) {
        return new SiftPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    SiftPos above() {
        return new SiftPos(x, y + 1, z);
    }

    SiftPos above(int blocks) {
        return new SiftPos(x, y + blocks, z);
    }

    SiftPos below() {
        return new SiftPos(x, y - 1, z);
    }

    SiftPos offset(int dx, int dy, int dz) {
        return new SiftPos(x + dx, y + dy, z + dz);
    }

    /** {@code BlockPos.distSqr(Vec3i)}. */
    double distSqr(SiftPos other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    double horizontalDistanceSqr(SiftPos other) {
        double dx = x - other.x;
        double dz = z - other.z;
        return dx * dx + dz * dz;
    }

    /** {@code Vec3.atBottomCenterOf}. */
    SiftPoint bottomCenter() {
        return new SiftPoint(x + 0.5, y, z + 0.5);
    }

    /** {@code Vec3.atCenterOf}. */
    SiftPoint center() {
        return new SiftPoint(x + 0.5, y + 0.5, z + 0.5);
    }
}
