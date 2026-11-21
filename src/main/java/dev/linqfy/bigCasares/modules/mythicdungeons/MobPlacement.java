package dev.linqfy.bigCasares.modules.mythicdungeons;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.ToDoubleFunction;

/**
 * Donde puede aparecer (o reaparecer) un mob mitico sin quedar adentro de una pared (sin servidor). Un lugar es el
 * bloque de los pies: el mob queda centrado en el, toda su hitbox (ancho por alto) tiene que caer en aire y el bloque
 * de abajo tiene que ser piso.
 */
public final class MobPlacement {

    private static final double EDGE = 1.0E-6;

    /** El bloque de los pies de un lugar. */
    public record Spot(int x, int y, int z) {
    }

    /** El mob elegido y donde va. */
    public record Placement<T>(T mob, Spot spot) {
    }

    private MobPlacement() {
    }

    /** Si una hitbox de {@code width} x {@code height} centrada en el bloque entra ahi parada sobre piso. */
    public static boolean fits(BlockGrid grid, Spot spot, double width, double height) {
        if (!grid.solid(spot.x(), spot.y() - 1, spot.z())) {
            return false;
        }
        double half = width / 2.0;
        int minX = (int) Math.floor(spot.x() + 0.5 - half);
        int maxX = (int) Math.floor(spot.x() + 0.5 + half - EDGE);
        int minZ = (int) Math.floor(spot.z() + 0.5 - half);
        int maxZ = (int) Math.floor(spot.z() + 0.5 + half - EDGE);
        int top = spot.y() + Math.max(1, (int) Math.ceil(height - EDGE)) - 1;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = spot.y(); y <= top; y++) {
                    if (grid.solid(x, y, z)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * El lugar mas cercano a {@code preferred} donde entra la hitbox, buscando en la caja de {@code range} bloques
     * horizontales y {@code vertical} verticales alrededor de {@code center}.
     */
    public static Optional<Spot> nearestFit(BlockGrid grid, Spot preferred, Spot center, int range, int vertical,
                                            double width, double height) {
        if (within(preferred, center, range, vertical) && fits(grid, preferred, width, height)) {
            return Optional.of(preferred);
        }
        List<Spot> candidates = new ArrayList<>();
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                for (int dy = -vertical; dy <= vertical; dy++) {
                    candidates.add(new Spot(center.x() + dx, center.y() + dy, center.z() + dz));
                }
            }
        }
        candidates.sort(Comparator.comparingDouble((Spot spot) -> distanceSquared(spot, preferred))
            .thenComparingInt(Spot::y).thenComparingInt(Spot::x).thenComparingInt(Spot::z));
        for (Spot spot : candidates) {
            if (fits(grid, spot, width, height)) {
                return Optional.of(spot);
            }
        }
        return Optional.empty();
    }

    /**
     * El mob que nace de un spawner: primero el del sorteo con pesos ({@code roll} uniforme en [0, 1)); si no entra
     * en ningun lugar del alcance, los demas del grupo de mas chico a mas grande. Vacio si ninguno entra: ese intento
     * del spawner se pierde en vez de meter un mob en la pared.
     */
    public static Optional<Placement<MythicMobType>> place(List<MythicMobType> pool, double roll, BlockGrid grid,
                                                           Spot preferred, Spot center, int range, int vertical) {
        return place(pool, MythicMobType::weight, MythicMobType::hitboxWidth, MythicMobType::hitboxHeight, roll,
            grid, preferred, center, range, vertical);
    }

    static <T> Optional<Placement<T>> place(List<T> pool, ToDoubleFunction<T> weight, ToDoubleFunction<T> width,
                                            ToDoubleFunction<T> height, double roll, BlockGrid grid, Spot preferred,
                                            Spot center, int range, int vertical) {
        Optional<T> first = WeightedPicker.pick(pool, weight, roll);
        if (first.isEmpty()) {
            return Optional.empty();
        }
        List<T> order = new ArrayList<>();
        order.add(first.get());
        pool.stream()
            .filter(candidate -> candidate != first.get() && weight.applyAsDouble(candidate) > 0.0)
            .sorted(Comparator.comparingDouble((T candidate) -> width.applyAsDouble(candidate)
                * width.applyAsDouble(candidate) * height.applyAsDouble(candidate)))
            .forEach(order::add);
        for (T candidate : order) {
            Optional<Spot> spot = nearestFit(grid, preferred, center, range, vertical,
                width.applyAsDouble(candidate), height.applyAsDouble(candidate));
            if (spot.isPresent()) {
                return Optional.of(new Placement<>(candidate, spot.get()));
            }
        }
        return Optional.empty();
    }

    private static boolean within(Spot spot, Spot center, int range, int vertical) {
        return Math.abs(spot.x() - center.x()) <= range && Math.abs(spot.z() - center.z()) <= range
            && Math.abs(spot.y() - center.y()) <= vertical;
    }

    private static double distanceSquared(Spot a, Spot b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
