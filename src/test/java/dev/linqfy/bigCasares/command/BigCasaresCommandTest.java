package dev.linqfy.bigCasares.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BigCasaresCommandTest {

    @Test
    void recognizesReloadShopAndDangerSubcommands() {
        assertTrue(BigCasaresCommand.isReload("reload"));
        assertTrue(BigCasaresCommand.isShop("shop"));
        assertTrue(BigCasaresCommand.isDanger("peligro"));
    }

    @Test
    void suggestsRootSubcommands() {
        assertEquals(14, BigCasaresCommand.rootSuggestions("").size());
        assertEquals(java.util.List.of("historial"), BigCasaresCommand.rootSuggestions("hi"));
        assertEquals(java.util.List.of("tideframe"), BigCasaresCommand.rootSuggestions("ti"));
        assertTrue(BigCasaresCommand.rootSuggestions("re").contains("reload"));
        assertTrue(BigCasaresCommand.rootSuggestions("pe").contains("peligro"));
        assertTrue(BigCasaresCommand.rootSuggestions("te").contains("team"));
        assertTrue(BigCasaresCommand.rootSuggestions("ne").contains("nexus"));
        assertTrue(BigCasaresCommand.rootSuggestions("bo").contains("boss"));
        assertTrue(BigCasaresCommand.rootSuggestions("pa").contains("pack"));
        assertTrue(BigCasaresCommand.rootSuggestions("it").contains("items"));
        assertTrue(BigCasaresCommand.rootSuggestions("tu").contains("tumba"));
        assertTrue(BigCasaresCommand.rootSuggestions("ba").contains("baltop"));
        assertEquals(java.util.List.of("items", "pack"), BigCasaresCommand.reloadSuggestions(""));
        assertEquals(java.util.List.of("info", "send"), BigCasaresCommand.packSuggestions(""));
        assertEquals(java.util.List.of("abrir"), BigCasaresCommand.tombstoneSuggestions(""));
    }

    @Test
    void suggestsCompleteTeamLifecycleAndColors() {
        assertEquals(
            java.util.List.of("create", "invite", "join", "leave", "kick", "dissolve", "rename", "tag", "color", "appearance"),
            BigCasaresCommand.teamSubcommandSuggestions("")
        );
        assertEquals(
            java.util.List.of("DARK_AQUA", "DARK_BLUE", "DARK_GRAY", "DARK_GREEN", "DARK_PURPLE", "DARK_RED"),
            BigCasaresCommand.teamColorSuggestions("dark_")
        );
    }

    @Test
    void suggestsBothPveBosses() {
        assertEquals(
            java.util.List.of("abyss-guardian", "tung-tung-sahur"),
            BigCasaresCommand.bossIdSuggestions("")
        );
        assertEquals(
            java.util.List.of("tung-tung-sahur"),
            BigCasaresCommand.bossIdSuggestions("tung")
        );
        assertTrue(BigCasaresCommand.isSupportedBossId("TUNG-TUNG-SAHUR"));
        assertTrue(BigCasaresCommand.isSupportedBossId("abyss-guardian"));
    }
}
