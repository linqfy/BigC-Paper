package dev.linqfy.bigCasares.modules.caichet;

/**
 * Donde y de que tamaño se pone la copia del arcoiris que ve un jugador de Bedrock. El servidor no manda entidades
 * a mas de unos 64-96 bloques y el arcoiris esta a 90-140, asi que se acerca sobre la recta ojos-arcoiris hasta
 * {@link #MAX_DISTANCE} bloques y se agranda en la misma proporcion: se ve igual de grande y en el mismo lugar del cielo.
 */
final class RainbowPlacement {

    /** A que distancia de los ojos queda la copia, como mucho. */
    static final double MAX_DISTANCE = 48.0;
    /** Lado, en bloques, del plano del modelo Bedrock con escala 1 (ver geometry.caichet.dream_rainbow). */
    static final double MODEL_BLOCKS = 16.0;
    /** Limites del atributo SCALE de Minecraft. */
    static final double MIN_SCALE = 0.0625;
    static final double MAX_SCALE = 16.0;

    /**
     * @param x     posicion de la copia
     * @param y     posicion de la copia
     * @param z     posicion de la copia
     * @param scale valor del atributo SCALE, ya dentro de los limites
     * @param yaw   giro en grados (convencion de Bukkit) para que mire a los ojos
     */
    record Placement(double x, double y, double z, double scale, float yaw) {
    }

    private RainbowPlacement() {
    }

    /** La distancia de la copia a los ojos: {@code min(48, distancia al arcoiris)}. */
    static double placementDistance(double distanceToAnchor) {
        return Math.min(MAX_DISTANCE, Math.max(0.0, distanceToAnchor));
    }

    /** SCALE para un arcoiris de {@code size} bloques de lado visto a {@code distance}, con la copia a {@code placed}. */
    static double scale(double size, double placed, double distance) {
        double apparent = distance <= 1.0e-9 ? size : size * placed / distance;
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, apparent / MODEL_BLOCKS));
    }

    static Placement place(double eyeX, double eyeY, double eyeZ, double anchorX, double anchorY, double anchorZ, double size) {
        double dx = anchorX - eyeX;
        double dy = anchorY - eyeY;
        double dz = anchorZ - eyeZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double placed = placementDistance(distance);
        double k = distance <= 1.0e-9 ? 0.0 : placed / distance;
        // la copia mira a los ojos: la direccion de la copia a los jugadores es la opuesta a (dx, dz)
        float yaw = (dx == 0.0 && dz == 0.0) ? 0f : (float) Math.toDegrees(Math.atan2(dx, -dz));
        return new Placement(eyeX + dx * k, eyeY + dy * k, eyeZ + dz * k, scale(size, placed, distance), yaw);
    }
}
