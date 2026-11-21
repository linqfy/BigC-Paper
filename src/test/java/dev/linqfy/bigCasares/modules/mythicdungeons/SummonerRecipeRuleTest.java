package dev.linqfy.bigCasares.modules.mythicdungeons;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SummonerRecipeRuleTest {

    private static List<String> fragments(MythicCulture culture, int count) {
        return Collections.nCopies(count, culture.fragmentItemId().orElseThrow());
    }

    @Test
    void fourFragmentsOfTheSameBossMakeItsSummoner() {
        for (MythicCulture culture : MythicCulture.bossCultures()) {
            assertEquals(Optional.of(culture), SummonerRecipeRule.result(fragments(culture, 4)));
        }
    }

    @Test
    void mixesAndWrongCountsAreRefused() {
        assertTrue(SummonerRecipeRule.result(fragments(MythicCulture.GREEK, 3)).isEmpty());
        assertTrue(SummonerRecipeRule.result(fragments(MythicCulture.GREEK, 5)).isEmpty());
        assertTrue(SummonerRecipeRule.result(List.of("hate_fragment_greek", "hate_fragment_greek",
            "hate_fragment_greek", "hate_fragment_norse")).isEmpty());
        assertTrue(SummonerRecipeRule.result(List.of()).isEmpty());
        assertTrue(SummonerRecipeRule.result(null).isEmpty());
    }

    @Test
    void plainVanillaItemsNeverCount() {
        // a plain echo shard (the fragments' base material) has no catalog id
        assertTrue(SummonerRecipeRule.result(Arrays.asList(null, null, null, null)).isEmpty());
        assertTrue(SummonerRecipeRule.result(Arrays.asList("hate_fragment_egyptian", "hate_fragment_egyptian",
            "hate_fragment_egyptian", null)).isEmpty());
        assertTrue(SummonerRecipeRule.result(List.of("echo_shard", "echo_shard", "echo_shard", "echo_shard"))
            .isEmpty());
        assertTrue(SummonerRecipeRule.result(List.of("boss_summoner_medieval", "hate_fragment_medieval",
            "hate_fragment_medieval", "hate_fragment_medieval")).isEmpty());
        // a culture that does not exist has no fragments at all
        assertTrue(SummonerRecipeRule.result(Collections.nCopies(4, "hate_fragment_atlantean")).isEmpty());
    }

    @Test
    void theTableKeepsTheCatalogResultWhenItIsRight() {
        SummonerRecipeRule.Decision decision = SummonerRecipeRule.decide(fragments(MythicCulture.NORSE, 4),
            Optional.of(MythicCulture.NORSE), false);
        assertEquals(SummonerRecipeRule.Action.KEEP, decision.action());
    }

    @Test
    void theModuleRecipeAlwaysGetsAFreshSummonerOfTheRightBoss() {
        for (MythicCulture culture : MythicCulture.bossCultures()) {
            // la receta propia propone siempre el mismo Invocador de muestra (el griego)
            SummonerRecipeRule.Decision decision = SummonerRecipeRule.decide(fragments(culture, 4),
                Optional.of(MythicCulture.GREEK), true);
            assertEquals(SummonerRecipeRule.Action.SET, decision.action(), culture.id());
            assertEquals(Optional.of(culture), decision.culture());
        }
    }

    @Test
    void aWrongSummonerFromAnyRecipeIsReplacedOrCleared() {
        assertEquals(Optional.of(MythicCulture.EGYPTIAN), SummonerRecipeRule.decide(
            fragments(MythicCulture.EGYPTIAN, 4), Optional.of(MythicCulture.GREEK), false).culture());
        // cuatro ecos vanilla o fragmentos mezclados en la receta propia: no sale nada
        assertEquals(SummonerRecipeRule.Action.CLEAR, SummonerRecipeRule.decide(Arrays.asList(null, null, null,
            null), Optional.of(MythicCulture.GREEK), true).action());
        assertEquals(SummonerRecipeRule.Action.CLEAR, SummonerRecipeRule.decide(List.of("hate_fragment_greek",
            "hate_fragment_greek", "hate_fragment_greek", "hate_fragment_norse"), Optional.empty(), true).action());
        assertEquals(SummonerRecipeRule.Action.CLEAR, SummonerRecipeRule.decide(fragments(MythicCulture.GREEK, 3),
            Optional.of(MythicCulture.GREEK), false).action());
    }

    @Test
    void fragmentsNeverServeAsTheirVanillaBaseInOtherRecipes() {
        // por ejemplo la brujula de recuperacion, que lleva fragmentos de eco
        List<String> compass = Arrays.asList(null, "hate_fragment_greek", null, null, null, null, null, null);
        assertEquals(SummonerRecipeRule.Action.CLEAR,
            SummonerRecipeRule.decide(compass, Optional.empty(), false).action());
        assertEquals(SummonerRecipeRule.Action.KEEP,
            SummonerRecipeRule.decide(Arrays.asList(null, null, null), Optional.empty(), false).action());
    }

    @Test
    void recognisesTheModuleItems() {
        assertTrue(SummonerRecipeRule.isMythicItem("hate_fragment_greek"));
        assertTrue(SummonerRecipeRule.isMythicItem("boss_summoner_mesopotamian"));
        assertFalse(SummonerRecipeRule.isMythicItem("hate_fragment_atlantean"));
        assertFalse(SummonerRecipeRule.isMythicItem("keep_inventory_totem"));
        assertFalse(SummonerRecipeRule.isMythicItem(null));
    }
}
