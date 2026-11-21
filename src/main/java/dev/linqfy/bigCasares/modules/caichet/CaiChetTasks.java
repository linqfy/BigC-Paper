package dev.linqfy.bigCasares.modules.caichet;

import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import org.bukkit.scheduler.BukkitTask;

/**
 * Las tareas del servidor del modulo: todas pasan por el scope de BigCasares, asi al apagar o recargar el modulo se
 * cancelan las que todavia no corrieron (y un callback de una corrida vieja no toca el modulo nuevo).
 */
final class CaiChetTasks {

    private final BukkitRuntimeRegistrations registrations;

    CaiChetTasks(BukkitRuntimeRegistrations registrations) {
        this.registrations = registrations;
    }

    /** Corre en el proximo tick. */
    void next(Runnable task) {
        registrations.scheduleImmediate("caichet-next", task);
    }

    /** Corre despues de delayTicks. */
    void later(Runnable task, long delayTicks) {
        registrations.scheduleDelayed("caichet-later", task, delayTicks);
    }

    /** Corre cada periodTicks (empieza despues de delayTicks) hasta que se cancele la tarea o se apague el modulo. */
    BukkitTask repeating(String id, Runnable task, long delayTicks, long periodTicks) {
        return registrations.scheduleRepeating("caichet-" + id, task, delayTicks, periodTicks);
    }
}
