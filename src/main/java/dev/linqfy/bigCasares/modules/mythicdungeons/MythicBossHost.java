package dev.linqfy.bigCasares.modules.mythicdungeons;

import org.bukkit.Location;

import java.util.UUID;

/** Lo que el modulo necesita del sistema pve-boss para los jefes de las mazmorras. */
public interface MythicBossHost {

    /** El sistema de jefes esta prendido y el jefe de la cultura quedo registrado. */
    boolean available(MythicCulture culture);

    /** Crea al jefe y devuelve su instancia. */
    UUID spawn(MythicCulture culture, Location location);

    boolean isActive(UUID bossInstanceId);

    boolean remove(UUID bossInstanceId);
}
