package dev.linqfy.bigCasares.modules.mythicdungeons;

import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;

/**
 * Las tareas del modulo y de los encuentros de los jefes: todas pasan por el scope de BigCasares, asi al apagar o
 * recargar el modulo se cancelan las pendientes y un callback de una corrida vieja no toca la nueva.
 * Todo corre en el hilo del servidor.
 */
public final class MythicScheduler {

    private final BukkitRuntimeRegistrations registrations;

    public MythicScheduler(BukkitRuntimeRegistrations registrations) {
        this.registrations = Objects.requireNonNull(registrations, "registrations");
    }

    /** Corre en el proximo tick. */
    public BukkitTask next(String id, Runnable task) {
        return registrations.scheduleImmediate("mythic-" + id, task);
    }

    /** Corre una vez despues de {@code delayTicks}. */
    public BukkitTask later(String id, long delayTicks, Runnable task) {
        return registrations.scheduleDelayed("mythic-" + id, task, Math.max(0L, delayTicks));
    }

    /**
     * Corre cada {@code periodTicks} desde {@code delayTicks} hasta que se cancele la tarea devuelta o se apague el
     * modulo. Un encuentro debe cancelar las suyas en {@code stop()}.
     */
    public BukkitTask repeating(String id, long delayTicks, long periodTicks, Runnable task) {
        return registrations.scheduleRepeating("mythic-" + id, task, Math.max(0L, delayTicks),
            Math.max(1L, periodTicks));
    }
}
