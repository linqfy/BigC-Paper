package dev.linqfy.bigCasares.modules.alexscaves.toxic;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Shared holds on chunk tickets. A plugin has only one ticket per chunk, so holders are counted here: the ticket is
 * added by the first holder and removed by the last one, and only when adding it created it (a ticket another part
 * of the plugin already had stays).
 */
final class ChunkHolds<K> {
    private static final class Hold {
        int holders;
        boolean ours;
    }

    private final Map<K, Hold> holds = new HashMap<>();

    /** Adds a holder; {@code add} runs only for the first and tells whether it created the ticket. */
    void hold(K chunk, BooleanSupplier add) {
        Hold hold = holds.computeIfAbsent(chunk, key -> new Hold());
        if (hold.holders++ == 0) {
            hold.ours = add.getAsBoolean();
        }
    }

    /** Drops a holder; {@code remove} runs when the last one leaves a ticket created here. */
    void release(K chunk, Runnable remove) {
        Hold hold = holds.get(chunk);
        if (hold == null || --hold.holders > 0) {
            return;
        }
        holds.remove(chunk);
        if (hold.ours) {
            remove.run();
        }
    }
}
