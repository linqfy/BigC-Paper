package dev.linqfy.bigCasares.modules.cosmicniv;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A custom item from {@code cosmicniv_valores.yml}. Once recognized it is valued under the key
 * {@code custom:<id>}, which is also how references refer to it.
 *
 * @param id       name under {@code custom-items:}
 * @param match    how it is recognized
 * @param category where it counts
 * @param family   weapon or tool family; {@code null} for armor
 * @param value    base value, before enchantments
 * @param slot     armor slot it is worn in; {@code null} to infer it from {@code match.material} (or, if that is not
 *                 set either, any slot). Only for armor
 */
public record CosmicNivCustomItem(
    String id,
    CosmicNivCustomMatch match,
    CosmicNivItemCategory category,
    String family,
    double value,
    CosmicNivArmorSlot slot
) {
    public static final String NAMESPACE = "custom";
    private static final Pattern ID = Pattern.compile("[a-z0-9_-]+");

    public CosmicNivCustomItem {
        id = Objects.requireNonNull(id, "id").trim().toLowerCase(Locale.ROOT);
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("custom item ids may only use a-z, 0-9, _ and -");
        }
        Objects.requireNonNull(match, "match");
        Objects.requireNonNull(category, "category");
        family = family == null ? null : family.trim().toLowerCase(Locale.ROOT);
        if (category == CosmicNivItemCategory.ARMOR && family != null) {
            throw new IllegalArgumentException("armor custom items do not have a family");
        }
        if (category != CosmicNivItemCategory.ARMOR && family == null) {
            throw new IllegalArgumentException(category.configKey() + " custom items need a family");
        }
        if (category != CosmicNivItemCategory.ARMOR && slot != null) {
            throw new IllegalArgumentException("only armor custom items have a slot");
        }
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("value must be a finite number");
        }
    }

    public CosmicNivCustomItem(String id, CosmicNivCustomMatch match, CosmicNivItemCategory category, String family,
                               double value) {
        this(id, match, category, family, value, null);
    }

    public String key() {
        return NAMESPACE + ":" + id;
    }

    /**
     * The slot this armor item is worn in, or {@code null} when it fits any slot.
     */
    public CosmicNivArmorSlot armorSlot() {
        if (slot != null || category != CosmicNivItemCategory.ARMOR) {
            return slot;
        }
        return CosmicNivArmorSlot.ofItemKey(match.material()).orElse(null);
    }
}
