package dev.linqfy.bigCasares.modules.mythicdungeons;

import dev.linqfy.bigCasares.modules.mythicdungeons.SummonerUse.Request;
import dev.linqfy.bigCasares.modules.mythicdungeons.SummonerUse.Result;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SummonerUseTest {

    private static final MythicCulture ZEUS = MythicCulture.GREEK;

    private static SummonerUse.Outcome decide(Optional<MythicCulture> dungeon, boolean altar, boolean altarInside,
                                              boolean alive, boolean system) {
        return SummonerUse.decide(new Request(ZEUS, dungeon, altar, altarInside, alive, system));
    }

    @Test
    void outsideEveryDungeonExplainsWhereItWorks() {
        SummonerUse.Outcome outcome = decide(Optional.empty(), true, true, false, true);
        assertEquals(Result.NOT_IN_DUNGEON, outcome.result());
        assertTrue(outcome.message().contains("Solo podés usar el invocador dentro de la mazmorra griega"));
        assertTrue(outcome.message().contains("Zeus"));
    }

    @Test
    void anotherCulturesDungeonIsRefused() {
        SummonerUse.Outcome outcome = decide(Optional.of(MythicCulture.NORSE), true, true, false, true);
        assertEquals(Result.WRONG_DUNGEON, outcome.result());
        assertTrue(outcome.message().contains("la mazmorra nórdica"));
        assertEquals(Result.WRONG_DUNGEON, decide(Optional.of(MythicCulture.JAPANESE), true, true, false, true)
            .result());
    }

    @Test
    void mustBeTheAltarInsideTheDungeon() {
        assertEquals(Result.NOT_AT_ALTAR, decide(Optional.of(ZEUS), false, false, false, true).result());
        assertEquals(Result.NOT_AT_ALTAR, decide(Optional.of(ZEUS), true, false, false, true).result());
        assertTrue(decide(Optional.of(ZEUS), false, false, false, true).message().contains("altar"));
    }

    @Test
    void bossSystemOffAndBossAliveAreRefused() {
        assertEquals(Result.BOSS_SYSTEM_OFF, decide(Optional.of(ZEUS), true, true, false, false).result());
        SummonerUse.Outcome alive = decide(Optional.of(ZEUS), true, true, true, true);
        assertEquals(Result.BOSS_ALREADY_ALIVE, alive.result());
        assertTrue(alive.message().contains("ya está despierto"));
    }

    @Test
    void theAltarOfTheRightDungeonSummons() {
        SummonerUse.Outcome outcome = decide(Optional.of(ZEUS), true, true, false, true);
        assertTrue(outcome.success());
        assertEquals(Result.SUCCESS, outcome.result());
        assertFalse(outcome.message().isBlank());
    }

    @Test
    void everyRefusalHasASpanishMessage() {
        for (Result result : Result.values()) {
            SummonerUse.Outcome outcome = switch (result) {
                case SUCCESS -> decide(Optional.of(ZEUS), true, true, false, true);
                case NOT_IN_DUNGEON -> decide(Optional.empty(), false, false, false, true);
                case WRONG_DUNGEON -> decide(Optional.of(MythicCulture.EGYPTIAN), true, true, false, true);
                case NOT_AT_ALTAR -> decide(Optional.of(ZEUS), false, false, false, true);
                case BOSS_ALREADY_ALIVE -> decide(Optional.of(ZEUS), true, true, true, true);
                case BOSS_SYSTEM_OFF -> decide(Optional.of(ZEUS), true, true, true, false);
            };
            assertEquals(result, outcome.result());
            assertTrue(outcome.message().startsWith("§"), result.name());
        }
    }
}
