package dev.linqfy.bigCasares.modules.forex;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForexFormatTest {

    @Test
    void formatsMoneyPricesAndChangesInTheServerStyle() {
        assertEquals("$12.500", ForexFormat.money(12_500));
        assertEquals("$1.234,50", ForexFormat.price(1234.5));
        assertEquals("$0,0000123", ForexFormat.price(0.0000123), "a collapsed coin is not shown as zero");
        assertEquals("§a+3,2%", ForexFormat.change(0.032));
        assertEquals("§c-99,9%", ForexFormat.change(-0.999));
    }

    @Test
    void sparklineFollowsThePriceHistory() {
        String line = ForexFormat.sparkline(List.of(1.0, 2.0, 3.0, 8.0));
        assertTrue(line.startsWith("§a▁"));
        assertTrue(line.endsWith("█"));
        assertTrue(ForexFormat.sparkline(List.of(5.0, 1.0)).startsWith("§c"));
        assertEquals(1.0, ForexFormat.trend(List.of(2.0, 3.0, 4.0)), 1e-12);
    }

    @Test
    void theFuneralMarchStaysInsideTheNoteBlockRange() {
        for (ForexAnnouncer.Note note : ForexAnnouncer.FUNERAL_MARCH) {
            assertTrue(note.pitch() >= 0.5f && note.pitch() <= 2.0f);
            assertTrue(note.ticks() > 0);
        }
    }
}
