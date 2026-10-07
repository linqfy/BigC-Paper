package dev.linqfy.bigCasares.modules.donpollos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntUnaryOperator;

/** Reglas de la ruleta de Don Pollo Fino (sin Bukkit). */
public final class DonPollosRoulette {

    /** Casillas del anillo: 2 verdes enfrentadas y rojo/negro alternados. */
    public static final int POCKETS = 26;
    static final int FULL_LAPS = 2;

    private final Map<DonPollosRouletteColor, Integer> weights;
    private final Map<DonPollosRouletteColor, Integer> multipliers;
    private final int totalWeight;
    private final List<DonPollosRouletteColor> wheel;

    public DonPollosRoulette(Map<DonPollosRouletteColor, Integer> weights,
                             Map<DonPollosRouletteColor, Integer> multipliers) {
        Objects.requireNonNull(weights, "weights");
        Objects.requireNonNull(multipliers, "multipliers");
        Map<DonPollosRouletteColor, Integer> w = new EnumMap<>(DonPollosRouletteColor.class);
        Map<DonPollosRouletteColor, Integer> m = new EnumMap<>(DonPollosRouletteColor.class);
        int total = 0;
        for (DonPollosRouletteColor color : DonPollosRouletteColor.values()) {
            Integer weight = weights.get(color);
            Integer multiplier = multipliers.get(color);
            if (weight == null || weight < 1) {
                throw new IllegalArgumentException("roulette weight for " + color.id() + " must be positive");
            }
            if (multiplier == null || multiplier < 1) {
                throw new IllegalArgumentException("roulette multiplier for " + color.id() + " must be at least 1");
            }
            w.put(color, weight);
            m.put(color, multiplier);
            total += weight;
        }
        this.weights = Collections.unmodifiableMap(w);
        this.multipliers = Collections.unmodifiableMap(m);
        this.totalWeight = total;
        this.wheel = buildWheel();
    }

    public static DonPollosRoulette standard() {
        return new DonPollosRoulette(
            Map.of(DonPollosRouletteColor.RED, 24, DonPollosRouletteColor.BLACK, 24, DonPollosRouletteColor.GREEN, 2),
            Map.of(DonPollosRouletteColor.RED, 2, DonPollosRouletteColor.BLACK, 2, DonPollosRouletteColor.GREEN, 2));
    }

    public int weight(DonPollosRouletteColor color) {
        return weights.get(color);
    }

    public int multiplier(DonPollosRouletteColor color) {
        return multipliers.get(color);
    }

    public int totalWeight() {
        return totalWeight;
    }

    public List<DonPollosRouletteColor> wheel() {
        return wheel;
    }

    /** randomBelow(n) devuelve un entero en [0, n). */
    public DonPollosRouletteColor spin(IntUnaryOperator randomBelow) {
        int roll = randomBelow.applyAsInt(totalWeight);
        int cumulative = 0;
        for (DonPollosRouletteColor color : DonPollosRouletteColor.values()) {
            cumulative += weights.get(color);
            if (roll < cumulative) {
                return color;
            }
        }
        return DonPollosRouletteColor.GREEN;
    }

    public int payout(int betAmount, DonPollosRouletteColor chosen, DonPollosRouletteColor result) {
        return chosen == result ? betAmount * multipliers.get(result) : 0;
    }

    /** Elige al azar una casilla del anillo con el color que salio. */
    public int stopPocket(DonPollosRouletteColor result, IntUnaryOperator randomBelow) {
        List<Integer> candidates = new ArrayList<>();
        for (int index = 0; index < wheel.size(); index++) {
            if (wheel.get(index) == result) {
                candidates.add(index);
            }
        }
        return candidates.get(randomBelow.applyAsInt(candidates.size()));
    }

    /**
     * Demoras (en ticks) de cada paso de la bola: arranca rapido y frena al final.
     * La bola sale de la casilla 0 y despues de todos los pasos queda en stopPocket.
     */
    public static List<Integer> stepDelays(int stopPocket) {
        int steps = FULL_LAPS * POCKETS + Math.floorMod(stopPocket, POCKETS);
        List<Integer> delays = new ArrayList<>(steps);
        for (int step = 0; step < steps; step++) {
            double progress = step / (double) Math.max(1, steps - 1);
            delays.add(1 + (int) Math.round(Math.pow(progress, 3) * 9));
        }
        return delays;
    }

    private static List<DonPollosRouletteColor> buildWheel() {
        List<DonPollosRouletteColor> pockets = new ArrayList<>(POCKETS);
        int half = POCKETS / 2;
        for (int index = 0; index < POCKETS; index++) {
            int offset = index % half;
            if (offset == 0) {
                pockets.add(DonPollosRouletteColor.GREEN);
            } else {
                pockets.add(offset % 2 == 1 ? DonPollosRouletteColor.RED : DonPollosRouletteColor.BLACK);
            }
        }
        return List.copyOf(pockets);
    }
}
