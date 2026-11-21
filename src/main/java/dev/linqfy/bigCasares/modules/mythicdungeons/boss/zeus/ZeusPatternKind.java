package dev.linqfy.bigCasares.modules.mythicdungeons.boss.zeus;

/** Los patrones de rayos de la fase 1, con el aviso que ven los jugadores (action bar, tambien en Bedrock). */
public enum ZeusPatternKind {
    LINES("§eLíneas de tormenta"),
    CROSS("§eCruz del Olimpo"),
    RINGS("§eAnillos de trueno"),
    SPIRAL("§eEspiral de Zeus"),
    CHECKERBOARD("§eTablero celeste"),
    SWEEP("§eBarrido: ¡buscá el hueco!"),
    RAIN("§eLluvia de rayos");

    private final String announcement;

    ZeusPatternKind(String announcement) {
        this.announcement = announcement;
    }

    public String announcement() {
        return announcement;
    }
}
