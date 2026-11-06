package dev.linqfy.bigCasares.modules.shop;

import dev.linqfy.bigCasares.platform.HappyHour;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopPricingTest {
    private static final ShopCatalog CATALOG = catalog();
    private final AtomicReference<ZonedDateTime> now = new AtomicReference<>(ZonedDateTime.parse("2026-11-07T12:00:00Z"));

    private static ShopCatalog catalog() {
        List<ShopEntry> entries = new ArrayList<>();
        for (int i = 0; i < 8; i++) entries.add(new ShopEntry("e" + i, i, Material.STONE, null, 1, 100, 10, null, List.of()));
        entries.add(new ShopEntry("gratis", 9, Material.DIRT, null, 1, 0, 1, null, List.of()));
        return new ShopCatalog(List.of(new ShopCategory("c", "C", Material.STONE, 0, entries)));
    }

    private ShopPricing pricing(ShopPricing.Offers offers, HappyHour happy) {
        return new ShopPricing(CATALOG, offers, happy, now::get);
    }

    @Test
    void offersAreTheSameAllDayForEveryoneAndChangeWithTheDate() {
        ShopPricing pricing = pricing(new ShopPricing.Offers(true, 2, 0.2), HappyHour.OFF);
        LocalDate day = LocalDate.parse("2026-11-07");
        Set<String> today = pricing.offerKeys(day);
        assertEquals(2, today.size());
        assertEquals(today, pricing.offerKeys(day), "deterministic");
        assertTrue(today.stream().noneMatch(key -> key.endsWith("/gratis")), "a free product is never an offer");
        boolean changes = false;
        for (int i = 1; i <= 7 && !changes; i++) changes = !pricing.offerKeys(day.plusDays(i)).equals(today);
        assertTrue(changes, "other days pick other products");
    }

    @Test
    void anOfferLowersOnlyThePurchasePriceOfItsProducts() {
        ShopPricing pricing = pricing(new ShopPricing.Offers(true, 1, 0.2), HappyHour.OFF);
        String offered = pricing.offerKeys(now.get().toLocalDate()).iterator().next();
        ShopEntry entry = CATALOG.entry("c", offered.substring(2)).orElseThrow();
        assertEquals(80, pricing.buyPrice(entry));
        assertEquals("OFERTA -20%", pricing.tag(entry));
        ShopEntry other = CATALOG.categories().getFirst().entries().stream()
            .filter(candidate -> !("c/" + candidate.id()).equals(offered) && candidate.buyPrice() > 0).findFirst().orElseThrow();
        assertEquals(100, pricing.buyPrice(other));
        assertEquals("", pricing.tag(other));
    }

    @Test
    void theHappyHourAndAnOfferNeverAddUpTheBiggerWins() {
        HappyHour saturday = new HappyHour(true, Set.of(DayOfWeek.SATURDAY), LocalTime.of(10, 0), LocalTime.of(14, 0), 2, 0.1);
        ShopPricing pricing = pricing(new ShopPricing.Offers(true, 1, 0.2), saturday);
        String offered = pricing.offerKeys(now.get().toLocalDate()).iterator().next();
        ShopEntry entry = CATALOG.entry("c", offered.substring(2)).orElseThrow();
        assertEquals(80, pricing.buyPrice(entry), "20% offer beats the 10% happy hour");
        ShopEntry other = CATALOG.categories().getFirst().entries().stream()
            .filter(candidate -> !("c/" + candidate.id()).equals(offered) && candidate.buyPrice() > 0).findFirst().orElseThrow();
        assertEquals(90, pricing.buyPrice(other));
        assertEquals("HORA FELIZ -10%", pricing.tag(other));
        now.set(ZonedDateTime.parse("2026-11-07T15:00:00Z"));
        assertEquals(100, pricing.buyPrice(other), "after the happy hour, the normal price");
        assertNotEquals("", pricing.tag(entry));
    }

    @Test
    void offersOffMeansCatalogPrices() {
        ShopPricing pricing = pricing(ShopPricing.Offers.OFF, HappyHour.OFF);
        assertEquals(Set.of(), pricing.offerKeys(now.get().toLocalDate()));
        CATALOG.categories().getFirst().entries().forEach(entry -> assertEquals(entry.buyPrice(), pricing.buyPrice(entry)));
        assertEquals(ShopPricing.Offers.OFF, ShopPricing.Offers.load(
            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File("src/main/resources/shop.yml"))),
            "the bundled shop.yml leaves offers off");
    }
}
