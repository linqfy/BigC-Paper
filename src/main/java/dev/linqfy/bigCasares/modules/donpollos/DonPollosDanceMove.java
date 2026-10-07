package dev.linqfy.bigCasares.modules.donpollos;

/** Pasos que el Salsero te pide en la batalla de baile. */
public enum DonPollosDanceMove {
    JUMP("§e§l¡SALTÁ!", "§e§l▲  ▲  ▲"),
    SNEAK("§b§l¡AGACHATE!", "§b§l▼  ▼  ▼"),
    LEFT("§a§l¡IZQUIERDA!", "§a§l←  ←  ←"),
    RIGHT("§d§l¡DERECHA!", "§d§l→  →  →"),
    FORWARD("§6§l¡ADELANTE!", "§6§l↑  ↑  ↑"),
    BACKWARD("§c§l¡ATRÁS!", "§c§l↓  ↓  ↓");

    private final String title;
    private final String arrows;

    DonPollosDanceMove(String title, String arrows) {
        this.title = title;
        this.arrows = arrows;
    }

    public String title() {
        return title;
    }

    public String arrows() {
        return arrows;
    }
}
