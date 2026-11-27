package dev.linqfy.bigCasares.modules.alexscaves.primordial;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class VolcanoCoreColumnTest {
    @Test
    void columnRangeIsClippedToTheWorld() {
        assertArrayEquals(new int[] {Volcano.CENTER_Y, Volcano.CENTER_Y + 56},
            Volcano.coreColumnRange(-64, 320));
        assertArrayEquals(new int[] {Volcano.CENTER_Y, Volcano.CENTER_Y + 10},
            Volcano.coreColumnRange(-64, Volcano.CENTER_Y + 11));
    }
}
