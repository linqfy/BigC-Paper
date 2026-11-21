package dev.linqfy.bigCasares.modules.mythicdungeons;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MythicMobCatalogTest {

    private static final Set<String> ELITES = Set.of("cerberus", "nidhogg", "hel", "treasure_dragon", "ammit",
        "apophis", "ereshkigal", "oni", "xibalba_lord", "djinn", "supay");
    /** Los que vienen en bandada: comunes con menos vida que el resto. */
    private static final Set<String> SWARM = Set.of("camazotz_bat");

    private final MythicMobCatalog catalog = MythicMobCatalog.defaults();

    @Test
    void everyCultureHasExactlyItsMobs() {
        assertEquals(27, catalog.all().size());
        for (MythicCulture culture : MythicCulture.values()) {
            List<MythicMobType> mobs = catalog.ofCulture(culture);
            assertEquals(culture.mobIds(), mobs.stream().map(MythicMobType::id).toList());
            mobs.forEach(mob -> assertEquals(culture, mob.culture()));
        }
    }

    @Test
    void statsWeightsAndModelsAreSane() {
        for (MythicMobType mob : catalog.all()) {
            assertTrue(mob.weight() > 0.0, mob.id());
            assertTrue(mob.health() > 0.0 && mob.speed() > 0.0 && mob.scale() > 0.0, mob.id());
            assertTrue(mob.fragmentChance() > 0.0 && mob.fragmentChance() <= 1.0, mob.id());
            assertEquals("bigcasares_myth_" + mob.id(), mob.modelKey());
            assertTrue(mob.baseEntity().isAlive(), mob.id());
            assertFalse(mob.displayName().isBlank(), mob.id());
            if (mob.behaviour() == MythicBehaviour.SUMMONER) {
                MythicMobType summoned = catalog.require(mob.summonId());
                assertEquals(mob.culture(), summoned.culture(), mob.id());
            }
            if (mob.behaviour() == MythicBehaviour.RANGED || mob.behaviour() == MythicBehaviour.BLINK_CASTER) {
                assertTrue(mob.projectile() != MythicProjectile.NONE, mob.id());
            }
        }
    }

    @Test
    void elitesAreTougherAndRarerThanTheCommonsOfTheirDungeon() {
        for (MythicMobType mob : catalog.all()) {
            assertEquals(ELITES.contains(mob.id()), mob.elite(), mob.id());
            if (mob.elite()) {
                assertTrue(mob.health() >= 80 && mob.health() <= 200, mob.id());
            } else if (SWARM.contains(mob.id())) {
                assertTrue(mob.health() >= 10 && mob.health() < 20, mob.id());
            } else {
                assertTrue(mob.health() >= 20 && mob.health() <= 40, mob.id());
            }
        }
        for (MythicCulture culture : MythicCulture.values()) {
            double maxEliteWeight = catalog.ofCulture(culture).stream().filter(MythicMobType::elite)
                .mapToDouble(MythicMobType::weight).max().orElse(0.0);
            double minCommonWeight = catalog.ofCulture(culture).stream().filter(mob -> !mob.elite())
                .mapToDouble(MythicMobType::weight).min().orElse(Double.MAX_VALUE);
            assertTrue(maxEliteWeight < minCommonWeight, culture.id());
        }
    }

    @Test
    void everyCultureHasAnEliteAndTheSwarmIsTheMostCommon() {
        for (MythicCulture culture : MythicCulture.values()) {
            assertTrue(catalog.ofCulture(culture).stream().anyMatch(MythicMobType::elite), culture.id());
            assertTrue(catalog.ofCulture(culture).stream().anyMatch(mob -> !mob.elite()), culture.id());
        }
        MythicMobType bat = catalog.require("camazotz_bat");
        for (MythicMobType other : catalog.ofCulture(MythicCulture.MESOAMERICAN)) {
            assertTrue(bat.weight() >= other.weight(), other.id());
        }
        assertTrue(bat.floating());
        assertEquals("camazotz_bat", catalog.require("xibalba_lord").summonId());
    }

    @Test
    void theNewCulturesKeepTheirVanillaDropsAndExperience() {
        for (MythicCulture culture : MythicCulture.values()) {
            if (culture.hasBoss()) {
                continue;
            }
            for (MythicMobType mob : catalog.ofCulture(culture)) {
                assertFalse(mob.drops().isEmpty(), mob.id());
                assertTrue(mob.xp() > 0, mob.id());
            }
        }
    }

    @Test
    void everyMobFitsTheHitboxCap() {
        for (MythicMobType mob : catalog.all()) {
            assertTrue(MythicBody.vanillaSize(mob.baseEntity()).isPresent(), mob.id() + " base without hitbox");
            assertTrue(mob.hitboxWidth() <= MythicBody.MAX_WIDTH, mob.id() + " width " + mob.hitboxWidth());
            assertTrue(mob.hitboxHeight() <= MythicBody.MAX_HEIGHT, mob.id() + " height " + mob.hitboxHeight());
        }
    }

    @Test
    void elitesAreVisiblyBiggerThanTheCommonsOfTheirDungeon() {
        for (MythicCulture culture : MythicCulture.values()) {
            double smallestElite = catalog.ofCulture(culture).stream().filter(MythicMobType::elite)
                .mapToDouble(MythicMobCatalogTest::volume).min().orElseThrow();
            double biggestCommon = catalog.ofCulture(culture).stream().filter(mob -> !mob.elite())
                .mapToDouble(MythicMobCatalogTest::volume).max().orElseThrow();
            assertTrue(smallestElite > biggestCommon, culture.id());
        }
    }

    private static double volume(MythicMobType mob) {
        return mob.hitboxWidth() * mob.hitboxWidth() * mob.hitboxHeight();
    }

    @Test
    void onlyTheGhostPhasesThroughWalls() {
        for (MythicMobType mob : catalog.all()) {
            boolean ignoresCollisions = MythicBody.ignoresBlocks(mob.baseEntity())
                || mob.behaviour() == MythicBehaviour.PHASER;
            assertEquals(ignoresCollisions, mob.phasesThroughWalls(), mob.id());
            assertEquals(mob.id().equals("ghost"), mob.phasesThroughWalls(), mob.id());
        }
    }

    @Test
    void theFlyersCollideWithBlocksAndAreSteered() {
        for (String id : List.of("erinys", "camazotz_bat")) {
            MythicMobType flyer = catalog.require(id);
            assertFalse(MythicBody.ignoresBlocks(flyer.baseEntity()), id);
            assertTrue(flyer.steeredFlyer() && flyer.floating(), id);
            assertEquals(MythicBehaviour.FLYER_DIVE, flyer.behaviour(), id);
        }
    }

    @Test
    void ymlOverridesTuneTheNewCulturesToo() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("mobs.oni.health", 150.0);
        yaml.set("mobs.camazotz_bat.weight", 20.0);
        yaml.set("mobs.djinn.damage", 12.0);
        List<String> unknown = new ArrayList<>();

        MythicMobCatalog tuned = catalog.withOverrides(yaml.getConfigurationSection("mobs"), unknown::add);

        assertEquals(150.0, tuned.require("oni").health());
        assertEquals(20.0, tuned.require("camazotz_bat").weight());
        assertEquals(12.0, tuned.require("djinn").damage());
        assertEquals(catalog.require("djinn").behaviour(), tuned.require("djinn").behaviour());
        assertEquals(List.of(), unknown);
    }

    @Test
    void ymlOverridesReplaceOnlyTheTunableNumbers() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("mobs.minotaur.health", 55.0);
        yaml.set("mobs.minotaur.damage", 9.0);
        yaml.set("mobs.minotaur.speed", 0.3);
        yaml.set("mobs.minotaur.fragment-chance", 0.5);
        yaml.set("mobs.minotaur.weight", 3.0);
        yaml.set("mobs.ghost.health", -4.0);
        yaml.set("mobs.ghost.fragment-chance", 2.0);
        yaml.set("mobs.unicorn.health", 10.0);
        List<String> unknown = new ArrayList<>();

        MythicMobCatalog tuned = catalog.withOverrides(yaml.getConfigurationSection("mobs"), unknown::add);

        MythicMobType minotaur = tuned.require("minotaur");
        assertEquals(55.0, minotaur.health());
        assertEquals(9.0, minotaur.damage());
        assertEquals(0.3, minotaur.speed());
        assertEquals(0.5, minotaur.fragmentChance());
        assertEquals(3.0, minotaur.weight());
        assertEquals(catalog.require("minotaur").baseEntity(), minotaur.baseEntity());
        MythicMobType ghost = tuned.require("ghost");
        assertEquals(catalog.require("ghost").health(), ghost.health());
        assertEquals(catalog.require("ghost").fragmentChance(), ghost.fragmentChance());
        assertEquals(List.of("unicorn"), unknown);
        assertEquals(catalog.ids(), tuned.ids());
    }
}
