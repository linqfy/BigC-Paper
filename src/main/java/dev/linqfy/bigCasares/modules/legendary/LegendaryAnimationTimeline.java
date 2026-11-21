package dev.linqfy.bigCasares.modules.legendary;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Visual playback only. Slot changes and transaction pauses invalidate old clips. */
final class LegendaryAnimationTimeline {
    private static final class State {
        final LegendaryWeapon weapon;
        final int slot;
        final long idleStart;
        List<LegendaryAnimationCatalog.Clip> active = List.of();
        long start;
        int priority;

        State(LegendaryWeapon weapon, int slot, long tick) {
            this.weapon = weapon;
            this.slot = slot;
            this.idleStart = tick;
        }

        long duration() {
            return active.stream().mapToLong(LegendaryAnimationCatalog.Clip::duration).sum();
        }
    }

    private final LegendaryAnimationCatalog catalog;
    private final Map<UUID, State> states = new HashMap<>();

    LegendaryAnimationTimeline(LegendaryAnimationCatalog catalog) {
        this.catalog = catalog;
    }

    private State state(UUID player, LegendaryWeapon weapon, int slot, long tick) {
        State state = states.get(player);
        if (state == null || state.weapon != weapon || state.slot != slot) {
            state = new State(weapon, slot, tick);
            states.put(player, state);
        }
        return state;
    }

    int frame(UUID player, LegendaryWeapon weapon, int slot, long tick) {
        return frame(player, weapon, slot, tick, true);
    }

    int frame(UUID player, LegendaryWeapon weapon, int slot, long tick, boolean idleEnabled) {
        if (!weapon.isAvailable()) {
            forget(player);
            return 0;
        }
        State state = state(player, weapon, slot, tick);
        long age = Math.max(0, tick - state.start);
        if (age < state.duration()) {
            for (var clip : state.active) {
                if (age < clip.duration()) return clip.frame(age);
                age -= clip.duration();
            }
        }
        state.active = List.of();
        state.priority = 0;
        if (!idleEnabled) return 0;
        var idle = catalog.clip(weapon, catalog.idle(weapon));
        return idle == null ? 0 : idle.frame(Math.max(0, tick - state.idleStart));
    }

    void play(
            UUID player,
            LegendaryWeapon weapon,
            int slot,
            long tick,
            int priority,
            String... names) {
        if (!weapon.isAvailable()) return;
        State state = state(player, weapon, slot, tick);
        if (priority < state.priority && tick - state.start < state.duration()) return;
        var clips =
                Arrays.stream(names)
                        .map(name -> catalog.clip(weapon, name))
                        .filter(java.util.Objects::nonNull)
                        .toList();
        if (clips.isEmpty()) return;
        state.active = clips;
        state.start = tick;
        state.priority = priority;
    }

    void forget(UUID player) {
        states.remove(player);
    }

    void clear() {
        states.clear();
    }
}
