package dev.linqfy.bigCasares.modules.alexscaves.candy;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CandyMobsTest {

    @Test
    void caniacArmHurtsOnlyInTheModsArc() {
        // Mod: wrapDegrees(rotation) % 180 > 75, so the arm hurts from 75 to 180 degrees of each turn.
        assertTrue(CandyMobs.Caniac.wrapped(100.0F) % 180.0F > 75.0F);
        assertTrue(!(CandyMobs.Caniac.wrapped(260.0F) % 180.0F > 75.0F), "the back half (-100) does not hurt");
        assertEquals(-170.0F, CandyMobs.Caniac.wrapped(550.0F), 1e-4);
    }

    @Test
    void statsFollowTheMod() {
        assertEquals(38.0, CandyMobs.CANIAC.attribute("max_health", 0));
        assertEquals(14.0, CandyMobs.GUMBEEPER.attribute("max_health", 0));
        assertEquals(4.0, CandyMobs.GUMBEEPER.attribute("armor", 0));
        assertEquals(30.0, CandyMobs.CANDICORN.attribute("max_health", 0));
        assertEquals(36.0, CandyMobs.GUMMY_BEAR.attribute("max_health", 0));
        assertEquals(6, CandyMobs.GUMBALLS);
    }

    @Test
    void licowitchSummonsTheModsMix() {
        // Mod createRandomPossessedMob: candicorn 10%, gummy bear 10%, gumbeeper 30%, caramel cube 15%,
        // gingerbread man 10%, caniac 25%.
        assertEquals("candicorn", CandyFolk.Licowitch.randomPossessed(0.05F));
        assertEquals("gummy_bear", CandyFolk.Licowitch.randomPossessed(0.15F));
        assertEquals("gumbeeper", CandyFolk.Licowitch.randomPossessed(0.45F));
        assertEquals("caramel_cube", CandyFolk.Licowitch.randomPossessed(0.6F));
        assertEquals("gingerbread_man", CandyFolk.Licowitch.randomPossessed(0.7F));
        assertEquals("caniac", CandyFolk.Licowitch.randomPossessed(0.9F));
    }

    @Test
    void folkStatsFollowTheMod() {
        assertEquals(10.0, CandyFolk.GINGERBREAD_MAN.attribute("max_health", 0));
        assertEquals(0.45, CandyFolk.GINGERBREAD_MAN.attribute("movement_speed", 0));
        assertEquals(40.0, CandyFolk.LICOWITCH.attribute("max_health", 0));
        assertTrue(CandyFolk.STEALS.contains("minecraft:cookie") && CandyFolk.STEALS.contains("ac_candy_cane"));
    }

    @Test
    void gumWormFollowsTheMod() {
        assertEquals(150.0, CandyGumWorm.GUM_WORM.attribute("max_health", 0));
        assertEquals(10.0, CandyGumWorm.GUM_WORM.attribute("armor", 0));
        assertEquals(9.0, CandyGumWorm.GUM_WORM.attribute("attack_damage", 0));
        // Mod loot: 2-4 sweet teeth, no Looting.
        var tooth = CandyGumWorm.GUM_WORM.drops().get(0);
        assertEquals(2, tooth.min());
        assertEquals(4, tooth.max());
        assertTrue(!tooth.looting());
        assertEquals(4, CandyItems.MAX_GUMBALLS);
    }

    @Test
    void dropsAndModelsExist() throws IOException {
        List<String> catalog = Files.readAllLines(Path.of("src/main/resources/alexscaves/items.txt"));
        for (ModMobType type : java.util.stream.Stream.concat(CandyMobs.ALL.stream(),
            java.util.stream.Stream.of(CandyFolk.GINGERBREAD_MAN, CandyFolk.LICOWITCH, CandyGumWorm.GUM_WORM)).toList()) {
            assertTrue(Files.exists(Path.of("src/main/resources/bettermodel/models/" + type.modelKey() + ".bbmodel")), type.id());
            type.drops().stream().filter(drop -> drop.item().startsWith("ac_"))
                .forEach(drop -> assertTrue(catalog.contains(drop.item()), drop.item()));
        }
        assertTrue(catalog.contains(CandyMobs.CARAMEL_DROP.item()));
        for (String item : List.of("ac_shot_gum", "ac_candy_cane_hook", "ac_sack_of_sating")) {
            assertTrue(catalog.contains(item), item);
        }
        assertTrue(Files.exists(Path.of("src/main/resources/bettermodel/models/alexscaves_gum_worm_segment.bbmodel")));
        for (String scoop : CandyFolk.SCOOPS) {
            assertTrue(catalog.contains(scoop), scoop);
        }
    }
}
