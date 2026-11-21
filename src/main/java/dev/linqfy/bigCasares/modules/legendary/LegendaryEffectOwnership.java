package dev.linqfy.bigCasares.modules.legendary;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Own temporary potion changes without erasing stronger or longer external effects. */
final class LegendaryEffectOwnership {
    record Effect(int duration, int amplifier, boolean ambient, boolean particles, boolean icon) {
        Effect remaining(int elapsed) {
            if (duration < 0) return this;
            return new Effect(Math.max(0, duration - elapsed), amplifier, ambient, particles, icon);
        }
    }
    interface Gateway {
        Optional<Effect> current(UUID target, String type);
        boolean apply(UUID target, String type, Effect effect);
        void remove(UUID target, String type);
    }
    private record Key(UUID target, String type) {}
    private record Lease(Effect previous, int previousAt, Effect applied, int appliedAt) {}
    private final Gateway gateway;
    private final Map<Key, Lease> leases = new HashMap<>();

    LegendaryEffectOwnership(Gateway gateway) { this.gateway = gateway; }

    void apply(UUID target, String type, Effect effect, int age) {
        Key key = new Key(target, type);
        Effect previous = gateway.current(target, type).orElse(null);
        Lease prior = leases.get(key);
        boolean refresh = prior != null && previous != null && owns(prior, previous, age);
        if (!gateway.apply(target, type, effect)) return;
        leases.put(key, new Lease(refresh ? prior.previous() : previous,
            refresh ? prior.previousAt() : age, effect, age));
    }

    void prune(int age) {
        leases.entrySet().removeIf(entry -> {
            Optional<Effect> active = gateway.current(entry.getKey().target(), entry.getKey().type());
            Lease lease = entry.getValue();
            return active.map(effect -> !owns(lease, effect, age))
                .orElse(age < lease.appliedAt() + lease.applied().duration());
        });
    }

    void forget(UUID target) {
        leases.keySet().removeIf(key -> key.target().equals(target));
    }

    void cleanup(int age) {
        leases.forEach((key, lease) -> {
            Optional<Effect> active = gateway.current(key.target(), key.type());
            boolean expired = age >= lease.appliedAt() + lease.applied().duration();
            if (active.map(effect -> owns(lease, effect, age)).orElse(expired)) {
                if (active.isPresent()) gateway.remove(key.target(), key.type());
                if (lease.previous() != null) {
                    Effect previous = lease.previous().remaining(age - lease.previousAt());
                    if (previous.duration() != 0) gateway.apply(key.target(), key.type(), previous);
                }
            }
        });
        leases.clear();
    }

    private static boolean owns(Lease lease, Effect current, int age) {
        Effect owned = lease.applied();
        return current.amplifier() == owned.amplifier()
            && current.duration() >= 0
            && current.ambient() == owned.ambient() && current.particles() == owned.particles()
            && current.icon() == owned.icon()
            && current.duration() <= Math.max(0, owned.duration() - (age - lease.appliedAt())) + 1;
    }
}
