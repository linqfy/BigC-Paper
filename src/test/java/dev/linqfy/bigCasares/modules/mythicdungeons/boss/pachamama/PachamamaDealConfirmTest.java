package dev.linqfy.bigCasares.modules.mythicdungeons.boss.pachamama;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PachamamaDealConfirmTest {

    private static final UUID ANA = UUID.randomUUID();
    private static final UUID BETO = UUID.randomUUID();

    @Test
    void oneClickOnlyArms() {
        PachamamaDealConfirm confirm = new PachamamaDealConfirm();
        assertEquals(PachamamaDealConfirm.Result.ARMED, confirm.click(ANA, 100));
        assertTrue(confirm.armed(ANA, 100));
        confirm.expire(100 + PachamamaTuning.DEAL_CONFIRM_TICKS + 1);
        assertFalse(confirm.armed(ANA, 100 + PachamamaTuning.DEAL_CONFIRM_TICKS + 1));
    }

    @Test
    void aSecondClickWithinTheWindowPays() {
        PachamamaDealConfirm confirm = new PachamamaDealConfirm();
        confirm.click(ANA, 100);
        assertEquals(PachamamaDealConfirm.Result.CONFIRMED, confirm.click(ANA, 120));
        // paid: the next click starts over
        assertEquals(PachamamaDealConfirm.Result.ARMED, confirm.click(ANA, 140));
        assertEquals(PachamamaDealConfirm.Result.CONFIRMED,
            confirm.click(ANA, 140 + PachamamaTuning.DEAL_CONFIRM_TICKS));
    }

    @Test
    void aSecondClickAfterTheWindowOnlyArmsAgain() {
        PachamamaDealConfirm confirm = new PachamamaDealConfirm();
        confirm.click(ANA, 100);
        assertEquals(PachamamaDealConfirm.Result.ARMED,
            confirm.click(ANA, 100 + PachamamaTuning.DEAL_CONFIRM_TICKS + 1));
    }

    @Test
    void anotherPlayersClickDoesNotConfirm() {
        PachamamaDealConfirm confirm = new PachamamaDealConfirm();
        confirm.click(ANA, 100);
        assertEquals(PachamamaDealConfirm.Result.ARMED, confirm.click(BETO, 120));
        assertEquals(PachamamaDealConfirm.Result.CONFIRMED, confirm.click(BETO, 130));
        assertEquals(PachamamaDealConfirm.Result.CONFIRMED, confirm.click(ANA, 140));
    }

    @Test
    void aHeldUseKeyNeverPays() {
        PachamamaDealConfirm confirm = new PachamamaDealConfirm();
        assertEquals(PachamamaDealConfirm.Result.ARMED, confirm.click(ANA, 0));
        // the client repeats a held right-click every 4 ticks, for as long as it is held
        for (long tick = 4; tick <= 200; tick += 4) {
            assertTrue(confirm.click(ANA, tick) != PachamamaDealConfirm.Result.CONFIRMED, "tick " + tick);
        }
    }
}
