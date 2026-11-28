package dev.linqfy.bigCasares.modules.toffy;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A reference loadout: armor per slot, one weapon per weapon family and one tool per tool family. Missing entries
 * count as empty.
 */
public record ToffyReference(
    Map<ToffyArmorSlot, ToffyItem> armor,
    Map<String, ToffyItem> weapons,
    Map<String, ToffyItem> tools
) {
    public ToffyReference {
        EnumMap<ToffyArmorSlot, ToffyItem> armorCopy = new EnumMap<>(ToffyArmorSlot.class);
        armorCopy.putAll(Objects.requireNonNull(armor, "armor"));
        armor = Collections.unmodifiableMap(armorCopy);
        weapons = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(weapons, "weapons")));
        tools = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(tools, "tools")));
    }

    public static ToffyReference empty() {
        return new ToffyReference(Map.of(), Map.of(), Map.of());
    }

    public Map<String, ToffyItem> held(ToffyItemCategory category) {
        return category == ToffyItemCategory.TOOL ? tools : weapons;
    }
}
