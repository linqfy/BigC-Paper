package dev.linqfy.bigCasares.modules.domination;

import org.bukkit.configuration.ConfigurationSection;

/**
 * @param threshold      muertes consecutivas sobre el mismo jugador para una Dominación
 * @param dominationSound sonido global de la Dominación (clave de recurso)
 * @param revengeSound    sonido global de la Venganza (clave de recurso)
 */
public record DominationSettings(int threshold, String dominationSound, String revengeSound) {

    public static final String ROOT = "domination-system";

    public DominationSettings {
        if (threshold < 1) {
            throw new IllegalArgumentException("kills-to-dominate debe ser al menos 1.");
        }
        dominationSound = dominationSound == null ? "" : dominationSound.trim();
        revengeSound = revengeSound == null ? "" : revengeSound.trim();
    }

    public static DominationSettings defaults() {
        return new DominationSettings(3, "minecraft:entity.ender_dragon.growl",
            "minecraft:ui.toast.challenge_complete");
    }

    public static DominationSettings load(ConfigurationSection config) {
        DominationSettings defaults = defaults();
        if (config == null) {
            return defaults;
        }
        try {
            return new DominationSettings(
                config.getInt(ROOT + ".kills-to-dominate", defaults.threshold()),
                config.getString(ROOT + ".domination-sound", defaults.dominationSound()),
                config.getString(ROOT + ".revenge-sound", defaults.revengeSound()));
        } catch (IllegalArgumentException invalid) {
            return defaults;
        }
    }
}
