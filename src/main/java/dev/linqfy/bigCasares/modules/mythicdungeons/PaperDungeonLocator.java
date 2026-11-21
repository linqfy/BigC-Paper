package dev.linqfy.bigCasares.modules.mythicdungeons;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.Structure;
import org.bukkit.generator.structure.StructurePiece;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Busca las mazmorras en las estructuras generadas del chunk ({@link World#getStructures(int, int)}), resolviendo cada
 * una a su clave de registro. Las estructuras de un chunk ya generado no cambian, asi que se guardan en un cache chico
 * por chunk (se vacia al apagar el modulo). Solo hay que llamarlo en el hilo del servidor con el chunk cargado.
 */
public final class PaperDungeonLocator implements DungeonLocator {

    private static final int CACHE_SIZE = 4096;

    private final Map<String, List<DungeonBounds>> cache = new LinkedHashMap<>(256, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<DungeonBounds>> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    @Override
    public Optional<DungeonBounds> dungeonBounds(Location location) {
        World world = location == null ? null : location.getWorld();
        if (world == null) {
            return Optional.empty();
        }
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        List<DungeonBounds> dungeons = cache.computeIfAbsent(world.getName() + ":" + chunkX + ":" + chunkZ,
            ignored -> scan(world, chunkX, chunkZ));
        for (DungeonBounds bounds : dungeons) {
            if (bounds.contains(world.getName(), location.getX(), location.getY(), location.getZ())) {
                return Optional.of(bounds);
            }
        }
        return Optional.empty();
    }

    public void clearCache() {
        cache.clear();
    }

    @Override
    public boolean isAltarSpot(Location block) {
        World world = block == null ? null : block.getWorld();
        if (world == null) {
            return false;
        }
        Registry<Structure> registry = structures();
        for (GeneratedStructure generated : world.getStructures(block.getBlockX() >> 4, block.getBlockZ() >> 4)) {
            if (keyOf(registry, generated.getStructure()).flatMap(MythicCulture::byStructureKey).isEmpty()) {
                continue;
            }
            for (StructurePiece piece : generated.getPieces()) {
                BoundingBox box = piece.getBoundingBox();
                int minX = (int) Math.floor(box.getMinX());
                int minZ = (int) Math.floor(box.getMinZ());
                // la caja de Bukkit puede venir con el maximo exclusivo: se prueban las dos lecturas
                for (int slack = 0; slack <= 1; slack++) {
                    if (AltarRule.isAltarSpot(minX, (int) Math.floor(box.getMinY()), minZ,
                        (int) Math.floor(box.getMaxX()) - slack, (int) Math.floor(box.getMaxZ()) - slack,
                        block.getBlockX(), block.getBlockY(), block.getBlockZ())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** La estructura de la mazmorra de una cultura, si el datapack ya esta cargado en el registro. */
    public static Optional<Structure> structureOf(MythicCulture culture) {
        NamespacedKey key = NamespacedKey.fromString(culture.structureKey());
        if (key == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(structures().get(key));
        } catch (RuntimeException unavailable) {
            return Optional.empty();
        }
    }

    private static List<DungeonBounds> scan(World world, int chunkX, int chunkZ) {
        List<DungeonBounds> found = new ArrayList<>(1);
        Registry<Structure> registry = structures();
        for (GeneratedStructure generated : world.getStructures(chunkX, chunkZ)) {
            Optional<MythicCulture> culture = keyOf(registry, generated.getStructure())
                .flatMap(MythicCulture::byStructureKey);
            if (culture.isEmpty()) {
                continue;
            }
            BoundingBox box = generated.getBoundingBox();
            // Whether the box max is inclusive or exclusive, flooring it keeps the last block row inside: being one
            // block generous at the border is harmless, losing the boss room wall row is not.
            found.add(new DungeonBounds(world.getName(), culture.get(),
                (int) Math.floor(box.getMinX()), (int) Math.floor(box.getMinY()), (int) Math.floor(box.getMinZ()),
                (int) Math.floor(box.getMaxX()), (int) Math.floor(box.getMaxY()), (int) Math.floor(box.getMaxZ())));
        }
        return List.copyOf(found);
    }

    private static Optional<String> keyOf(Registry<Structure> registry, Structure structure) {
        if (structure == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(registry.getKey(structure)).map(NamespacedKey::asString);
        } catch (RuntimeException unregistered) {
            return Optional.empty();
        }
    }

    private static Registry<Structure> structures() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.STRUCTURE);
    }
}
