package dev.linqfy.bigCasares.modules.caichet;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.modules.nexus.NexusModule;
import org.bukkit.World;

/** Zonas donde el pozo no se construye. En BigCasares son los volumenes protegidos de los nexus. */
@FunctionalInterface
interface PitZoneRules {

    PitZoneRules NONE = (world, minX, maxX, minY, maxY, minZ, maxZ) -> false;

    /** Si la caja [minX..maxX] x [minY..maxY] x [minZ..maxZ] toca una zona protegida. */
    boolean isProtected(World world, int minX, int maxX, int minY, int maxY, int minZ, int maxZ);

    /** Consulta el modulo de nexus en el momento (puede estar apagado: sin nexus no hay nada que proteger). */
    static PitZoneRules nexus(BigCasares plugin) {
        return (world, minX, maxX, minY, maxY, minZ, maxZ) -> {
            NexusModule nexus = plugin.getNexusModule();
            return nexus != null && nexus.intersectsProtectedVolume(world.getUID(), minX, maxX, minY, maxY, minZ, maxZ);
        };
    }
}
