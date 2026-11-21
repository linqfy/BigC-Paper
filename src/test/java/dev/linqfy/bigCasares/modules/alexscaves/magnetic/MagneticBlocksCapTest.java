package dev.linqfy.bigCasares.modules.alexscaves.magnetic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagneticBlocksCapTest {

    @Test
    void zapQueueStopsAtTheCap() {
        assertTrue(MagneticBlocks.canQueueZap(0));
        assertTrue(MagneticBlocks.canQueueZap(MagneticBlocks.MAX_PENDING_ZAPS - 1));
        assertFalse(MagneticBlocks.canQueueZap(MagneticBlocks.MAX_PENDING_ZAPS));
    }
}
