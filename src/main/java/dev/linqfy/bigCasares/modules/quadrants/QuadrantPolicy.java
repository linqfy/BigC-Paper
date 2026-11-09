package dev.linqfy.bigCasares.modules.quadrants;

import java.util.Optional;

/** Reglas puras de los cuadrantes; fuera de los mundos configurados no se restringe nada. */
public final class QuadrantPolicy {

    private QuadrantPolicy() {
    }

    /**
     * El PvP vale si ambos están en Anarquía. En la zona Chill solo sigue un combate ya iniciado
     * (atacante y víctima en combate), por ejemplo si alguien entró por un método no controlado.
     */
    public static boolean allowsPvp(Optional<QuadrantZone> attackerZone, Optional<QuadrantZone> victimZone,
                                    boolean attackerTagged, boolean victimTagged) {
        if (attackerZone.isEmpty() && victimZone.isEmpty()) {
            return true;
        }
        boolean bothAnarchy = attackerZone.orElse(QuadrantZone.ANARCHY) == QuadrantZone.ANARCHY
            && victimZone.orElse(QuadrantZone.ANARCHY) == QuadrantZone.ANARCHY;
        return bothAnarchy || (attackerTagged && victimTagged);
    }

    /** Un jugador en combate no puede entrar a la zona Chill. */
    public static boolean blocksEntry(Optional<QuadrantZone> from, Optional<QuadrantZone> to, boolean tagged) {
        return tagged && to.filter(QuadrantZone.CHILL::equals).isPresent()
            && from.filter(QuadrantZone.CHILL::equals).isEmpty();
    }
}
