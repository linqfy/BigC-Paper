package dev.linqfy.bigCasares.modules.mythicdungeons.boss.horus;

/** Un punto del encuentro de Horus sin tipos de Bukkit (coordenadas de mundo, en bloques). */
public record HorusPoint(double x, double y, double z) {

    public HorusPoint {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("point must be finite");
        }
    }

    public HorusPoint plus(double dx, double dy, double dz) {
        return new HorusPoint(x + dx, y + dy, z + dz);
    }

    public HorusPoint withY(double newY) {
        return new HorusPoint(x, newY, z);
    }

    /** Interpolacion lineal: {@code t = 0} es este punto, {@code t = 1} es {@code other}. */
    public HorusPoint lerp(HorusPoint other, double t) {
        return new HorusPoint(x + (other.x - x) * t, y + (other.y - y) * t, z + (other.z - z) * t);
    }

    public double distance(HorusPoint other) {
        double dx = other.x - x;
        double dy = other.y - y;
        double dz = other.z - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public double horizontalDistance(HorusPoint other) {
        double dx = other.x - x;
        double dz = other.z - z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Yaw de Minecraft (grados) para mirar desde este punto hacia {@code other}; 0 mira al sur (+Z). */
    public float yawTowards(HorusPoint other, float fallback) {
        double dx = other.x - x;
        double dz = other.z - z;
        if (dx * dx + dz * dz < 1.0E-6) {
            return fallback;
        }
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }
}
