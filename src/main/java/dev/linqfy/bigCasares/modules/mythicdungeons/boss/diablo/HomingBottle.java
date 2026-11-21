package dev.linqfy.bigCasares.modules.mythicdungeons.boss.diablo;

import java.util.Objects;

/**
 * La botella de vino de la fase 1, sin Bukkit: persigue al jugador cada tick (vuelve a apuntar siempre, sin inercia)
 * y acelera, asi que no se puede esquivar; si igual tarda demasiado, pega al llegar al tope de ticks. No choca con
 * bloques: es un efecto guionado, no un proyectil vanilla.
 */
final class HomingBottle {

    record Vec(double x, double y, double z) {

        double distance(Vec other) {
            double dx = x - other.x;
            double dy = y - other.y;
            double dz = z - other.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    /** La posicion despues del paso y si pego. */
    record Step(Vec position, boolean hit) {
    }

    private final double hitRadius;
    private final double acceleration;
    private final int maximumTicks;
    private Vec position;
    private double speed;
    private int age;
    private boolean done;

    /**
     * @param speed        bloques por tick al salir (los jugadores corren a ~0.28)
     * @param acceleration bloques por tick que gana cada tick
     * @param hitRadius    distancia a la que pega
     * @param maximumTicks pega si llega a este tope
     */
    HomingBottle(Vec start, double speed, double acceleration, double hitRadius, int maximumTicks) {
        if (speed <= 0.0 || acceleration < 0.0 || hitRadius <= 0.0 || maximumTicks < 1) {
            throw new IllegalArgumentException("invalid bottle settings");
        }
        this.position = Objects.requireNonNull(start, "start");
        this.speed = speed;
        this.acceleration = acceleration;
        this.hitRadius = hitRadius;
        this.maximumTicks = maximumTicks;
    }

    Step advance(Vec target) {
        if (done) {
            return new Step(position, false);
        }
        age++;
        double distance = position.distance(target);
        if (distance <= hitRadius + speed || age >= maximumTicks) {
            position = target;
            done = true;
            return new Step(position, true);
        }
        double factor = speed / distance;
        // un arco chico hacia arriba al principio, para que se lea como un tiro y no como un laser
        double lift = age < 6 ? 0.06 * (6 - age) : 0.0;
        position = new Vec(position.x() + (target.x() - position.x()) * factor,
            position.y() + (target.y() - position.y()) * factor + lift,
            position.z() + (target.z() - position.z()) * factor);
        speed += acceleration;
        return new Step(position, false);
    }

    Vec position() {
        return position;
    }

    boolean done() {
        return done;
    }

    int age() {
        return age;
    }
}
