package dev.linqfy.bigCasares.modules.shop;

import org.bukkit.ChatColor;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A page in the catalog or in the cross-category search results. Each offer keeps its server-side product key. */
record NativeShopPage(int category, int number, int pageCount, String query, List<Offer> offers) {
    static final int PAGE_SIZE = 16;

    record Offer(String categoryId, ShopEntry entry) {
        ShopEntry resolve(ShopCatalog catalog, boolean sell) {
            return catalog.entry(categoryId, entry.id()).filter(entry::equals)
                .filter(it -> it.amount() > 0 && tradable(sell ? it.sellPrice() : it.buyPrice())).orElse(null);
        }

        String name() {
            return plain(entry.displayName() == null ? entry.id().replace('_', ' ') : entry.displayName());
        }
    }

    static NativeShopPage select(ShopCatalog catalog, int category, int requestedPage, String query) {
        List<Offer> matches = new ArrayList<>();
        String words = normalize(query);
        if (words.isEmpty()) {
            ShopCategory chosen = catalog.categories().get(category);
            for (ShopEntry entry : chosen.entries()) matches.add(new Offer(chosen.id(), entry));
        } else {
            List<Offer> partial = new ArrayList<>();
            for (ShopCategory group : catalog.categories()) {
                for (ShopEntry entry : group.entries()) {
                    Offer offer = new Offer(group.id(), entry);
                    String name = normalize(offer.name());
                    if (name.startsWith(words)) matches.add(offer);
                    else if (name.contains(words)) partial.add(offer);
                }
            }
            matches.addAll(partial);
        }
        int count = Math.max(1, (matches.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int number = Math.clamp(requestedPage, 0, count - 1);
        int from = number * PAGE_SIZE;
        return new NativeShopPage(category, number, count, words.isEmpty() ? "" : query.strip(),
            List.copyOf(matches.subList(from, Math.min(from + PAGE_SIZE, matches.size()))));
    }

    static boolean tradable(double price) {
        return ShopService.tradable(price);
    }

    static String plain(String value) {
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', value));
    }

    private static String normalize(String value) {
        return Normalizer.normalize(plain(value), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT).strip().replaceAll("\\s+", " ");
    }
}
