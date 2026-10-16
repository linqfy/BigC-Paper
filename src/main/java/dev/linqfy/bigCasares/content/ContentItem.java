package dev.linqfy.bigCasares.content;

import dev.linqfy.bigCasares.dimension.NamespacedId;

import java.util.List;
import java.util.Objects;

/**
 * A code-defined item belonging to a content module (e.g. the Sift).
 *
 * <p>Unlike operator-editable catalog items in {@code content/items/}, content
 * items ship with their module and are identified by the persistent-data key
 * {@code bigcasares:content_id}. They render through {@code item_model} and
 * resource-pack item definitions, so no custom-model-data is involved.</p>
 *
 * @param id          stable id, e.g. {@code the_sift:siftslate}
 * @param material    vanilla base material; for block items this is the backing block's
 *                    material so right-clicking triggers a normal placement
 * @param itemModel   item model id ({@code the_sift:siftslate})
 * @param nameKey     translation key shipped in the resource pack's lang files
 * @param fallbackName plain name shown when the pack is missing
 * @param maxStackSize 1..99
 * @param placesBlock custom block id this item places, or null
 * @param food        optional food values
 */
public record ContentItem(
    NamespacedId id,
    String material,
    NamespacedId itemModel,
    String nameKey,
    String fallbackName,
    List<String> lore,
    int maxStackSize,
    NamespacedId placesBlock,
    Food food
) {

    public record Food(int nutrition, float saturation, boolean canAlwaysEat) {
        public Food {
            if (nutrition < 0 || saturation < 0) {
                throw new IllegalArgumentException("Food values must be non-negative");
            }
        }
    }

    public ContentItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(material, "material");
        if (!material.matches("[A-Z0-9_]+")) {
            throw new IllegalArgumentException("Material must be a Bukkit material name: " + material);
        }
        Objects.requireNonNull(itemModel, "itemModel");
        Objects.requireNonNull(nameKey, "nameKey");
        Objects.requireNonNull(fallbackName, "fallbackName");
        lore = List.copyOf(lore);
        if (maxStackSize < 1 || maxStackSize > 99) {
            throw new IllegalArgumentException("maxStackSize must be within 1..99");
        }
    }

    public static Builder builder(NamespacedId id, String material) {
        return new Builder(id, material);
    }

    public static final class Builder {
        private final NamespacedId id;
        private final String material;
        private NamespacedId itemModel;
        private String nameKey;
        private String fallbackName;
        private List<String> lore = List.of();
        private int maxStackSize = 64;
        private NamespacedId placesBlock;
        private Food food;

        private Builder(NamespacedId id, String material) {
            this.id = id;
            this.material = material;
            this.itemModel = id;
        }

        public Builder model(NamespacedId model) { this.itemModel = model; return this; }
        public Builder name(String translationKey, String fallback) { this.nameKey = translationKey; this.fallbackName = fallback; return this; }
        public Builder lore(List<String> lines) { this.lore = lines; return this; }
        public Builder maxStack(int size) { this.maxStackSize = size; return this; }
        public Builder places(NamespacedId block) { this.placesBlock = block; return this; }
        public Builder food(int nutrition, float saturation, boolean canAlwaysEat) { this.food = new Food(nutrition, saturation, canAlwaysEat); return this; }

        public ContentItem build() {
            String key = nameKey == null ? "item." + id.namespace() + "." + id.path().replace('/', '.') : nameKey;
            String fallback = fallbackName == null ? id.path() : fallbackName;
            return new ContentItem(id, material, itemModel, key, fallback, lore, maxStackSize, placesBlock, food);
        }
    }
}
