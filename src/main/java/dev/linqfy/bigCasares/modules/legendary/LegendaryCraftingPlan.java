package dev.linqfy.bigCasares.modules.legendary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A vanilla nine-slot recipe consumes one unit from each occupied slot. */
final class LegendaryCraftingPlan {
    interface Items<T> {
        String material(T item);
        int amount(T item);
        int maximum(T item);
        boolean eligible(T item);
        boolean similar(T first, T second);
        T amount(T item, int amount);
    }
    record Plan<T>(List<T> before, List<T> after) { }
    private LegendaryCraftingPlan() { }

    static <T> Plan<T> create(LegendaryWeapon weapon, List<T> storage, List<T> matrix, Items<T> items) {
        if (!weapon.isAvailable()) throw new IllegalArgumentException("Esta arma fue retirada.");
        if (matrix.size() != 9) throw new IllegalArgumentException("Usa una mesa de crafteo normal.");
        var counts = new java.util.HashMap<String, Integer>();
        for (T item : matrix) {
            if (item == null || items.amount(item) <= 0 || !items.eligible(item)) {
                throw new IllegalArgumentException("La receta necesita ingredientes vanilla.");
            }
            counts.merge(items.material(item), 1, Integer::sum);
        }
        if (!counts.equals(weapon.ingredients)) throw new IllegalArgumentException("La receta no corresponde a esta arma.");
        var before = new ArrayList<>(storage);
        var after = new ArrayList<>(storage);
        for (T item : matrix) {
            insert(before, item, items.amount(item), items);
            insert(after, item, items.amount(item) - 1, items);
        }
        return new Plan<>(Collections.unmodifiableList(before), Collections.unmodifiableList(after));
    }

    private static <T> void insert(List<T> storage, T item, int remaining, Items<T> items) {
        for (int slot = 0; slot < storage.size() && remaining > 0; slot++) {
            T existing = storage.get(slot);
            if (existing == null || !items.similar(existing, item)) continue;
            int added = Math.min(remaining, Math.max(0, items.maximum(existing) - items.amount(existing)));
            if (added > 0) storage.set(slot, items.amount(existing, items.amount(existing) + added));
            remaining -= added;
        }
        for (int slot = 0; slot < storage.size() && remaining > 0; slot++) {
            if (storage.get(slot) != null) continue;
            int added = Math.min(remaining, items.maximum(item));
            storage.set(slot, items.amount(item, added));
            remaining -= added;
        }
        if (remaining > 0) throw new IllegalArgumentException("Libera espacio para devolver los materiales de la mesa sin tirarlos al suelo.");
    }
}
