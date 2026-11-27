package dev.linqfy.bigCasares.modules.alexscaves;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AlexsCavesItemMenuTest {
    @Test
    void keepsOnlyModuleItemsSorted() {
        assertEquals(List.of("ac_a", "ac_b"), AlexsCavesItemMenu.ids(List.of("nexus", "ac_b", "ac_a", "tac_x")));
    }

    @Test
    void pagesHoldFortyFiveItems() {
        assertEquals(1, AlexsCavesItemMenu.pages(0));
        assertEquals(1, AlexsCavesItemMenu.pages(45));
        assertEquals(2, AlexsCavesItemMenu.pages(46));
        assertEquals(1, AlexsCavesItemMenu.clampPage(9, 46));
        assertEquals(0, AlexsCavesItemMenu.clampPage(-3, 46));
    }
}
