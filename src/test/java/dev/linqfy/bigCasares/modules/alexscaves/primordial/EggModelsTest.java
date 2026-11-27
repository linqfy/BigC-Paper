package dev.linqfy.bigCasares.modules.alexscaves.primordial;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EggModelsTest {
    @Test
    void stagesPickTheCrackedModels() {
        assertEquals("alexscaves_tremorsaurus_egg", EggModels.key("tremorsaurus", 0).orElseThrow());
        assertEquals("alexscaves_tremorsaurus_egg_cracked", EggModels.key("tremorsaurus", 1).orElseThrow());
        assertEquals("alexscaves_atlatitan_egg_very_cracked", EggModels.key("atlatitan", 2).orElseThrow());
        assertEquals("alexscaves_atlatitan_egg_very_cracked", EggModels.key("atlatitan", 7).orElseThrow());
    }

    @Test
    void tremorzillaHasASingleModelAndUnknownMobsNone() {
        assertEquals("alexscaves_tremorzilla_egg", EggModels.key("tremorzilla", 2).orElseThrow());
        assertTrue(EggModels.key("teletor", 0).isEmpty());
    }

    @Test
    void everyEggModelIsPackaged() {
        assertEquals(19, EggModels.all().size());
        for (String key : EggModels.all()) {
            assertTrue(Files.isRegularFile(Path.of("src/main/resources/bettermodel/models/" + key + ".bbmodel")), key);
        }
    }
}
