package dev.linqfy.bigCasares.modules.legendary;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

public final class LegendaryTargets {
    private LegendaryTargets() {}

    public static <T> List<T> select(Collection<T> candidates, Function<T, UUID> identity,
                                     ToDoubleFunction<T> distance, Predicate<T> allowed, int limit) {
        if (limit < 1) throw new IllegalArgumentException("El límite de objetivos debe ser positivo.");
        var seen = new HashSet<UUID>();
        return candidates.stream().filter(allowed).sorted(java.util.Comparator.comparingDouble(distance))
            .filter(target -> seen.add(identity.apply(target))).limit(limit).toList();
    }
}
