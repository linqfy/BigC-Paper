package dev.linqfy.bigCasares.modules.humillacion;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Settings of the Humillación.
 *
 * @param overkillThreshold     damage beyond health + absorption needed to humiliate instead of kill
 * @param remainingHealth       health the humiliated player is left with
 * @param requirePlayerAttacker only hits (or projectiles) from another player count
 * @param immunitySeconds       after a humiliation the player can die normally for this long
 * @param broadcast             announce the humiliation to the whole server
 */
public record HumillacionSettings(
    double overkillThreshold,
    double remainingHealth,
    boolean requirePlayerAttacker,
    long immunitySeconds,
    boolean broadcast
) {

    public static HumillacionSettings defaults() {
        return new HumillacionSettings(20.0, 2.0, true, 60L, true);
    }

    public static HumillacionSettings load(FileConfiguration config) {
        HumillacionSettings defaults = defaults();
        try {
            return new HumillacionSettings(
                config.getDouble("humillacion.overkill-threshold", defaults.overkillThreshold()),
                config.getDouble("humillacion.remaining-health", defaults.remainingHealth()),
                config.getBoolean("humillacion.require-player-attacker", defaults.requirePlayerAttacker()),
                config.getLong("humillacion.immunity-seconds", defaults.immunitySeconds()),
                config.getBoolean("humillacion.broadcast", defaults.broadcast())
            );
        } catch (IllegalArgumentException invalid) {
            return defaults;
        }
    }

    public HumillacionSettings {
        if (overkillThreshold <= 0.0) {
            throw new IllegalArgumentException("overkill-threshold debe ser mayor a cero.");
        }
        if (remainingHealth <= 0.0) {
            throw new IllegalArgumentException("remaining-health debe ser mayor a cero.");
        }
        if (immunitySeconds < 0L) {
            throw new IllegalArgumentException("immunity-seconds no puede ser negativo.");
        }
    }
}
