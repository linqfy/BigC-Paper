package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enma;

/**
 * La sala del juicio vista por las reglas, sin Bukkit: solo quien esta adentro puede ser blanco de Enma, de sus
 * reflejos, de los oni o de las balas. La audiencia del runtime (48 bloques) atraviesa paredes; la sala mide ~33x33.
 *
 * @param radius horizontal desde donde nacio Enma
 * @param below  bloques que se puede estar por debajo de sus pies
 * @param above  bloques que se puede estar por encima (la sala tiene al menos 16 de alto)
 */
public record EnmaArena(double centerX, double centerY, double centerZ, double radius, double below, double above) {

    public static final double ROOM_RADIUS = 17.0;
    public static final double ROOM_BELOW = 4.0;
    public static final double ROOM_ABOVE = 12.0;

    public EnmaArena {
        if (!(radius > 0.0) || below < 0.0 || above < 0.0) {
            throw new IllegalArgumentException("arena sizes must be positive");
        }
    }

    public static EnmaArena around(double x, double y, double z) {
        return new EnmaArena(x, y, z, ROOM_RADIUS, ROOM_BELOW, ROOM_ABOVE);
    }

    public boolean contains(double x, double y, double z) {
        return containsHorizontally(x, z) && y >= centerY - below && y <= centerY + above;
    }

    public boolean containsHorizontally(double x, double z) {
        double dx = x - centerX;
        double dz = z - centerZ;
        return dx * dx + dz * dz <= radius * radius;
    }
}
