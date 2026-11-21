package dev.linqfy.bigCasares.modules.mythicdungeons.boss.came;

import java.util.Objects;

/**
 * El aro de piedra de la cancha de pelota, como geometria pura: un anillo vertical con centro {@code center} y normal
 * {@code normal} (horizontal, apunta hacia el lado de los jugadores). Una pelota devuelta es gol si cruza el plano del
 * aro de adelante hacia atras a menos de {@link #opening(int)} + {@link #SLACK} del centro. La abertura se achica con
 * cada gol. Para que se pueda jugar en Bedrock / pantalla tactil hay holgura (el radio de la pelota y algo mas) y una
 * ayuda de punteria: si el golpe sale dentro de un cono alrededor del aro, la pelota va derecho al centro.
 */
public record CameHoop(CameVec center, CameVec normal) {

    /** Goles que terminan la fase 1. */
    public static final int GOALS_TO_WIN = 3;
    /** Lo que se perdona por fuera de la abertura (radio de la pelota ~0.8 menos un poco). */
    public static final double SLACK = 0.55;
    private static final double[] OPENINGS = {2.2, 1.6, 1.1};
    private static final double[] ASSIST_DEGREES = {30.0, 22.0, 15.0};

    public CameHoop {
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(normal, "normal");
        CameVec flat = new CameVec(normal.x(), 0.0, normal.z());
        if (flat.length() < 1.0e-6) {
            throw new IllegalArgumentException("the hoop normal must be horizontal");
        }
        normal = flat.normalize(flat);
    }

    /** Radio de la abertura (bloques) despues de {@code goals} goles. */
    public static double opening(int goals) {
        return OPENINGS[Math.max(0, Math.min(OPENINGS.length - 1, goals))];
    }

    /** Medio angulo del cono de ayuda de punteria (grados) despues de {@code goals} goles. */
    public static double assistDegrees(int goals) {
        return ASSIST_DEGREES[Math.max(0, Math.min(ASSIST_DEGREES.length - 1, goals))];
    }

    /** Distancia con signo al plano del aro: positiva del lado de los jugadores. */
    public double side(CameVec point) {
        return point.subtract(center).dot(normal);
    }

    /** {@code true} si el tramo {@code from -> to} atraviesa la abertura de adelante hacia atras. */
    public boolean passes(CameVec from, CameVec to, int goals) {
        double before = side(from);
        double after = side(to);
        if (!(before > 0.0 && after <= 0.0)) {
            return false;
        }
        double t = before / (before - after);
        CameVec crossing = from.add(to.subtract(from).multiply(t));
        return crossing.distance(center) <= opening(goals) + SLACK;
    }

    /**
     * La direccion de una pelota golpeada desde {@code from} hacia donde mira el jugador ({@code look}): si apunta
     * dentro del cono de ayuda, derecho al centro del aro; si no, tal cual. Desde atras del aro no hay ayuda.
     */
    public CameVec assist(CameVec from, CameVec look, int goals) {
        CameVec aim = look.normalize(normal.multiply(-1.0));
        if (side(from) <= 0.0) {
            return aim;
        }
        CameVec toHoop = center.subtract(from);
        if (toHoop.length() < 1.0e-6) {
            return aim;
        }
        return aim.angleTo(toHoop) <= assistDegrees(goals) ? toHoop.normalize(aim) : aim;
    }
}
