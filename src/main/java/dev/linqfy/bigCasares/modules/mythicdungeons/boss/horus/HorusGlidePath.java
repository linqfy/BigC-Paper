package dev.linqfy.bigCasares.modules.mythicdungeons.boss.horus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * El planeo de Horus: baja en picada desde el aire ({@code start}) hasta casi el piso en el punto marcado
 * ({@code low}, donde estaba el objetivo) y sigue rasante, frenando, unos bloques mas ({@code end}). Todo queda
 * adentro del circulo de la arena. Puro: el encuentro lo usa tick a tick para mover al Warden.
 */
public record HorusGlidePath(HorusPoint start, HorusPoint low, HorusPoint end, int diveTicks, int skimTicks) {

    /** Velocidad de la picada (bloques por tick). */
    public static final double DIVE_SPEED = 0.8;
    /** Velocidad del tramo rasante: mas lento, para que se lo pueda golpear al pasar. */
    public static final double SKIM_SPEED = 0.45;
    /** Altura de los pies del Warden sobre el piso en el punto mas bajo (el cuerpo barre a los jugadores). */
    public static final double LOW_HEIGHT = 0.4;
    /** Altura sobre el piso al terminar el tramo rasante. */
    public static final double END_HEIGHT = 3.0;
    /** Cuanto sigue de largo despues del punto marcado. */
    public static final double OVERSHOOT = 7.0;
    /** Radio horizontal del golpe del cuerpo. */
    public static final double HIT_RADIUS = 1.7;
    /** Alto del cuerpo que golpea (el del Warden). */
    public static final double BODY_HEIGHT = 2.9;

    public HorusGlidePath {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(low, "low");
        Objects.requireNonNull(end, "end");
        if (diveTicks < 1 || skimTicks < 1) {
            throw new IllegalArgumentException("glide segments need at least one tick");
        }
    }

    /**
     * Planea un planeo hacia {@code target}.
     *
     * @param start  donde arranca (en el aire)
     * @param target donde esta el objetivo (solo importan x y z)
     * @param center centro de la arena
     * @param radius radio horizontal de la arena que no se pasa
     * @param floorY altura del piso de la arena
     */
    public static HorusGlidePath plan(HorusPoint start, HorusPoint target, HorusPoint center, double radius,
                                      double floorY) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(center, "center");
        if (!(radius > 0.0)) {
            throw new IllegalArgumentException("radius must be positive");
        }
        HorusPoint low = clampInside(target, center, radius).withY(floorY + LOW_HEIGHT);
        double dx = low.x() - start.x();
        double dz = low.z() - start.z();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-3) {
            // ya esta encima del objetivo: sigue hacia el centro (o hacia el norte si esta en el centro)
            dx = center.x() - low.x();
            dz = center.z() - low.z();
            length = Math.sqrt(dx * dx + dz * dz);
            if (length < 1.0E-3) {
                dx = 0.0;
                dz = -1.0;
                length = 1.0;
            }
        }
        HorusPoint end = clampInside(low.plus(dx / length * OVERSHOOT, 0.0, dz / length * OVERSHOOT), center, radius)
            .withY(floorY + END_HEIGHT);
        int dive = Math.max(1, (int) Math.ceil(start.distance(low) / DIVE_SPEED));
        int skim = Math.max(1, (int) Math.ceil(low.distance(end) / SKIM_SPEED));
        return new HorusGlidePath(start, low, end, dive, skim);
    }

    /** El punto mas cercano a {@code point} dentro del circulo horizontal (conserva la altura). */
    public static HorusPoint clampInside(HorusPoint point, HorusPoint center, double radius) {
        double distance = point.horizontalDistance(center);
        if (distance <= radius) {
            return point;
        }
        double factor = radius / distance;
        return new HorusPoint(center.x() + (point.x() - center.x()) * factor, point.y(),
            center.z() + (point.z() - center.z()) * factor);
    }

    public int totalTicks() {
        return diveTicks + skimTicks;
    }

    /** Donde esta Horus {@code tick} ticks despues de arrancar (fuera de rango: el extremo mas cercano). */
    public HorusPoint positionAt(int tick) {
        if (tick <= 0) {
            return start;
        }
        if (tick <= diveTicks) {
            return start.lerp(low, (double) tick / diveTicks);
        }
        if (tick >= totalTicks()) {
            return end;
        }
        double t = (double) (tick - diveTicks) / skimTicks;
        HorusPoint flat = low.lerp(end, t);
        // queda rasante casi todo el tramo y recien al final remonta
        return flat.withY(low.y() + (end.y() - low.y()) * t * t);
    }

    /** Puntos en el piso a lo largo del recorrido, cada {@code spacing} bloques (para el aviso). */
    public List<HorusPoint> groundMarks(double floorY, double spacing) {
        if (!(spacing > 0.0)) {
            throw new IllegalArgumentException("spacing must be positive");
        }
        List<HorusPoint> marks = new ArrayList<>();
        addSegment(marks, start.withY(floorY), low.withY(floorY), spacing);
        addSegment(marks, low.withY(floorY), end.withY(floorY), spacing);
        marks.add(end.withY(floorY));
        return marks;
    }

    /** Si el cuerpo de Horus, con los pies en {@code boss}, toca a un jugador con los pies en {@code player}. */
    public static boolean bodyHits(HorusPoint boss, HorusPoint player) {
        return boss.horizontalDistance(player) <= HIT_RADIUS
            && player.y() >= boss.y() - 1.6
            && player.y() <= boss.y() + BODY_HEIGHT;
    }

    private static void addSegment(List<HorusPoint> marks, HorusPoint from, HorusPoint to, double spacing) {
        double length = from.distance(to);
        int steps = (int) Math.floor(length / spacing);
        for (int i = 0; i < steps; i++) {
            marks.add(from.lerp(to, i * spacing / length));
        }
    }
}
