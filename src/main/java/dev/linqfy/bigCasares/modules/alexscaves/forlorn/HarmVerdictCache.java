package dev.linqfy.bigCasares.modules.alexscaves.forlorn;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Remembers for a while whether an owner may hurt a target, so protections are not asked on every tick. */
public final class HarmVerdictCache {
    static final long TTL_TICKS = 40;
    private static final int PRUNE_SIZE = 256;

    private record Key(UUID owner, UUID target) {
    }

    private record Verdict(boolean allowed, long expiresAt) {
    }

    private final Map<Key, Verdict> verdicts = new HashMap<>();

    /** The cached verdict while it is fresh; otherwise {@code check} is asked and its answer kept. */
    public boolean allowed(UUID owner, UUID target, long now, BooleanSupplier check) {
        Key key = new Key(owner, target);
        Verdict known = verdicts.get(key);
        if (known != null && known.expiresAt() > now) {
            return known.allowed();
        }
        if (verdicts.size() >= PRUNE_SIZE) {
            verdicts.values().removeIf(verdict -> verdict.expiresAt() <= now);
        }
        boolean allowed = check.getAsBoolean();
        verdicts.put(key, new Verdict(allowed, now + TTL_TICKS));
        return allowed;
    }

    /** Asks again right now and keeps the answer. */
    boolean refresh(UUID owner, UUID target, long now, BooleanSupplier check) {
        verdicts.remove(new Key(owner, target));
        return allowed(owner, target, now, check);
    }

    void clear() {
        verdicts.clear();
    }

    int size() {
        return verdicts.size();
    }
}
