package dev.linqfy.bigCasares.modules.shop;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ShopCatalog {

    private final List<ShopCategory> categories;
    private final Map<String, ShopCategory> categoriesById;

    public ShopCatalog(List<ShopCategory> categories) {
        this.categories = List.copyOf(categories);
        this.categoriesById = categories.stream().collect(
            java.util.stream.Collectors.toMap(
                category -> normalize(category.id()),
                category -> category,
                (left, right) -> left,
                java.util.LinkedHashMap::new
            )
        );
    }

    public List<ShopCategory> categories() {
        return categories;
    }

    public Optional<ShopCategory> category(String id) {
        return Optional.ofNullable(categoriesById.get(normalize(id)));
    }

    public Optional<ShopEntry> entry(String categoryId, String entryId) {
        String normalizedEntryId = normalize(entryId);
        return category(categoryId).flatMap(category -> category.entries().stream()
            .filter(entry -> normalize(entry.id()).equals(normalizedEntryId))
            .findFirst());
    }

    /**
     * The {@code category/entry} key of an entry of this catalog (a favourites copy already carries it as its id), or
     * empty for an entry it does not have. Offers and daily limits are kept by this key.
     */
    public Optional<String> keyOf(ShopEntry entry) {
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.id().contains("/")) {
            return Optional.of(entry.id());
        }
        for (ShopCategory category : categories) {
            for (ShopEntry candidate : category.entries()) {
                if (candidate == entry || candidate.equals(entry)) {
                    return Optional.of(category.id() + "/" + candidate.id());
                }
            }
        }
        return Optional.empty();
    }

    private String normalize(String id) {
        return id.toLowerCase(Locale.ROOT).trim();
    }
}
