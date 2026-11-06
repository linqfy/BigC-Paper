package dev.linqfy.bigCasares.modules.shop;

import dev.linqfy.bigCasares.platform.HappyHour;
import org.bukkit.configuration.ConfigurationSection;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The purchase price of each product right now: the catalog's price, less the day's offer (a few products chosen
 * from the date, the same for everyone) or the happy hour, whichever takes more off; never both. Sale prices never
 * change, so nothing can be bought cheap and sold back dear. Every menu shows, and the shop charges, this price.
 */
final class ShopPricing {
    /** {@code ofertas} in shop.yml (off by default): how many products each day, and how much off (up to half). */
    record Offers(boolean enabled, int count, double discount) {
        static final Offers OFF = new Offers(false, 0, 0);

        Offers {
            count = Math.max(0, Math.min(10, count));
            discount = Math.max(0, Math.min(0.5, discount));
        }

        static Offers load(ConfigurationSection shopYml) {
            ConfigurationSection section = shopYml == null ? null : shopYml.getConfigurationSection("ofertas");
            return section == null || !section.getBoolean("enabled", false) ? OFF
                : new Offers(true, section.getInt("cantidad", 2), section.getDouble("descuento", 0.2));
        }
    }

    static final ShopPricing NONE = new ShopPricing(new ShopCatalog(List.of()), Offers.OFF, HappyHour.OFF, ZonedDateTime::now);

    private final ShopCatalog catalog;
    private final Offers offers;
    private final HappyHour happyHour;
    private final Supplier<ZonedDateTime> clock;

    ShopPricing(ShopCatalog catalog, Offers offers, HappyHour happyHour, Supplier<ZonedDateTime> clock) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.offers = Objects.requireNonNull(offers, "offers");
        this.happyHour = Objects.requireNonNull(happyHour, "happyHour");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** The day's offers ({@code category/entry} keys): products that can be bought, picked with the date as seed. */
    Set<String> offerKeys(LocalDate day) {
        if (!offers.enabled() || offers.count() == 0) {
            return Set.of();
        }
        List<String> keys = new ArrayList<>();
        for (ShopCategory category : catalog.categories()) {
            for (ShopEntry entry : category.entries()) {
                if (entry.buyPrice() > 0) keys.add(category.id() + "/" + entry.id());
            }
        }
        Collections.sort(keys);
        Collections.shuffle(keys, new Random(day.toEpochDay()));
        return Set.copyOf(keys.subList(0, Math.min(offers.count(), keys.size())));
    }

    /** What is taken off the entry's purchase price now (0 to 0.5). */
    double discount(ShopEntry entry) {
        ZonedDateTime now = clock.get();
        double offer = catalog.keyOf(entry).filter(offerKeys(now.toLocalDate())::contains).isPresent() ? offers.discount() : 0;
        double happy = happyHour.active(now) ? happyHour.shopDiscount() : 0;
        return Math.max(offer, happy);
    }

    /** The purchase price of one lot now, rounded to cents. */
    double buyPrice(ShopEntry entry) {
        return Math.round(entry.buyPrice() * (1 - discount(entry)) * 100) / 100.0;
    }

    /** The label of a discounted product ("OFERTA -20%", "HORA FELIZ -10%"), or "" at its normal price. */
    String tag(ShopEntry entry) {
        double discount = discount(entry);
        if (discount <= 0) {
            return "";
        }
        boolean happy = happyHour.active(clock.get()) && happyHour.shopDiscount() >= discount;
        return (happy ? "HORA FELIZ -" : "OFERTA -") + Math.round(discount * 100) + "%";
    }
}
