package dev.linqfy.bigCasares.modules.alexscaves.core.mob;

import dev.linqfy.bigCasares.modules.alexscaves.core.spawn.BiomeSpawnTable;
import dev.linqfy.bigCasares.modules.alexscaves.core.spawn.BiomeSpawnTable.SpawnCategory;
import dev.linqfy.bigCasares.modules.alexscaves.core.spawn.ModSpawnTables;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModMobCoreTest {
    private static final ModMobType TELETOR = new ModMobType("teletor", CaveBiomeId.MAGNETIC_CAVES, "zombie",
        SpawnCategory.MONSTER, Map.of("max_health", 18.0, "attack_damage", 2.0), "alexscaves_teletor",
        List.of(MobDrop.of("ac_telecore", 0, 1)));

    @Test
    void dropRangesFollowLootingLikeTheMod() {
        MobDrop drop = MobDrop.of("ac_telecore", 0, 1);
        SplittableRandom random = new SplittableRandom(1);
        int max = 0;
        for (int i = 0; i < 2000; i++) {
            int count = drop.roll(random, 3, true);
            assertTrue(count >= 0 && count <= 4);
            max = Math.max(max, count);
        }
        assertEquals(4, max, "looting III adds up to three");
        for (int i = 0; i < 500; i++) {
            assertTrue(drop.roll(random, 0, false) <= 1);
        }
    }

    @Test
    void negativeMinimumMakesDropsRarer() {
        MobDrop soylent = MobDrop.of("ac_green_soylent", -2, 1);
        SplittableRandom random = new SplittableRandom(4);
        int drops = 0;
        for (int i = 0; i < 4000; i++) {
            drops += soylent.roll(random, 0, true) > 0 ? 1 : 0;
        }
        assertTrue(drops > 800 && drops < 1200, "about one in four: " + drops);
    }

    @Test
    void rareDropNeedsAPlayerAndScalesWithLooting() {
        MobDrop dagger = MobDrop.rare("ac_desolate_dagger", 0.025, 0.01);
        SplittableRandom random = new SplittableRandom(9);
        for (int i = 0; i < 1000; i++) {
            assertEquals(0, dagger.roll(random, 3, false));
        }
        int plain = 0;
        int looted = 0;
        for (int i = 0; i < 40000; i++) {
            plain += dagger.roll(random, 0, true);
            looted += dagger.roll(random, 3, true);
        }
        assertTrue(plain > 700 && plain < 1300, "2.5 %: " + plain);
        assertTrue(looted > plain * 1.6, "looting raises the chance: " + looted);
    }

    @Test
    void identityRoundTripsThroughTheSharedCustomEntityKey() {
        Map<String, String> pdc = new HashMap<>();
        ModMobIdentity.TagStore store = new ModMobIdentity.TagStore() {
            @Override
            public String getString(String key) {
                return pdc.get(key);
            }

            @Override
            public void setString(String key, String value) {
                pdc.put(key, value);
            }
        };
        assertEquals(Optional.empty(), ModMobIdentity.mobId(store));
        ModMobIdentity.tag(store, TELETOR);
        assertEquals("alexscaves:teletor", pdc.get("custom_entity"));
        assertEquals(Optional.of("teletor"), ModMobIdentity.mobId(store));
        pdc.put("custom_entity", "jeremy");
        assertEquals(Optional.empty(), ModMobIdentity.mobId(store), "other BigCasares mobs are not ours");
    }

    @Test
    void catalogRejectsDuplicatesAndInvalidTypes() {
        ModMobCatalog catalog = new ModMobCatalog();
        catalog.register(TELETOR, (entity, type) -> ModMobBehaviour.NONE);
        assertThrows(IllegalStateException.class, () -> catalog.register(TELETOR, (entity, type) -> ModMobBehaviour.NONE));
        assertEquals(List.of(TELETOR), catalog.inBiome(CaveBiomeId.MAGNETIC_CAVES));
        assertThrows(IllegalArgumentException.class, () -> new ModMobType("Bad Id", CaveBiomeId.MAGNETIC_CAVES,
            "zombie", SpawnCategory.MONSTER, Map.of(), null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ModMobType("x", CaveBiomeId.MAGNETIC_CAVES,
            "zombie", SpawnCategory.MONSTER, Map.of("max_health", -1.0), null, List.of()));
        assertEquals("alexscaves_teletor", TELETOR.geyserMarker());
    }

    @Test
    void spawnTablesCoverEveryBiomeWithUniqueMobs() {
        Set<String> seen = new HashSet<>();
        for (CaveBiomeId biome : CaveBiomeId.values()) {
            assertTrue(!ModSpawnTables.all(biome).isEmpty(), biome.name());
            for (BiomeSpawnTable.Entry entry : ModSpawnTables.all(biome)) {
                assertTrue(seen.add(entry.mobId()), "mob listed twice: " + entry.mobId());
            }
        }
        assertEquals(350, ModSpawnTables.all(CaveBiomeId.MAGNETIC_CAVES).stream()
            .filter(entry -> entry.mobId().equals("boundroid")).findFirst().orElseThrow().weight());
        BiomeSpawnTable onlyTeletor = ModSpawnTables.table(CaveBiomeId.MAGNETIC_CAVES, SpawnCategory.MONSTER,
            id -> id.equals("teletor"));
        assertEquals(1, onlyTeletor.entries().size());
    }
}
