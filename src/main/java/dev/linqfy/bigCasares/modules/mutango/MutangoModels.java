package dev.linqfy.bigCasares.modules.mutango;

import java.util.List;

/** Los modelos de Lef, Lihuen y Lican (sin dependencias del servidor, asi los tests y las demas clases los comparten). */
final class MutangoModels {

    /** Modelos de 1, 2 y 3 ojos, en el orden en que aparecen (de izquierda a derecha). */
    static final List<String> MODELS = List.of(
        "bigcasares_boss_mutango",
        "bigcasares_boss_mutango_2ojos",
        "bigcasares_boss_mutango_3ojos"
    );

    private MutangoModels() {
    }
}
