package dev.linqfy.bigCasares.modules.mythicdungeons;

import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerInteractEvent;

/** Reglas compartidas de los clicks con items del modulo (sin servidor). */
final class MythicInteractions {

    private MythicInteractions() {
    }

    /**
     * Otro plugin (o modulo) ya nego usar el item de la mano: el modulo no lo usa tampoco. No se mira
     * {@link PlayerInteractEvent#isCancelled()}: Bukkit marca cancelado todo click al aire (no hay bloque que usar).
     */
    static boolean deniedByOthers(PlayerInteractEvent event) {
        return event.useItemInHand() == Event.Result.DENY;
    }
}
