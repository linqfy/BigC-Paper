package dev.linqfy.bigCasares.modules.tideframe.engine;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One right click in a menu must not also count as a left click through the arm swing the client adds after it. */
class SwingFilterTest {
    private final AtomicLong now = new AtomicLong(5_000_000_000L);
    private final SwingFilter swings = new SwingFilter(now::get);

    @Test
    void aSwingWithNoRightClickBeforeIsALeftClick() {
        assertTrue(swings.isClick());
        assertTrue(swings.isClick(), "every plain swing counts");
    }

    @Test
    void theSwingsThatFollowARightClickAreItsEchoBothOfThem() {
        swings.rightClick();
        now.addAndGet(50_000_000L);
        assertFalse(swings.isClick(), "the left-click-air event of the echo");
        assertFalse(swings.isClick(), "and the animation event of the same packet");
    }

    @Test
    void aDeliberateLeftClickAfterTheWindowCountsAgain() {
        swings.rightClick();
        now.addAndGet(SwingFilter.WINDOW_NANOS);
        assertTrue(swings.isClick());
    }
}
