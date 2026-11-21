package dev.linqfy.bigCasares.modules.caichet;

import dev.linqfy.bigCasares.BigCasares;

import java.util.logging.Logger;

/**
 * Lo que comparten las piezas del modulo mientras esta prendido: el plugin (para mostrar u ocultar entidades por
 * jugador y para el logger) y las tareas del scope. Lo instala {@link CaiChetModule} al prender y lo saca al apagar.
 */
final class CaiChetRuntime {

    private static BigCasares plugin;
    private static CaiChetTasks tasks;
    private static boolean active;

    private CaiChetRuntime() {
    }

    static void install(BigCasares owner, CaiChetTasks owned) {
        plugin = owner;
        tasks = owned;
        active = true;
    }

    /** Se llama al empezar a apagar: lo que se haga despues ya no programa tareas nuevas. */
    static void stopping() {
        active = false;
    }

    static void clear() {
        active = false;
        tasks = null;
        plugin = null;
    }

    /** Prendido y sin estar apagandose. */
    static boolean active() {
        return active;
    }

    static BigCasares plugin() {
        return plugin;
    }

    static CaiChetTasks tasks() {
        return tasks;
    }

    static Logger logger() {
        return plugin.getLogger();
    }
}
