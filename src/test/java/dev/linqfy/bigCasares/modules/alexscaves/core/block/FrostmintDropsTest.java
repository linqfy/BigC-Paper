package dev.linqfy.bigCasares.modules.alexscaves.core.block;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FrostmintDropsTest {
    @Test
    void aDoubleFrostmintDropsTwoSlabsAndTheOthersOne() {
        assertEquals(2, NoteBlockService.amount(new ModBlockRegistry.Placed("frostmint", "double"), null));
        assertEquals(1, NoteBlockService.amount(new ModBlockRegistry.Placed("frostmint", "default"), null));
        assertEquals(1, NoteBlockService.amount(new ModBlockRegistry.Placed("frostmint", "top"), null));
    }
}
