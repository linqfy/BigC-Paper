package dev.linqfy.bigCasares.modules.quadrants;

/** Reglas de un cuadrante: PvP libre, consensuado o protegido. */
public enum QuadrantZone {
    ANARCHY("§c§lAnarquía §7(PvP activado)"),
    CHILL("§a§lSMP Chill §7(sin PvP)"),
    CONSENSUAL("§e§lPvP consensuado §7(/pvp para participar)");

    private final String label;

    QuadrantZone(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
