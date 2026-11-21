package dev.linqfy.bigCasares.modules.mythicdungeons.boss.odin;

/**
 * La sala del trono vista por las reglas, sin Bukkit: solo quien esta adentro puede ser objetivo de una embestida o
 * de los guardianes. La audiencia del jefe (48 bloques) es mas ancha que la sala (~33x33), asi que alguien en un
 * pasillo detras de una pared no cuenta.
 *
 * @param radius horizontal desde el lugar donde nacio Odín
 * @param below  bloques que puede estar por debajo de sus pies
 * @param above  bloques que puede estar por encima (la sala tiene al menos 16 de alto)
 */
public record OdinArena(double centerX, double centerY, double centerZ, double radius, double below, double above) {

    public static final double ROOM_RADIUS = 17.0;
    public static final double ROOM_BELOW = 4.0;
    public static final double ROOM_ABOVE = 10.0;

    public OdinArena {
        if (!(radius > 0.0) || below < 0.0 || above < 0.0) {
            throw new IllegalArgumentException("arena sizes must be positive");
        }
    }

    public static OdinArena around(double x, double y, double z) {
        return new OdinArena(x, y, z, ROOM_RADIUS, ROOM_BELOW, ROOM_ABOVE);
    }

    public boolean contains(double x, double y, double z) {
        double dx = x - centerX;
        double dz = z - centerZ;
        return dx * dx + dz * dz <= radius * radius && y >= centerY - below && y <= centerY + above;
    }
}
