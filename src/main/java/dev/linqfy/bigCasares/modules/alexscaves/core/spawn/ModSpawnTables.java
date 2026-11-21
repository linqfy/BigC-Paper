package dev.linqfy.bigCasares.modules.alexscaves.core.spawn;

import dev.linqfy.bigCasares.modules.alexscaves.core.spawn.BiomeSpawnTable.Entry;
import dev.linqfy.bigCasares.modules.alexscaves.core.spawn.BiomeSpawnTable.SpawnCategory;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Module mob spawns of each biome, copied from the mod's biome JSON
 * ({@code spawners}). Vanilla mobs of those biomes are spawned by the server
 * from the datapack; only module mobs are listed here. The Atlatitan only
 * spawns after its boss is defeated and is handled by the Primordial package.
 */
public final class ModSpawnTables {
    private static final Map<CaveBiomeId, List<Entry>> ENTRIES = new EnumMap<>(Map.of(
        CaveBiomeId.MAGNETIC_CAVES, List.of(
            new Entry("notor", SpawnCategory.AMBIENT, 20, 1, 4),
            new Entry("magnetron", SpawnCategory.MONSTER, 80, 1, 1),
            new Entry("teletor", SpawnCategory.MONSTER, 225, 1, 1),
            new Entry("boundroid", SpawnCategory.MONSTER, 350, 1, 2),
            new Entry("ferrouslime", SpawnCategory.MONSTER, 200, 1, 2)),
        CaveBiomeId.PRIMORDIAL_CAVES, List.of(
            new Entry("trilocaris", SpawnCategory.WATER_AMBIENT, 15, 1, 2),
            new Entry("subterranodon", SpawnCategory.CAVE_CREATURE, 6, 3, 5),
            new Entry("vallumraptor", SpawnCategory.CAVE_CREATURE, 6, 6, 7),
            new Entry("grottoceratops", SpawnCategory.CAVE_CREATURE, 27, 2, 4),
            new Entry("tremorsaurus", SpawnCategory.CAVE_CREATURE, 5, 1, 1),
            new Entry("relicheirus", SpawnCategory.CAVE_CREATURE, 13, 1, 1),
            new Entry("atlatitan", SpawnCategory.CAVE_CREATURE, 10, 2, 3)),
        CaveBiomeId.TOXIC_CAVES, List.of(
            new Entry("gammaroach", SpawnCategory.AMBIENT, 55, 8, 8),
            new Entry("raycat", SpawnCategory.CAVE_CREATURE, 10, 1, 1),
            new Entry("brainiac", SpawnCategory.MONSTER, 160, 1, 2),
            new Entry("nucleeper", SpawnCategory.MONSTER, 45, 1, 1),
            new Entry("radgill", SpawnCategory.WATER_AMBIENT, 10, 1, 3)),
        CaveBiomeId.ABYSSAL_CHASM, List.of(
            new Entry("deep_one_mage", SpawnCategory.MONSTER, 2, 1, 1),
            new Entry("deep_one_knight", SpawnCategory.MONSTER, 4, 1, 1),
            new Entry("deep_one", SpawnCategory.MONSTER, 8, 1, 1),
            new Entry("hullbreaker", SpawnCategory.UNDERGROUND_WATER_CREATURE, 20, 1, 1),
            new Entry("lanternfish", SpawnCategory.WATER_AMBIENT, 100, 15, 20),
            new Entry("tripodfish", SpawnCategory.DEEP_SEA_CREATURE, 40, 1, 2),
            new Entry("sea_pig", SpawnCategory.DEEP_SEA_CREATURE, 50, 3, 4),
            new Entry("gossamer_worm", SpawnCategory.DEEP_SEA_CREATURE, 20, 1, 1)),
        CaveBiomeId.FORLORN_HOLLOWS, List.of(
            new Entry("gloomoth", SpawnCategory.AMBIENT, 20, 4, 4),
            new Entry("underzealot", SpawnCategory.MONSTER, 100, 1, 1),
            new Entry("corrodent", SpawnCategory.MONSTER, 90, 3, 4),
            new Entry("vesper", SpawnCategory.MONSTER, 60, 2, 3)),
        CaveBiomeId.CANDY_CAVITY, List.of(
            new Entry("caniac", SpawnCategory.MONSTER, 100, 1, 1),
            new Entry("gumbeeper", SpawnCategory.MONSTER, 100, 1, 2),
            new Entry("gum_worm", SpawnCategory.MONSTER, 80, 1, 1),
            new Entry("caramel_cube", SpawnCategory.MONSTER, 40, 2, 3),
            new Entry("sweetish_fish", SpawnCategory.WATER_AMBIENT, 10, 1, 3),
            new Entry("candicorn", SpawnCategory.CAVE_CREATURE, 100, 4, 6),
            new Entry("gummy_bear", SpawnCategory.CAVE_CREATURE, 100, 1, 3))
    ));

    private ModSpawnTables() {
    }

    public static List<Entry> all(CaveBiomeId biome) {
        return ENTRIES.getOrDefault(biome, List.of());
    }

    /** Table of one biome and category, restricted to mobs the module currently implements. */
    public static BiomeSpawnTable table(CaveBiomeId biome, SpawnCategory category, Predicate<String> implemented) {
        return new BiomeSpawnTable(all(biome).stream()
            .filter(entry -> entry.category() == category && implemented.test(entry.mobId()))
            .toList());
    }
}
