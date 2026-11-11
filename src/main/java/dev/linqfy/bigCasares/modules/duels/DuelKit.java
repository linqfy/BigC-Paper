package dev.linqfy.bigCasares.modules.duels;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Equipment both players receive for a duel.
 *
 * @param armor helmet, chestplate, leggings and boots; null entries leave the slot empty
 */
record DuelKit(String id, String displayName, String icon, List<DuelItemSpec> items, List<DuelItemSpec> armor,
               DuelItemSpec offhand) {

    static final String[] ARMOR_KEYS = {"helmet", "chestplate", "leggings", "boots"};

    DuelKit {
        items = List.copyOf(items);
        armor = Collections.unmodifiableList(new ArrayList<>(armor));
    }

    static DuelKit load(String id, ConfigurationSection section) {
        List<DuelItemSpec> items = new ArrayList<>();
        for (String raw : section.getStringList("items")) {
            items.add(DuelItemSpec.parse(raw));
        }
        if (items.size() > 36) {
            throw new IllegalArgumentException("El kit " + id + " tiene más de 36 items.");
        }
        List<DuelItemSpec> armor = new ArrayList<>(4);
        for (String key : ARMOR_KEYS) {
            String raw = section.getString(key);
            armor.add(raw == null || raw.isBlank() ? null : DuelItemSpec.parse(raw));
        }
        String offhand = section.getString("offhand");
        return new DuelKit(
            id.toLowerCase(Locale.ROOT),
            section.getString("name", id),
            section.getString("icon", items.isEmpty() ? "CHEST" : items.getFirst().material()).toUpperCase(Locale.ROOT),
            items,
            armor,
            offhand == null || offhand.isBlank() ? null : DuelItemSpec.parse(offhand)
        );
    }
}
