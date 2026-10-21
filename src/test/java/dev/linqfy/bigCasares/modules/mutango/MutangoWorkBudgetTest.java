package dev.linqfy.bigCasares.modules.mutango;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El presupuesto de tiempo de la construccion de ruinas: se acaba por reloj, no por cantidad de bloques. */
class MutangoWorkBudgetTest {

    @Test
    void anEmptyBudgetIsExhaustedFromTheStart() {
        assertTrue(new MutangoWorkBudget(0, () -> 0L).exhausted());
    }

    @Test
    void itIsNotExhaustedWhileTimeIsLeft() {
        AtomicLong now = new AtomicLong();
        MutangoWorkBudget budget = new MutangoWorkBudget(1_000, now::get);
        for (int i = 0; i < 1000; i++) {
            budget.charge(1);
        }
        assertFalse(budget.exhausted(), "el reloj no avanzo: queda tiempo");
    }

    @Test
    void itIsExhaustedOnceTheDeadlinePassesAtTheNextClockCheck() {
        AtomicLong now = new AtomicLong();
        MutangoWorkBudget budget = new MutangoWorkBudget(1_000, now::get);
        now.set(1_000);
        // el reloj no se mira en cada operacion: hasta el proximo chequeo sigue sin darse cuenta
        for (int i = 0; i < MutangoWorkBudget.CHECK_EVERY - 1; i++) {
            budget.charge(1);
            assertFalse(budget.exhausted());
        }
        budget.charge(1);
        assertTrue(budget.exhausted());
    }

    @Test
    void theClockIsReadOncePerBatchOfOperationsNotPerBlock() {
        AtomicLong reads = new AtomicLong();
        MutangoWorkBudget budget = new MutangoWorkBudget(Long.MAX_VALUE / 2, () -> {
            reads.incrementAndGet();
            return 0L;
        });
        long afterCreation = reads.get();
        for (int i = 0; i < MutangoWorkBudget.CHECK_EVERY * 10; i++) {
            budget.charge(1);
        }
        assertEquals(10, reads.get() - afterCreation);
    }

    @Test
    void expensiveOperationsReachTheCheckSooner() {
        AtomicLong now = new AtomicLong();
        MutangoWorkBudget budget = new MutangoWorkBudget(1, now::get);
        now.set(5);
        budget.charge(MutangoWorkBudget.CHECK_EVERY); // una sola operacion cara alcanza para mirar el reloj
        assertTrue(budget.exhausted());
    }
}
