package dev.linqfy.bigCasares.modules.villagermood;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Queue;
import java.util.function.Consumer;

/** A bounded slice of a previously collected batch of main-thread work. */
final class VillagerWorkBatch<T> {
    private final Queue<T> pending = new ArrayDeque<>();

    void addAll(Collection<? extends T> entries) {
        pending.addAll(entries);
    }

    boolean isEmpty() {
        return pending.isEmpty();
    }

    void clear() {
        pending.clear();
    }

    void drain(int limit, Consumer<? super T> action) {
        for (int i = 0; i < limit && !pending.isEmpty(); i++) {
            action.accept(pending.remove());
        }
    }
}
