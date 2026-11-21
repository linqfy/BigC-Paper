package dev.linqfy.bigCasares.modules.mythicdungeons.boss.iblis;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Attacks announced on the ground that land a fixed number of ticks later, without server types. Every dangerous
 * attack of Iblis goes through here, so none lands before its warning ran for at least {@link #MINIMUM_WARNING}.
 */
public final class IblisTelegraphs<T> {

    /** About 0.8 s at 20 ticks per second. */
    public static final int MINIMUM_WARNING = 16;

    public record Pending<T>(T payload, int castTick, int landTick) {

        /** 0 at the cast, 1 when it lands. */
        public double progress(int tick) {
            int span = Math.max(1, landTick - castTick);
            return Math.max(0.0, Math.min(1.0, (tick - castTick) / (double) span));
        }
    }

    private final List<Pending<T>> pending = new ArrayList<>();

    /** Announces {@code payload}; it lands after {@code warning} ticks (never less than {@link #MINIMUM_WARNING}). */
    public Pending<T> add(T payload, int tick, int warning) {
        Pending<T> entry = new Pending<>(Objects.requireNonNull(payload, "payload"), tick,
            tick + Math.max(MINIMUM_WARNING, warning));
        pending.add(entry);
        return entry;
    }

    /** What is still being announced. */
    public List<Pending<T>> pending() {
        return List.copyOf(pending);
    }

    /** Removes and returns what lands this tick. */
    public List<T> land(int tick) {
        List<T> landed = new ArrayList<>();
        Iterator<Pending<T>> iterator = pending.iterator();
        while (iterator.hasNext()) {
            Pending<T> entry = iterator.next();
            if (tick >= entry.landTick()) {
                landed.add(entry.payload());
                iterator.remove();
            }
        }
        return landed;
    }

    public boolean isEmpty() {
        return pending.isEmpty();
    }

    public void clear() {
        pending.clear();
    }
}
