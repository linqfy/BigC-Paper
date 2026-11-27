package dev.linqfy.bigCasares.modules.alexscaves.primordial;

import dev.linqfy.bigCasares.modules.alexscaves.core.item.AlexsCavesCraftGuard;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrimordialTamingTest {

    @Test
    void threeTurronesTameEveryTameableDinosaur() {
        assertFalse(TamingRules.tamedByTurrones(1));
        assertFalse(TamingRules.tamedByTurrones(2));
        assertTrue(TamingRules.tamedByTurrones(3), "the third turrón tames");
        assertTrue(TamingRules.tameable(PrimordialMobs.SUBTERRANODON));
        assertTrue(TamingRules.tameable(PrimordialMobs.VALLUMRAPTOR));
        assertTrue(TamingRules.tameable(PrimordialMobs.TREMORSAURUS));
        assertFalse(TamingRules.tameable(PrimordialMobs.GROTTOCERATOPS), "not tameable in the mod");
        assertFalse(TamingRules.tameable(PrimordialMobs.RELICHEIRUS));
        assertFalse(TamingRules.tameable(Atlatitan.ATLATITAN));
    }

    @Test
    void anotherPlayersTurronStartsTheCountAgain() {
        assertEquals(1, TamingRules.turronesAfterFeeding(0, false));
        assertEquals(3, TamingRules.turronesAfterFeeding(2, true), "the same player's third tames");
        assertEquals(1, TamingRules.turronesAfterFeeding(2, false), "nobody steals the third turrón");
    }

    @Test
    void turronesComeFromStructureChestsOnly() {
        assertTrue(TamingRules.structureChest("chests/simple_dungeon"));
        assertTrue(TamingRules.structureChest("chests/caveman_house"), "the module's ruins too");
        assertFalse(TamingRules.structureChest("chests/spawn_bonus_chest"));
        assertFalse(TamingRules.structureChest("entities/zombie"));
        assertFalse(TamingRules.structureChest("archaeology/desert_pyramid"));
    }

    @Test
    void halfOfTheChestsHoldOneOrTwoTurrones() {
        assertEquals(0, TamingRules.turronesInChest(0.5, 0.0));
        assertEquals(0, TamingRules.turronesInChest(0.99, 0.99));
        assertEquals(1, TamingRules.turronesInChest(0.0, 0.0));
        assertEquals(1, TamingRules.turronesInChest(0.49, 0.49));
        assertEquals(2, TamingRules.turronesInChest(0.49, 0.5));
        assertEquals(2, TamingRules.turronesInChest(0.0, 0.999999));
    }

    @Test
    void theTurronFillsTwoShanks() throws IOException {
        String yaml = Files.readString(Path.of("src/main/resources/content/items/ac_turron.yml"));
        assertTrue(yaml.contains("nutrition: 4"), "two drumsticks of the hunger bar");
        assertTrue(yaml.contains("material: DRIED_KELP"), "a base no vanilla mob eats");
    }

    @Test
    void onlyTheModMountsAreRideable() {
        assertTrue(TamingRules.rideable(PrimordialMobs.TREMORSAURUS));
        assertTrue(TamingRules.rideable(PrimordialMobs.SUBTERRANODON));
        assertFalse(TamingRules.rideable(PrimordialMobs.VALLUMRAPTOR));
        assertFalse(TamingRules.rideable(PrimordialMobs.GROTTOCERATOPS));
    }

    @Test
    void groundMountGoesWhereTheRiderLooks() {
        // Yaw 0 looks south (+z) in Minecraft.
        double[] v = TamingRules.mountVelocity(0f, 0f, true, false, false, false, false, false, 0.4, false, -0.08, true);
        assertEquals(0.0, v[0], 1e-9);
        assertEquals(0.4, v[2], 1e-9);
        assertEquals(-0.08, v[1], 1e-9, "keeps falling on the ground mount");
        double[] jump = TamingRules.mountVelocity(90f, 0f, false, false, false, false, true, false, 0.4, false, 0, true);
        assertEquals(0.55, jump[1], 1e-9);
        double[] diagonal = TamingRules.mountVelocity(0f, 0f, true, false, true, false, false, false, 1.0, false, 0, true);
        assertEquals(1.0, Math.hypot(diagonal[0], diagonal[2]), 1e-9, "no faster on diagonals");
    }

    @Test
    void flyingMountClimbsWithPitchAndHovers() {
        double[] up = TamingRules.mountVelocity(0f, -30f, true, false, false, false, false, false, 1.0, true, 0, false);
        assertTrue(up[1] > 0.4, "looking up climbs");
        double[] idle = TamingRules.mountVelocity(0f, -30f, false, false, false, false, false, false, 1.0, true, -0.5, false);
        assertEquals(0.0, idle[1], 1e-9, "no input: hovers");
        double[] sink = TamingRules.mountVelocity(0f, 0f, false, false, false, false, false, true, 1.0, true, 0, false);
        assertTrue(sink[1] < 0);
    }

    @Test
    void breedingFoodsFollowTheMod() {
        assertTrue(TamingRules.breedingFood(PrimordialMobs.SUBTERRANODON, "COD", false));
        assertTrue(TamingRules.breedingFood(PrimordialMobs.GROTTOCERATOPS, "ac_tree_star", false));
        assertFalse(TamingRules.breedingFood(PrimordialMobs.TREMORSAURUS, "ac_dinosaur_chop", false), "wild ones do not breed");
        assertTrue(TamingRules.breedingFood(PrimordialMobs.TREMORSAURUS, "ac_cooked_dinosaur_chop", true));
        assertFalse(TamingRules.breedingFood(PrimordialMobs.TRILOCARIS, "COD", true));
        assertEquals(3, TamingRules.eggsLaid(PrimordialMobs.SUBTERRANODON, 2));
        assertEquals(1, TamingRules.eggsLaid(PrimordialMobs.RELICHEIRUS, 2));
    }

    @Test
    void eggsHatchAtTheModsRandomTickPace() {
        double perStageSeconds = 1.0 / TamingRules.hatchStageChancePerSecond(false);
        assertEquals(1365.0, perStageSeconds, 1.0, "about 22.7 minutes per stage");
        assertEquals(perStageSeconds / 2, 1.0 / TamingRules.hatchStageChancePerSecond(true), 1.0, "twice as fast on fern thatch");
    }

    @Test
    void primordialArmorFeedsMoreFromRawChops() {
        double[] none = PrimordialArmor.bonus(0);
        assertEquals(0.0, none[0], 1e-9);
        assertEquals(0.0, none[1], 1e-9);
        double[] full = PrimordialArmor.bonus(3);
        assertEquals(6.0, full[0], 1e-9, "3 pieces: +3 shanks per bite, 2 bites");
        // Mod per bite: (3 + 3) * (0.2 + 0.375) * 2 = 6.9 against 1.2 without armor.
        assertEquals((6.9 - 1.2) * 2, full[1], 1e-9);
    }

    @Test
    void everyItemNamedHereExistsInTheCatalog() throws IOException {
        Set<String> catalog = Files.readAllLines(Path.of("src/main/resources/alexscaves/items.txt")).stream()
            .map(String::trim).filter(line -> !line.isEmpty() && !line.startsWith("#")).collect(Collectors.toSet());
        for (String item : TamingRules.TAMING_ITEMS) {
            assertTrue(catalog.contains(item), item);
        }
        for (Map.Entry<String, String> cooking : AlexsCavesCraftGuard.COOKING.entrySet()) {
            assertTrue(catalog.contains(cooking.getKey()) && catalog.contains(cooking.getValue()), cooking.toString());
        }
        for (ModMobType type : PrimordialMobs.ALL) {
            if (type != PrimordialMobs.TRILOCARIS) {
                assertTrue(catalog.contains("ac_" + type.id() + "_egg"), type.id());
            }
        }
        assertTrue(catalog.contains(PrimordialSpear.SPEAR));
        for (String piece : PrimordialArmor.PIECES) {
            assertTrue(catalog.contains(piece), piece);
        }
        for (List<PrimordialDrops.Drop> drops : PrimordialDrops.DROPS.values()) {
            for (PrimordialDrops.Drop drop : drops) {
                assertTrue(catalog.contains(drop.item()), drop.item());
            }
        }
    }

    @Test
    void clampsDeadlinesSavedAgainstAnotherClock() {
        assertEquals(1000L, TamingRules.clampDeadline(1000L, 500L, 24000L), "a sane deadline is kept");
        assertEquals(500L + 24000L, TamingRules.clampDeadline(9_000_000L, 500L, 24000L));
        assertEquals(0L, TamingRules.clampDeadline(0L, 500L, 24000L));
    }

    @Test
    void bossesArePersistentAndOtherMobsAreNot() {
        assertTrue(Luxtructosaurus.LUXTRUCTOSAURUS.persistent());
        assertTrue(Tremorzilla.TREMORZILLA.persistent());
        assertFalse(PrimordialMobs.TREMORSAURUS.persistent());
        assertFalse(Atlatitan.ATLATITAN.persistent());
    }
}
