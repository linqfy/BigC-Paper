package dev.linqfy.bigCasares.modules.mythicdungeons.boss.came;

/**
 * La cancha de Xibalbá vista por las reglas, sin Bukkit: solo quien esta adentro es blanco de la pelota o de los
 * golpes. La audiencia del jefe (48 bloques) atraviesa paredes y es mas ancha que la sala (~33x33).
 *
 * @param radius horizontal desde donde aparecio el jefe
 * @param below  bloques que puede estar por debajo de sus pies
 * @param above  bloques que puede estar por encima (la sala tiene al menos 16 de alto)
 */
public record CameArena(double centerX, double centerY, double centerZ, double radius, double below, double above) {

    public static final double ROOM_RADIUS = 17.0;
    public static final double ROOM_BELOW = 4.0;
    public static final double ROOM_ABOVE = 10.0;
    /** Hasta donde se pueden mover los señores (un poco adentro de las paredes). */
    public static final double MOVE_RADIUS = 14.5;

    public CameArena {
        if (!(radius > 0.0) || below < 0.0 || above < 0.0) {
            throw new IllegalArgumentException("arena sizes must be positive");
        }
    }

    public static CameArena around(double x, double y, double z) {
        return new CameArena(x, y, z, ROOM_RADIUS, ROOM_BELOW, ROOM_ABOVE);
    }

    public boolean contains(double x, double y, double z) {
        double dx = x - centerX;
        double dz = z - centerZ;
        return dx * dx + dz * dz <= radius * radius && y >= centerY - below && y <= centerY + above;
    }

    /** {@code point} acercado al centro lo necesario para quedar a menos de {@code limit} (altura intacta). */
    public CameVec clamp(CameVec point, double limit) {
        double dx = point.x() - centerX;
        double dz = point.z() - centerZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance <= limit) {
            return point;
        }
        double factor = limit / distance;
        return new CameVec(centerX + dx * factor, point.y(), centerZ + dz * factor);
    }
}
