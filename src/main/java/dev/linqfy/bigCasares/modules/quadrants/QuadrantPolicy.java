package dev.linqfy.bigCasares.modules.quadrants;

import java.util.Optional;

/** Reglas puras de los cuadrantes; fuera de los mundos configurados no se restringe nada. */
public final class QuadrantPolicy {

    private QuadrantPolicy() {
    }

    /**
     * La zona Chill siempre protege. En la zona consensuada ambos jugadores deben aceptar;
     * fuera de ella rige el PvP libre, también en mundos no gestionados.
     */
    public static boolean allowsPvp(Optional<QuadrantZone> attackerZone, Optional<QuadrantZone> victimZone,
                                    boolean attackerOptedIn, boolean victimOptedIn) {
        if (attackerZone.filter(QuadrantZone.CHILL::equals).isPresent()
            || victimZone.filter(QuadrantZone.CHILL::equals).isPresent()) {
            return false;
        }
        if (attackerZone.filter(QuadrantZone.CONSENSUAL::equals).isPresent()
            || victimZone.filter(QuadrantZone.CONSENSUAL::equals).isPresent()) {
            return attackerOptedIn && victimOptedIn;
        }
        return true;
    }

    /** Un jugador en combate no puede entrar a la zona Chill. */
    public static boolean blocksEntry(Optional<QuadrantZone> from, Optional<QuadrantZone> to, boolean tagged) {
        return tagged && to.filter(QuadrantZone.CHILL::equals).isPresent()
            && from.filter(QuadrantZone.CHILL::equals).isEmpty();
    }
}
