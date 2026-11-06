package dev.linqfy.bigCasares.modules.mutango;

import java.util.function.LongSupplier;

/**
 * El tiempo que una tarea puede usar en un tick (sin Bukkit): se mide en reloj y no en cantidad de bloques, asi el costo
 * en el hilo principal es parejo aunque cada operacion salga mas cara o mas barata. El reloj se mira cada tantas
 * operaciones (leerlo en cada bloque costaria casi tanto como el bloque).
 */
final class MutangoWorkBudget {

    /** Cada cuantas operaciones se mira el reloj. */
    static final int CHECK_EVERY = 64;

    private final LongSupplier clock;
    private final long deadline;
    private int sinceCheck;
    private boolean exhausted;

    MutangoWorkBudget(long nanos, LongSupplier clock) {
        this.clock = clock;
        this.deadline = clock.getAsLong() + nanos;
        this.exhausted = nanos <= 0;
    }

    /** Se hicieron ops operaciones; cada tanto se mira si se acabo el tiempo. */
    void charge(int ops) {
        sinceCheck += ops;
        if (sinceCheck >= CHECK_EVERY) {
            sinceCheck = 0;
            if (clock.getAsLong() >= deadline) {
                exhausted = true;
            }
        }
    }

    boolean exhausted() {
        return exhausted;
    }
}
