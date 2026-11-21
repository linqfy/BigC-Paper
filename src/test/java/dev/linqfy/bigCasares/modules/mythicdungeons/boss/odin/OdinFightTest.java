package dev.linqfy.bigCasares.modules.mythicdungeons.boss.odin;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OdinFightTest {

    private static final OdinFight.Timings T = new OdinFight.Timings(10, 8, 3, 4, 20, 2, 30);

    @Test
    void staysInvulnerableInPhaseOneWhileGuardiansLive() {
        OdinFight fight = new OdinFight(T);
        assertEquals(1, fight.phase());
        assertFalse(fight.vulnerable());
        // antes de invocarlos, cero vivos no termina la fase
        assertEquals(Optional.empty(), fight.update(5, 0, true));
        fight.markGuardiansSummoned();
        assertEquals(Optional.empty(), fight.update(6, 3, true));
        assertEquals(OdinFight.Stage.GUARDIANS, fight.stage());
        assertFalse(fight.vulnerable());
    }

    @Test
    void killingTheLastGuardianStartsPhaseTwoWithTheHelmet() {
        OdinFight fight = new OdinFight(T);
        fight.markGuardiansSummoned();
        assertEquals(Optional.of(OdinFight.Stage.AWAKENING), fight.update(100, 0, true));
        assertEquals(2, fight.phase());
        assertFalse(fight.vulnerable());
        assertEquals(Optional.empty(), fight.update(109, 0, true));
        assertEquals(Optional.of(OdinFight.Stage.RECOVERING), fight.update(110, 0, true));
        assertEquals(Optional.of(OdinFight.Stage.WINDUP), fight.update(114, 0, true));
    }

    @Test
    void everySecondChargeEndsInAStunWindow() {
        OdinFight fight = toWindup(new OdinFight(T), 0);
        long tick = 14;
        assertEquals(2, fight.chargesUntilStun());

        tick = chargeAndEnd(fight, tick, false);
        assertEquals(OdinFight.Stage.RECOVERING, fight.stage());
        assertFalse(fight.vulnerable());
        assertEquals(1, fight.chargesUntilStun());

        assertEquals(Optional.of(OdinFight.Stage.WINDUP), fight.update(tick + 4, 0, true));
        tick = chargeAndEnd(fight, tick + 4, false);
        assertEquals(OdinFight.Stage.STUNNED, fight.stage());
        assertTrue(fight.vulnerable());
        assertFalse(fight.lastStunFromWall());
        assertEquals(2, fight.chargesUntilStun());
        assertEquals(20, fight.ticksLeft(tick));

        assertEquals(Optional.empty(), fight.update(tick + 19, 0, true));
        assertTrue(fight.vulnerable());
        assertEquals(Optional.of(OdinFight.Stage.RECOVERING), fight.update(tick + 20, 0, true));
        assertFalse(fight.vulnerable());
    }

    @Test
    void hittingAWallStunsImmediatelyAndResetsTheCount() {
        OdinFight fight = toWindup(new OdinFight(T), 0);
        long tick = chargeAndEnd(fight, 14, false);
        assertEquals(1, fight.chargesUntilStun());
        fight.update(tick + 4, 0, true);
        tick = chargeAndEnd(fight, tick + 4, true);
        assertEquals(OdinFight.Stage.STUNNED, fight.stage());
        assertTrue(fight.lastStunFromWall());
        assertEquals(2, fight.chargesUntilStun());

        // la primera embestida despues del aturdimiento contra una pared tambien aturde
        fight.update(tick + 20, 0, true);
        fight.update(tick + 24, 0, true);
        assertEquals(OdinFight.Stage.WINDUP, fight.stage());
        chargeAndEnd(fight, tick + 24, true);
        assertTrue(fight.vulnerable());
    }

    @Test
    void aChargeThatNeverEndsIsCutByTheSafetyLimit() {
        OdinFight fight = toWindup(new OdinFight(T), 0);
        assertEquals(Optional.of(OdinFight.Stage.CHARGING), fight.update(22, 0, true));
        assertEquals(Optional.empty(), fight.update(51, 0, true));
        assertEquals(Optional.of(OdinFight.Stage.RECOVERING), fight.update(52, 0, true));
        assertEquals(1, fight.chargesUntilStun());
    }

    @Test
    void theAimLocksBeforeTheChargeLeaves() {
        OdinFight fight = toWindup(new OdinFight(T), 0);
        assertFalse(fight.aimLocked(14));
        assertFalse(fight.aimLocked(16));
        assertTrue(fight.aimLocked(17));
        assertTrue(OdinFight.Timings.DEFAULT.lockedTicks() >= 16, "at least 0.8 s of locked warning");
    }

    @Test
    void withoutPlayersHeWaitsInsteadOfCharging() {
        OdinFight fight = new OdinFight(T);
        fight.markGuardiansSummoned();
        fight.update(0, 0, false);
        fight.update(10, 0, false);
        assertEquals(OdinFight.Stage.RECOVERING, fight.stage());
        assertEquals(Optional.empty(), fight.update(20, 0, false));
        assertEquals(OdinFight.Stage.RECOVERING, fight.stage());
        assertEquals(Optional.of(OdinFight.Stage.WINDUP), fight.update(24, 0, true));
    }

    @Test
    void chargeEndedOutsideAChargeChangesNothing() {
        OdinFight fight = new OdinFight(T);
        assertEquals(OdinFight.Stage.GUARDIANS, fight.chargeEnded(3, true));
        assertFalse(fight.vulnerable());
    }

    @Test
    void timingsRejectAWindupWithoutLockedWarning() {
        assertThrows(IllegalArgumentException.class, () -> new OdinFight.Timings(10, 8, 8, 4, 20, 2, 30));
        assertThrows(IllegalArgumentException.class, () -> new OdinFight.Timings(10, 8, 3, 4, 20, 0, 30));
    }

    /** Lleva la pelea a WINDUP en el tick 14 (awakening 0..10, recover 10..14). */
    private static OdinFight toWindup(OdinFight fight, long start) {
        fight.markGuardiansSummoned();
        fight.update(start, 0, true);
        fight.update(start + 10, 0, true);
        fight.update(start + 14, 0, true);
        assertEquals(OdinFight.Stage.WINDUP, fight.stage());
        return fight;
    }

    /** Del WINDUP que empezo en {@code windupStart} a CHARGING y lo termina; devuelve el tick del final. */
    private static long chargeAndEnd(OdinFight fight, long windupStart, boolean wall) {
        long chargeStart = windupStart + T.windupTicks();
        assertEquals(Optional.of(OdinFight.Stage.CHARGING), fight.update(chargeStart, 0, true));
        long end = chargeStart + 5;
        fight.chargeEnded(end, wall);
        return end;
    }
}
