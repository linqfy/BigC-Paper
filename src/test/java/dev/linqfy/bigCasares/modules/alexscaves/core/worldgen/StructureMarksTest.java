package dev.linqfy.bigCasares.modules.alexscaves.core.worldgen;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class StructureMarksTest {
    @Test
    void mergeKeepsEachBoxOnce() {
        int[] old = {2, 0, -50, 0, 15, -40, 15};
        int[] merged = StructureMarks.merge(old, List.of(
            new int[] {2, 0, -50, 0, 15, -40, 15},
            new int[] {0, 1, 2, 3, 4, 5, 6},
            new int[] {0, 1, 2, 3, 4, 5, 6}));
        assertArrayEquals(new int[] {2, 0, -50, 0, 15, -40, 15, 0, 1, 2, 3, 4, 5, 6}, merged);
    }
}
