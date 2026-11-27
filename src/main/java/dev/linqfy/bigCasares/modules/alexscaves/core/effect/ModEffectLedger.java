package dev.linqfy.bigCasares.modules.alexscaves.core.effect;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Active module effects per entity. Stacking follows vanilla: a higher
 * amplifier replaces the current one, and an equal amplifier only extends the
 * duration.
 */
public final class ModEffectLedger {
    private final Map<UUID, EnumMap<ModEffectType, ActiveEffect>> active = new HashMap<>();

    public boolean apply(UUID entity, ModEffectType type, int amplifier, int durationTicks) {
        if (durationTicks <= 0 || amplifier < 0) {
            return false;
        }
        EnumMap<ModEffectType, ActiveEffect> effects = active.computeIfAbsent(entity, ignored -> new EnumMap<>(ModEffectType.class));
        ActiveEffect current = effects.get(type);
        if (current != null) {
            if (current.amplifier() > amplifier) {
                return false;
            }
            if (current.amplifier() == amplifier && current.remainingTicks() >= durationTicks) {
                return false;
            }
        }
        int elapsed = current == null ? 0 : current.elapsedTicks();
        effects.put(type, new ActiveEffect(type, amplifier, durationTicks, elapsed));
        return true;
    }

    public Optional<ActiveEffect> get(UUID entity, ModEffectType type) {
        EnumMap<ModEffectType, ActiveEffect> effects = active.get(entity);
        return effects == null ? Optional.empty() : Optional.ofNullable(effects.get(type));
    }

    public boolean has(UUID entity, ModEffectType type) {
        return get(entity, type).isPresent();
    }

    public List<ActiveEffect> effectsOf(UUID entity) {
        EnumMap<ModEffectType, ActiveEffect> effects = active.get(entity);
        return effects == null ? List.of() : List.copyOf(effects.values());
    }

    public boolean remove(UUID entity, ModEffectType type) {
        EnumMap<ModEffectType, ActiveEffect> effects = active.get(entity);
        if (effects == null || effects.remove(type) == null) {
            return false;
        }
        if (effects.isEmpty()) {
            active.remove(entity);
        }
        return true;
    }

    /** Removes the milk-curable effects; returns the removed types. */
    public List<ModEffectType> cureWithMilk(UUID entity) {
        List<ModEffectType> removed = new ArrayList<>();
        EnumMap<ModEffectType, ActiveEffect> effects = active.get(entity);
        if (effects == null) {
            return removed;
        }
        effects.keySet().removeIf(type -> {
            boolean curable = type.curableByMilk();
            if (curable) {
                removed.add(type);
            }
            return curable;
        });
        if (effects.isEmpty()) {
            active.remove(entity);
        }
        return removed;
    }

    public void forget(UUID entity) {
        active.remove(entity);
    }

    public Set<UUID> entities() {
        return Set.copyOf(active.keySet());
    }

    /** Puts a stored effect back as it was (used when a player joins). */
    public void restore(UUID entity, ActiveEffect effect) {
        if (effect.remainingTicks() > 0 && effect.amplifier() >= 0) {
            active.computeIfAbsent(entity, ignored -> new EnumMap<>(ModEffectType.class)).put(effect.type(), effect);
        }
    }

    /** Advances every effect by {@code ticks}; returns the effects that expired. */
    public List<ExpiredEffect> tick(int ticks) {
        return tick(ticks, ignored -> true);
    }

    /** Advances only the entities accepted by {@code advancing} (offline players stay paused). */
    public List<ExpiredEffect> tick(int ticks, Predicate<UUID> advancing) {
        List<ExpiredEffect> expired = new ArrayList<>();
        Iterator<Map.Entry<UUID, EnumMap<ModEffectType, ActiveEffect>>> owners = active.entrySet().iterator();
        while (owners.hasNext()) {
            Map.Entry<UUID, EnumMap<ModEffectType, ActiveEffect>> owner = owners.next();
            if (!advancing.test(owner.getKey())) {
                continue;
            }
            EnumMap<ModEffectType, ActiveEffect> effects = owner.getValue();
            Iterator<Map.Entry<ModEffectType, ActiveEffect>> iterator = effects.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<ModEffectType, ActiveEffect> entry = iterator.next();
                ActiveEffect next = entry.getValue().advance(ticks);
                if (next.remainingTicks() <= 0) {
                    expired.add(new ExpiredEffect(owner.getKey(), next));
                    iterator.remove();
                } else {
                    entry.setValue(next);
                }
            }
            if (effects.isEmpty()) {
                owners.remove();
            }
        }
        return expired;
    }

    public record ExpiredEffect(UUID entity, ActiveEffect effect) {
    }

    public record ActiveEffect(ModEffectType type, int amplifier, int remainingTicks, int elapsedTicks) {
        ActiveEffect advance(int ticks) {
            return new ActiveEffect(type, amplifier, remainingTicks - ticks, elapsedTicks + ticks);
        }
    }
}
