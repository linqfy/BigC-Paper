package dev.linqfy.bigCasares.modules.shop;

import dev.linqfy.bigCasares.testsupport.FakePlayer;
import dev.linqfy.bigCasares.testsupport.MapPlayerStrings;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShopDailyLimitsTest {
    private static final ShopEntry LIMITED = new ShopEntry("diamante", 0, Material.DIAMOND, null, 1, 500, 50, null, List.of(), 3);
    private static final ShopEntry FREE = new ShopEntry("piedra", 1, Material.STONE, null, 64, 64, 4, null, List.of());
    private static final ShopCatalog CATALOG = new ShopCatalog(List.of(
        new ShopCategory("raros", "Raros", Material.DIAMOND, 0, List.of(LIMITED, FREE))));

    private final MapPlayerStrings store = new MapPlayerStrings();
    private final AtomicReference<LocalDate> today = new AtomicReference<>(LocalDate.parse("2026-11-07"));
    private final ShopDailyLimits limits = new ShopDailyLimits(store, today::get, CATALOG::keyOf);
    private final FakePlayer juan = new FakePlayer("juan");

    @Test
    void countsLotsPerDayAndStartsAgainTheNextDay() {
        assertEquals(3, limits.remaining(juan.player, LIMITED));
        limits.bought(juan.player, LIMITED, 2);
        assertEquals(1, limits.remaining(juan.player, LIMITED));
        limits.bought(juan.player, LIMITED, 1);
        assertEquals(0, limits.remaining(juan.player, LIMITED));
        today.set(today.get().plusDays(1));
        assertEquals(3, limits.remaining(juan.player, LIMITED), "a new day");
    }

    @Test
    void entriesWithoutALimitAreNeverCountedOrKept() {
        assertEquals(Integer.MAX_VALUE, limits.remaining(juan.player, FREE));
        limits.bought(juan.player, FREE, 10);
        assertEquals(java.util.Map.of(), store.values);
    }

    @Test
    void unreadableValuesCountAsNothingBought() {
        store.set(juan.player, ShopDailyLimits.KEY, "2026-11-07|raros/diamante=muchos,=3,raros/diamante");
        assertEquals(3, limits.remaining(juan.player, LIMITED));
        limits.bought(juan.player, LIMITED, 1);
        assertEquals(2, limits.remaining(juan.player, LIMITED));
    }
}
