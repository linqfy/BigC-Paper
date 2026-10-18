package dev.linqfy.bigCasares.modules.sift;

/** A point in the world for the Bukkit-free creature logic (the mod's {@code Vec3}). */
record SiftPoint(double x, double y, double z) {

    double distanceSqr(SiftPoint other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    double horizontalDistanceSqr(SiftPoint other) {
        double dx = x - other.x;
        double dz = z - other.z;
        return dx * dx + dz * dz;
    }

    SiftPos containing() {
        return SiftPos.containing(x, y, z);
    }
}
