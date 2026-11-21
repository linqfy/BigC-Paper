package dev.linqfy.bigCasares.modules.bloodmoon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BloodMoonAlexsCavesExclusionTest {

    @Test
    void onlyAlexsCavesTagsAreExcluded() {
        assertTrue(BukkitBloodMoonMobModifier.isAlexsCavesMob("alexscaves:teletor"));
        assertFalse(BukkitBloodMoonMobModifier.isAlexsCavesMob("jeremy"), "Jeremy keeps its Blood Moon behaviour");
        assertFalse(BukkitBloodMoonMobModifier.isAlexsCavesMob(null));
        assertFalse(BukkitBloodMoonMobModifier.isAlexsCavesMob(""));
    }
}
