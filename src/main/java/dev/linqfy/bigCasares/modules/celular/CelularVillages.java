package dev.linqfy.bigCasares.modules.celular;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.Structure;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.Set;

/** The "this village already gave its phone" flag lives in the village's own persistent data, inside the world. */
final class CelularVillages {

    private static final Set<Structure> VILLAGES = Set.of(
        Structure.VILLAGE_PLAINS, Structure.VILLAGE_DESERT, Structure.VILLAGE_SAVANNA,
        Structure.VILLAGE_SNOWY, Structure.VILLAGE_TAIGA);

    private final NamespacedKey gaveKey;

    CelularVillages(Plugin plugin) {
        this.gaveKey = new NamespacedKey(plugin, "celular_village_phone");
    }

    Optional<GeneratedStructure> villageAt(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return Optional.empty();
        }
        return world.getStructures(location.getBlockX() >> 4, location.getBlockZ() >> 4).stream()
            .filter(structure -> VILLAGES.contains(structure.getStructure()))
            .filter(structure -> structure.getBoundingBox().clone().expand(1).contains(location.toVector()))
            .findFirst();
    }

    boolean alreadyGave(Optional<GeneratedStructure> village) {
        return village.map(structure -> structure.getPersistentDataContainer().has(gaveKey, PersistentDataType.BYTE))
            .orElse(false);
    }

    void markGave(Optional<GeneratedStructure> village) {
        village.ifPresent(structure ->
            structure.getPersistentDataContainer().set(gaveKey, PersistentDataType.BYTE, (byte) 1));
    }
}
