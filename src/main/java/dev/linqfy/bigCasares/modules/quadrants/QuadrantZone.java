package dev.linqfy.bigCasares.modules.quadrants;

/** Reglas de un cuadrante: Anarquía (PvP libre) o SMP Chill (sin PvP). */
public enum QuadrantZone {
    ANARCHY("§c§lAnarquía §7(PvP activado)"),
    CHILL("§a§lSMP Chill §7(sin PvP)");

    private final String label;

    QuadrantZone(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
