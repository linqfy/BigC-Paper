package dev.linqfy.bigCasares.modules.mythicdungeons.boss.came;

/** Un vector de mundo (bloques) para las reglas puras de Camé, sin Bukkit. */
public record CameVec(double x, double y, double z) {

    public static final CameVec ZERO = new CameVec(0.0, 0.0, 0.0);

    public CameVec add(CameVec other) {
        return new CameVec(x + other.x, y + other.y, z + other.z);
    }

    public CameVec subtract(CameVec other) {
        return new CameVec(x - other.x, y - other.y, z - other.z);
    }

    public CameVec multiply(double factor) {
        return new CameVec(x * factor, y * factor, z * factor);
    }

    public double dot(CameVec other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public double length() {
        return Math.sqrt(dot(this));
    }

    public double distance(CameVec other) {
        return subtract(other).length();
    }

    public double horizontalDistance(CameVec other) {
        double dx = x - other.x;
        double dz = z - other.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** El mismo vector de largo 1; {@code fallback} si es casi nulo. */
    public CameVec normalize(CameVec fallback) {
        double length = length();
        return length < 1.0e-9 ? fallback : multiply(1.0 / length);
    }

    /** La parte horizontal, de largo 1 (o {@code +Z} si es vertical). */
    public CameVec horizontal() {
        return new CameVec(x, 0.0, z).normalize(new CameVec(0.0, 0.0, 1.0));
    }

    /** La perpendicular horizontal (gira 90 grados alrededor del eje vertical). */
    public CameVec perpendicular() {
        return new CameVec(-z, 0.0, x);
    }

    /** Angulo entre dos direcciones, en grados (0..180). */
    public double angleTo(CameVec other) {
        double lengths = length() * other.length();
        if (lengths < 1.0e-9) {
            return 180.0;
        }
        double cos = Math.max(-1.0, Math.min(1.0, dot(other) / lengths));
        return Math.toDegrees(Math.acos(cos));
    }
}
