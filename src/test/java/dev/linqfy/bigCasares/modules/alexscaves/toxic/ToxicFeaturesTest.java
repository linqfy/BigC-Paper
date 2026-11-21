package dev.linqfy.bigCasares.modules.alexscaves.toxic;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ToxicFeaturesTest {

    @Test
    void radiationChanceAddsUpOverASecond() {
        assertTrue(Math.abs(ToxicRadiation.overTicks(0.001, 20) - (1 - Math.pow(0.999, 20))) < 1e-12);
        assertTrue(ToxicRadiation.overTicks(2.0, 20) == 1.0, "capped");
        assertTrue(ToxicRadiation.overTicks(0, 20) == 0.0);
    }

    @Test
    void radioactiveItemsExistInTheCatalog() throws IOException {
        List<String> catalog = Files.readAllLines(Path.of("src/main/resources/alexscaves/items.txt"));
        for (String item : Hazmat.PIECES) {
            assertTrue(catalog.contains(item), item);
        }
        for (String item : ToxicRadiation.RADIOACTIVE.keySet()) {
            assertTrue(catalog.contains(item), item);
        }
    }

    @Test
    void bandFollowsTheBiomeDepth() {
        assertTrue(ToxicFeatures.BAND_TOP == 38, "0.3 depth units");
        assertTrue(ToxicFeatures.BAND_DEPTH == 154, "1.5 - 0.3 depth units");
    }

    @Test
    void acidFollowsTheModNumbers() {
        assertTrue(AcidRules.damage(0, false, false) == 1.0, "bare skin: 1 per hit");
        assertTrue(Math.abs(AcidRules.damage(0, true, false) - 0.01) < 1e-9, "armor takes the hit");
        assertTrue(AcidRules.damage(2, false, false) == 0.5, "two hazmat pieces halve it");
        assertTrue(AcidRules.damageChance(4) == 0.0, "full hazmat: immune");
        assertTrue(AcidRules.damage(0, false, true) == 11.0, "iron golems are weak to acid");
        assertTrue(AcidRules.RESISTS.contains("alexscaves:nucleeper") && AcidRules.RESISTS.contains("minecraft:creeper"));
        assertTrue(Math.abs(AcidRules.overTicks(0.05, 5) - (1 - Math.pow(0.95, 5))) < 1e-12);
    }

    @Test
    void onlyToxicCavesWaterIsAcid() {
        assertTrue(ToxicAcid.isToxicBiome(new org.bukkit.NamespacedKey("alexscaves", "toxic_caves")));
        assertTrue(!ToxicAcid.isToxicBiome(org.bukkit.NamespacedKey.minecraft("river")));
    }

    @Test
    void ventSmokeFollowsWhatTheColumnStandsOn() {
        assertTrue(ToxicVents.smokeOf(org.bukkit.Material.WATER, true) == ToxicVents.Smoke.ACID, "acid gives radon");
        assertTrue(ToxicVents.smokeOf(org.bukkit.Material.WATER, false) == ToxicVents.Smoke.WATER);
        assertTrue(ToxicVents.smokeOf(org.bukkit.Material.LAVA, false) == ToxicVents.Smoke.LAVA);
        assertTrue(ToxicVents.smokeOf(org.bukkit.Material.MUD, true) == ToxicVents.Smoke.NONE);
    }
}
