package dev.linqfy.bigCasares.items.catalog;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Armor component of a catalog item: armor and toughness while worn in {@code slot}, and
 * optionally the equipment asset that draws it on the player ({@code namespace:id}, the
 * resource pack's {@code assets/<namespace>/equipment/<id>.json}).
 *
 * <p>Optional extras: {@code waterEfficiency} (water movement efficiency while worn, 0 to 1; the swimming boost of
 * diving legwear), {@code repairItem} (vanilla material that repairs it in an anvil instead of the base armor's) and
 * {@code enchantability} (enchantment value; 0 keeps the base item's).
 */
public record ItemArmorDefinition(String slot, double defense, double toughness, String equipmentModel,
                                  double waterEfficiency, String repairItem, int enchantability) {
    private static final Set<String> SLOTS = Set.of("HEAD", "CHEST", "LEGS", "FEET");

    /**
     * Text hashed into the catalog revision: the record's own {@code toString} of before the optional extras existed,
     * with them appended only when set, so armors that do not use them keep their revision.
     */
    public String revisionKey() {
        String key = "ItemArmorDefinition[slot=" + slot + ", defense=" + defense + ", toughness=" + toughness
            + ", equipmentModel=" + equipmentModel + "]";
        if (waterEfficiency != 0.0 || repairItem != null || enchantability != 0) {
            key += "{water=" + waterEfficiency + ",repair=" + repairItem + ",enchant=" + enchantability + "}";
        }
        return key;
    }

    public ItemArmorDefinition(String slot, double defense, double toughness, String equipmentModel) {
        this(slot, defense, toughness, equipmentModel, 0.0, null, 0);
    }

    public ItemArmorDefinition {
        slot = Objects.requireNonNull(slot, "slot").trim().toUpperCase(Locale.ROOT);
        if (!SLOTS.contains(slot)) {
            throw new IllegalArgumentException("armor slot must be HEAD, CHEST, LEGS or FEET: " + slot);
        }
        if (!Double.isFinite(defense) || defense < 0.0) {
            throw new IllegalArgumentException("armor defense must be finite and non-negative");
        }
        if (!Double.isFinite(toughness) || toughness < 0.0) {
            throw new IllegalArgumentException("armor toughness must be finite and non-negative");
        }
        if (!Double.isFinite(waterEfficiency) || waterEfficiency < 0.0 || waterEfficiency > 1.0) {
            throw new IllegalArgumentException("armor water efficiency must be between 0 and 1");
        }
        if (enchantability < 0) {
            throw new IllegalArgumentException("armor enchantability must not be negative");
        }
        if (repairItem != null) {
            repairItem = repairItem.trim().toUpperCase(Locale.ROOT);
            if (repairItem.isEmpty()) {
                repairItem = null;
            }
        }
        if (equipmentModel != null) {
            equipmentModel = equipmentModel.trim().toLowerCase(Locale.ROOT);
            if (!equipmentModel.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
                throw new IllegalArgumentException("equipment model must be namespace:id: " + equipmentModel);
            }
        }
    }
}
