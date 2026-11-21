package dev.linqfy.bigCasares.modules.mythicdungeons.boss.horus;

import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HorusDamageRulesTest {

    @Test
    void phaseOneHitsCannotSkipThePhaseTwoThreshold() {
        assertEquals(12.0, HorusDamageRules.admitted(1, 900.0, 1000.0, 12.0));
        assertEquals(5.0, HorusDamageRules.admitted(1, 505.0, 1000.0, 12.0));
        assertEquals(0.0, HorusDamageRules.admitted(1, 500.0, 1000.0, 12.0));
    }

    @Test
    void phaseTwoAndBadValuesAreNegated() {
        assertEquals(0.0, HorusDamageRules.admitted(2, 900.0, 1000.0, 12.0));
        assertEquals(0.0, HorusDamageRules.admitted(1, 900.0, 1000.0, Double.NaN));
        assertEquals(0.0, HorusDamageRules.admitted(1, 900.0, 1000.0, -3.0));
    }

    @Test
    void everyClipTheEncounterAndTheDefinitionUseExistsInTheModels() throws IOException {
        List<String> horusClips = List.of("idle", "walk", "planeo", "dive", "attack", "rage", "cast", "vanish",
            "death");
        String horus = resource("bettermodel/models/bigcasares_myth_horus.bbmodel");
        for (String clip : horusClips) {
            assertTrue(animations(horus).contains(clip), clip);
        }
        String definition = resource("bosses/myth-horus.yml");
        Matcher used = Pattern.compile("(?m)^\\s+animation: (\\w+)").matcher(definition);
        while (used.find()) {
            assertTrue(animations(horus).contains(used.group(1)), used.group(1));
        }
        String eye = resource("bettermodel/models/" + HorusFight.EYE_MODEL + ".bbmodel");
        for (String clip : List.of("idle", "walk", "attack", "death", "rise")) {
            assertTrue(animations(eye).contains(clip), clip);
        }
        assertEquals(List.of(HorusFight.EYE_MODEL), HorusEncounter.extraModelKeys());
    }

    private static List<String> animations(String bbmodel) {
        int start = bbmodel.indexOf("\"animations\":[");
        assertTrue(start >= 0);
        Matcher names = Pattern.compile("\"name\":\"(\\w+)\",\"loop\"").matcher(bbmodel.substring(start));
        return names.results().map(match -> match.group(1)).toList();
    }

    private String resource(String path) throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void onlySurvivalAndAdventurePlayersAreTargeted() {
        assertTrue(HorusDamageRules.targetable(GameMode.SURVIVAL, false));
        assertTrue(HorusDamageRules.targetable(GameMode.ADVENTURE, false));
        assertFalse(HorusDamageRules.targetable(GameMode.CREATIVE, false));
        assertFalse(HorusDamageRules.targetable(GameMode.SPECTATOR, false));
        assertFalse(HorusDamageRules.targetable(GameMode.ADVENTURE, true));
    }
}
