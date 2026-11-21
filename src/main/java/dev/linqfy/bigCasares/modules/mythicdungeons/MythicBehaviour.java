package dev.linqfy.bigCasares.modules.mythicdungeons;

/** Las conductas extra que el tick de los mobs miticos suma a la IA vanilla de su entidad base. */
public enum MythicBehaviour {
    /** Solo la IA vanilla (pelea cuerpo a cuerpo). */
    MELEE,
    /** Embiste o salta sobre el objetivo cuando esta a media distancia. */
    LEAPER,
    /** Dispara proyectiles ({@link MythicProjectile}); las bases que ya disparan (esqueletos) usan los suyos. */
    RANGED,
    /** Voladores que se tiran en picada sobre el objetivo. */
    FLYER_DIVE,
    /** Aura: aplica sus efectos a los jugadores cercanos cada tanto. */
    CASTER,
    /** Invoca a otro mob de su cultura (no sueltan fragmentos). */
    SUMMONER,
    /** Fantasmas: se teletransportan detras del objetivo. */
    PHASER,
    /** Atrapa y frena al objetivo cuando lo tiene encima. */
    CONSTRICTOR,
    /** Aguanta y golpea el piso: empuja a los que lo rodean. */
    TANK,
    /**
     * Hechicero esquivo: dispara proyectiles ({@link MythicProjectile}) a distancia y, si el objetivo se le pega, se
     * teletransporta lejos en vez de pelear cuerpo a cuerpo.
     */
    BLINK_CASTER
}
