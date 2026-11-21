package dev.linqfy.bigCasares.modules.mythicdungeons.boss.came;

/** Los dos señores de Xibalbá: Hun Camé ("Uno Muerte", el Warden autoritativo) y Vucub Camé ("Siete Muerte"). */
public enum CameLord {
    HUN("Hun Camé"),
    VUCUB("Vucub Camé");

    private final String displayName;

    CameLord(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public CameLord other() {
        return this == HUN ? VUCUB : HUN;
    }
}
