package dev.linqfy.bigCasares.modules.alexscaves.core.mob;

import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Registered mob types and their behaviours. Duplicate ids are a programming error and fail the enable. */
public final class ModMobCatalog {
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<String, SpawnRule> spawnRules = new LinkedHashMap<>();

    /** Extra natural spawn condition of a mob (the mod's spawn placement predicate), checked on the spawn block. */
    @FunctionalInterface
    public interface SpawnRule {
        boolean allows(org.bukkit.block.Block block, java.util.random.RandomGenerator random);
    }

    /**
     * Mod {@code spawn_overrides} of a structure: monsters spawning on a spot it recognises (by the blocks there) are
     * replaced by the structure's own list. Returns a module mob id, {@code minecraft:<entity>} for a vanilla mob, an
     * empty string when the spot is in the structure but nothing spawns this time, or null when it is not.
     */
    @FunctionalInterface
    public interface StructureSpawn {
        String mobAt(org.bukkit.block.Block spot, java.util.random.RandomGenerator random);
    }

    /**
     * A structure's monster spawns, with its group size.
     *
     * @param water its monsters spawn in water ({@code IN_WATER}): the spawner then tries them on their own, since the
     *              biome's water spots never reach its monster list
     */
    public record StructureSpawns(CaveBiomeId biome, StructureSpawn spawn, int count, boolean water) {
    }

    private final List<StructureSpawns> structureSpawns = new ArrayList<>();
    private final java.util.Set<String> waterSpawns = new java.util.HashSet<>();

    /** Mod {@code SpawnPlacements.Type.IN_WATER} for a mob whose category spawns on the ground (monsters). */
    public void spawnsInWater(String id) {
        waterSpawns.add(id);
    }

    public boolean inWater(String id) {
        return waterSpawns.contains(id);
    }

    public void structureSpawn(CaveBiomeId biome, int count, StructureSpawn spawn) {
        structureSpawns.add(new StructureSpawns(biome, spawn, count, false));
    }

    /** A structure whose monsters spawn in water (the Abyssal Ruins' mine guardians). */
    public void waterStructureSpawn(CaveBiomeId biome, int count, StructureSpawn spawn) {
        structureSpawns.add(new StructureSpawns(biome, spawn, count, true));
    }

    public List<StructureSpawns> structureSpawns(CaveBiomeId biome) {
        return structureSpawns.stream().filter(entry -> entry.biome() == biome).toList();
    }

    public void spawnRule(String id, SpawnRule rule) {
        spawnRules.put(id, rule);
    }

    /** Whether a natural spawn of the mob may happen at the block (true without a rule). */
    public boolean allowsSpawn(String id, org.bukkit.block.Block block, java.util.random.RandomGenerator random) {
        SpawnRule rule = spawnRules.get(id);
        return rule == null || rule.allows(block, random);
    }

    public void register(ModMobType type, ModMobBehaviour.Factory behaviour) {
        if (entries.putIfAbsent(type.id(), new Entry(type, behaviour)) != null) {
            throw new IllegalStateException("Mob de Alex's Caves duplicado: " + type.id());
        }
    }

    public Optional<ModMobType> find(String id) {
        Entry entry = entries.get(id);
        return entry == null ? Optional.empty() : Optional.of(entry.type());
    }

    public Optional<ModMobBehaviour.Factory> behaviour(String id) {
        Entry entry = entries.get(id);
        return entry == null ? Optional.empty() : Optional.of(entry.behaviour());
    }

    public Collection<ModMobType> types() {
        List<ModMobType> types = new ArrayList<>();
        for (Entry entry : entries.values()) {
            types.add(entry.type());
        }
        return types;
    }

    public List<ModMobType> inBiome(CaveBiomeId biome) {
        return types().stream().filter(type -> type.biome() == biome).toList();
    }

    public boolean contains(String id) {
        return entries.containsKey(id);
    }

    private record Entry(ModMobType type, ModMobBehaviour.Factory behaviour) {
    }
}
