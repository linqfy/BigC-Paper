package dev.linqfy.bigCasares.modules.mutango;

import java.util.UUID;

/** Animaciones de BetterModel sobre un Mutango (por id de la entidad). */
interface MutangoAnimations {
    void once(UUID id, String animation);

    void loop(UUID id, String animation);

    void stop(UUID id, String animation);
}
