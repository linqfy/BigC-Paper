package dev.linqfy.bigCasares.modules.mythicdungeons.boss.diablo;

import java.util.Objects;

/**
 * El movimiento de Diablo en la oscuridad (fase 1), en el plano XZ y sin Bukkit: se aleja del jugador elegido,
 * vuelve a acercarse, le da vueltas alrededor y, al terminar la vuelta, avisa y tira un zarpazo; despues repite
 * cambiando el sentido de giro. Cada tick devuelve a donde ir, limitado a la velocidad de cada tramo.
 */
final class DiabloOrbit {

    enum Leg { RETREAT, APPROACH, CIRCLE, SWIPE }

    record Point(double x, double z) {

        double distance(Point other) {
            return Math.hypot(x - other.x, z - other.z);
        }
    }

    /**
     * @param to        a donde mover al jefe este tick
     * @param leg       el tramo actual
     * @param legStart  {@code true} en el primer tick de un tramo (para animaciones y avisos)
     * @param strike    {@code true} en el tick en que el zarpazo pega
     */
    record Move(Point to, Leg leg, boolean legStart, boolean strike) {
    }

    /**
     * @param retreatDistance distancia a la que se aleja
     * @param circleRadius    radio de las vueltas
     * @param retreatTicks    duracion del alejamiento
     * @param approachTicks   duracion del acercamiento
     * @param circleTicks     duracion de las vueltas
     * @param swipeTicks      aviso del zarpazo (el golpe llega al final)
     * @param walkSpeed       bloques por tick al alejarse/acercarse
     * @param circleSpeed     bloques por tick dando vueltas
     */
    record Settings(double retreatDistance, double circleRadius, int retreatTicks, int approachTicks, int circleTicks,
                    int swipeTicks, double walkSpeed, double circleSpeed) {

        Settings {
            if (retreatDistance <= circleRadius || circleRadius <= 0.0 || walkSpeed <= 0.0 || circleSpeed <= 0.0) {
                throw new IllegalArgumentException("invalid orbit distances");
            }
            if (retreatTicks < 1 || approachTicks < 1 || circleTicks < 1 || swipeTicks < 1) {
                throw new IllegalArgumentException("invalid orbit timings");
            }
        }
    }

    private final Settings settings;
    private Leg leg = Leg.RETREAT;
    private long legStart = Long.MIN_VALUE;
    private double angle;
    private int direction = 1;

    DiabloOrbit(Settings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    Move next(long tick, Point boss, Point target) {
        boolean started = false;
        if (legStart == Long.MIN_VALUE) {
            legStart = tick;
            started = true;
        } else if (tick - legStart >= duration(leg)) {
            leg = Leg.values()[(leg.ordinal() + 1) % Leg.values().length];
            legStart = tick;
            started = true;
            if (leg == Leg.RETREAT) {
                direction = -direction;
            }
        }
        if (started && leg == Leg.CIRCLE) {
            angle = Math.atan2(boss.z() - target.z(), boss.x() - target.x());
        }
        Point to = switch (leg) {
            case RETREAT -> step(boss, radial(boss, target, settings.retreatDistance()), settings.walkSpeed());
            case APPROACH -> step(boss, radial(boss, target, settings.circleRadius()), settings.walkSpeed());
            case CIRCLE -> {
                angle += direction * settings.circleSpeed() / settings.circleRadius();
                Point onRing = new Point(target.x() + Math.cos(angle) * settings.circleRadius(),
                    target.z() + Math.sin(angle) * settings.circleRadius());
                yield step(boss, onRing, settings.circleSpeed() * 1.6);
            }
            case SWIPE -> boss;
        };
        boolean strike = leg == Leg.SWIPE && tick - legStart == settings.swipeTicks() - 1L;
        return new Move(to, leg, started, strike);
    }

    /** El paso de este tick no se pudo dar (pared o fuera de la sala): gira para el otro lado. */
    void blocked() {
        direction = -direction;
    }

    Leg leg() {
        return leg;
    }

    int direction() {
        return direction;
    }

    private long duration(Leg value) {
        return switch (value) {
            case RETREAT -> settings.retreatTicks();
            case APPROACH -> settings.approachTicks();
            case CIRCLE -> settings.circleTicks();
            case SWIPE -> settings.swipeTicks();
        };
    }

    /** El punto a {@code distance} del objetivo en la direccion en la que esta el jefe. */
    private static Point radial(Point boss, Point target, double distance) {
        double dx = boss.x() - target.x();
        double dz = boss.z() - target.z();
        double length = Math.hypot(dx, dz);
        if (length < 1.0e-6) {
            dx = 1.0;
            dz = 0.0;
            length = 1.0;
        }
        return new Point(target.x() + dx / length * distance, target.z() + dz / length * distance);
    }

    private static Point step(Point from, Point to, double maximum) {
        double distance = from.distance(to);
        if (distance <= maximum) {
            return to;
        }
        double factor = maximum / distance;
        return new Point(from.x() + (to.x() - from.x()) * factor, from.z() + (to.z() - from.z()) * factor);
    }
}
