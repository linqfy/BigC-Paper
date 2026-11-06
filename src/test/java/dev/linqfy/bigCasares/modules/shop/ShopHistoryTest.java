package dev.linqfy.bigCasares.modules.shop;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShopHistoryTest {
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-04T12:00:00Z"));
    private final ShopHistory history = new ShopHistory(now::get);
    private final UUID juan = UUID.randomUUID();

    @Test
    void newestFirstWithHowLongAgo() {
        history.record(juan, "Compraste stone x4 por $256.");
        now.set(now.get().plus(Duration.ofMinutes(5)));
        history.record(juan, "Vendiste bread por $2.");
        now.set(now.get().plus(Duration.ofSeconds(20)));

        assertEquals(List.of("§6Tus últimas operaciones en la tienda", "§8recién §fVendiste bread por $2.",
            "§8hace 5 min §fCompraste stone x4 por $256."), history.lines(juan));
    }

    @Test
    void keepsOnlyTheLastTenAndEachPlayerTheirOwn() {
        for (int i = 0; i < 12; i++) history.record(juan, "op " + i);
        List<String> lines = history.lines(juan);
        assertEquals(1 + ShopHistory.KEPT, lines.size());
        assertEquals("§8recién §fop 11", lines.get(1));
        assertEquals("§8recién §fop 2", lines.getLast());
        assertEquals("§7Todavía no compraste ni vendiste nada.", history.lines(UUID.randomUUID()).get(1));
    }

    @Test
    void whatIsKeptWithThePlayerComesBackAfterARestart() {
        history.record(juan, "Compraste stone por $64.");
        now.set(now.get().plusSeconds(120));
        history.record(juan, "Vendiste\tbread\npor $2.");
        String kept = history.encode(juan);

        ShopHistory afterRestart = new ShopHistory(now::get);
        afterRestart.restore(juan, kept + "\nbasura\n12x\tnada");
        assertEquals(java.util.Optional.of("Vendiste bread por $2."), afterRestart.latest(juan), "tabs and line breaks flattened");
        assertEquals(List.of("§6Tus últimas operaciones en la tienda", "§8recién §fVendiste bread por $2.",
            "§8hace 2 min §fCompraste stone por $64."), afterRestart.lines(juan), "unreadable lines skipped");

        afterRestart.record(juan, "Compraste apple por $5.");
        afterRestart.restore(juan, kept);
        assertEquals(java.util.Optional.of("Compraste apple por $5."), afterRestart.latest(juan),
            "a later restore never replaces this session's operations");
    }

    @Test
    void agesReadInMinutesHoursAndDays() {
        assertEquals("hace 59 min", ShopHistory.ago(Duration.ofMinutes(59)));
        assertEquals("hace 3 h", ShopHistory.ago(Duration.ofHours(3).plusMinutes(10)));
        assertEquals("hace 2 d", ShopHistory.ago(Duration.ofDays(2)));
    }
}
