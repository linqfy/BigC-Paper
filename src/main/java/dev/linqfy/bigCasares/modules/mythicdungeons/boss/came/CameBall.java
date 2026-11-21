package dev.linqfy.bigCasares.modules.mythicdungeons.boss.came;

import java.util.Objects;

/**
 * El vuelo de la pelota de hule, sin Bukkit. Posiciones = centro de la pelota.
 *
 * <ul>
 *   <li>{@code HELD}: en la mano del que la tira (el aviso).</li>
 *   <li>{@code INBOUND}: va hacia el jugador elegido, rapido de lejos y lenta de cerca para que le pueda pegar;
 *   si lo alcanza es {@link Event#IMPACT}; si choca con una pared o tarda demasiado se cae ({@link Event#DROPPED}).</li>
 *   <li>{@code RETURNING}: un jugador le pego; vuela recto hacia donde miraba (con la ayuda del aro). Si cruza la
 *   abertura es {@link Event#GOAL}; si choca o se aleja demasiado, {@link Event#MISSED}.</li>
 * </ul>
 */
public final class CameBall {

    public enum Mode { HELD, INBOUND, RETURNING, DONE }

    public enum Event { NONE, IMPACT, DROPPED, GOAL, MISSED }

    /** Que bloques frenan la pelota. */
    @FunctionalInterface
    public interface Terrain {
        boolean solid(double x, double y, double z);
    }

    public static final double FAR_SPEED = 0.6;
    public static final double NEAR_SPEED = 0.25;
    public static final double NEAR_DISTANCE = 5.0;
    /** Distancia del centro de la pelota al pecho del jugador que cuenta como pelotazo. */
    public static final double HIT_RADIUS = 1.15;
    public static final double RETURN_SPEED = 1.0;
    public static final double MAX_RETURN = 34.0;
    public static final int MAX_INBOUND_TICKS = 140;

    private CameVec position;
    private CameVec direction = new CameVec(0.0, 0.0, 1.0);
    private Mode mode = Mode.HELD;
    private double travelled;
    private int ticks;

    public CameBall(CameVec at) {
        this.position = Objects.requireNonNull(at, "at");
    }

    public CameVec position() {
        return position;
    }

    public Mode mode() {
        return mode;
    }

    /** Mientras esta en la mano: sigue a la mano. */
    public void hold(CameVec at) {
        if (mode == Mode.HELD) {
            position = Objects.requireNonNull(at, "at");
        }
    }

    public void launch() {
        if (mode == Mode.HELD) {
            mode = Mode.INBOUND;
            ticks = 0;
        }
    }

    /** {@code true} si alguien todavia la puede devolver de un golpe. */
    public boolean returnable() {
        return mode == Mode.INBOUND;
    }

    /**
     * Un jugador le pego: sale en la direccion de {@code look} (ya corregida por {@link CameHoop#assist}).
     *
     * @return {@code false} si no estaba en juego para devolver
     */
    public boolean strike(CameVec look) {
        if (mode != Mode.INBOUND) {
            return false;
        }
        direction = look.normalize(direction.multiply(-1.0));
        mode = Mode.RETURNING;
        travelled = 0.0;
        return true;
    }

    /** {@code true} si {@code chest} esta lo bastante cerca para que la pelota lo golpee. */
    public boolean touches(CameVec chest) {
        return position.distance(chest) <= HIT_RADIUS;
    }

    /** Un tick de vuelo hacia {@code target} (el pecho del jugador) mientras va hacia el. */
    public Event advanceInbound(CameVec target, Terrain terrain) {
        if (mode != Mode.INBOUND) {
            return Event.NONE;
        }
        ticks++;
        CameVec offset = target.subtract(position);
        double distance = offset.length();
        if (distance <= HIT_RADIUS) {
            mode = Mode.DONE;
            return Event.IMPACT;
        }
        if (ticks > MAX_INBOUND_TICKS) {
            mode = Mode.DONE;
            return Event.DROPPED;
        }
        double speed = distance <= NEAR_DISTANCE ? NEAR_SPEED : FAR_SPEED;
        direction = offset.normalize(direction);
        CameVec next = position.add(direction.multiply(Math.min(speed, distance)));
        if (terrain.solid(next.x(), next.y(), next.z())) {
            mode = Mode.DONE;
            return Event.DROPPED;
        }
        position = next;
        if (position.distance(target) <= HIT_RADIUS) {
            mode = Mode.DONE;
            return Event.IMPACT;
        }
        return Event.NONE;
    }

    /** Un tick de vuelo de una pelota devuelta. */
    public Event advanceReturn(CameHoop hoop, int goals, Terrain terrain) {
        if (mode != Mode.RETURNING) {
            return Event.NONE;
        }
        CameVec next = position.add(direction.multiply(RETURN_SPEED));
        if (hoop.passes(position, next, goals)) {
            position = next;
            mode = Mode.DONE;
            return Event.GOAL;
        }
        travelled += RETURN_SPEED;
        if (terrain.solid(next.x(), next.y(), next.z()) || travelled > MAX_RETURN) {
            mode = Mode.DONE;
            return Event.MISSED;
        }
        position = next;
        return Event.NONE;
    }
}
