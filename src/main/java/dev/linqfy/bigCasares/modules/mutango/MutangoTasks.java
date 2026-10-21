package dev.linqfy.bigCasares.modules.mutango;

import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import org.bukkit.scheduler.BukkitTask;

/**
 * Las tareas del servidor del modulo: todas pasan por el scope de BigCasares, asi al apagar o recargar el modulo se
 * cancelan las que todavia no corrieron (y un callback de una corrida vieja no toca el modulo nuevo).
 */
final class MutangoTasks {

    private final BukkitRuntimeRegistrations registrations;

    MutangoTasks(BukkitRuntimeRegistrations registrations) {
        this.registrations = registrations;
    }

    /** Corre en el proximo tick. */
    void next(String id, Runnable task) {
        registrations.scheduleImmediate("mutango-" + id, task);
    }

    /** Corre despues de delayTicks. */
    void later(String id, Runnable task, long delayTicks) {
        registrations.scheduleDelayed("mutango-" + id, task, delayTicks);
    }

    /** Corre cada periodTicks (empieza despues de delayTicks) hasta que se cancele la tarea o se apague el modulo. */
    BukkitTask repeating(String id, Runnable task, long delayTicks, long periodTicks) {
        return registrations.scheduleRepeating("mutango-" + id, task, delayTicks, periodTicks);
    }
}
