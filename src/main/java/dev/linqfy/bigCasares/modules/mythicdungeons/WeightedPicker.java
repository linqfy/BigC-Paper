package dev.linqfy.bigCasares.modules.mythicdungeons;

import java.util.List;
import java.util.Optional;
import java.util.function.ToDoubleFunction;

/** Sorteo con pesos (sin servidor): {@code roll} es una tirada uniforme en [0, 1). */
public final class WeightedPicker {

    private WeightedPicker() {
    }

    /** Elige un elemento proporcional a su peso; los pesos no positivos o no finitos nunca salen. */
    public static <T> Optional<T> pick(List<T> options, ToDoubleFunction<T> weight, double roll) {
        double total = 0.0;
        for (T option : options) {
            total += usable(weight.applyAsDouble(option));
        }
        if (!(total > 0.0)) {
            return Optional.empty();
        }
        double target = Math.max(0.0, Math.min(roll, Math.nextDown(1.0))) * total;
        T last = null;
        for (T option : options) {
            double value = usable(weight.applyAsDouble(option));
            if (value <= 0.0) {
                continue;
            }
            last = option;
            if (target < value) {
                return Optional.of(option);
            }
            target -= value;
        }
        return Optional.ofNullable(last);
    }

    private static double usable(double weight) {
        return Double.isFinite(weight) && weight > 0.0 ? weight : 0.0;
    }
}
