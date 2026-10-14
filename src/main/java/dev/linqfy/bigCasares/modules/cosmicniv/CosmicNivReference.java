package dev.linqfy.bigCasares.modules.cosmicniv;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A reference loadout: armor per slot, one weapon per weapon family and one tool per tool family. Missing entries
 * count as empty.
 */
public record CosmicNivReference(
    Map<CosmicNivArmorSlot, CosmicNivItem> armor,
    Map<String, CosmicNivItem> weapons,
    Map<String, CosmicNivItem> tools
) {
    public CosmicNivReference {
        EnumMap<CosmicNivArmorSlot, CosmicNivItem> armorCopy = new EnumMap<>(CosmicNivArmorSlot.class);
        armorCopy.putAll(Objects.requireNonNull(armor, "armor"));
        armor = Collections.unmodifiableMap(armorCopy);
        weapons = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(weapons, "weapons")));
        tools = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(tools, "tools")));
    }

    public static CosmicNivReference empty() {
        return new CosmicNivReference(Map.of(), Map.of(), Map.of());
    }

    public Map<String, CosmicNivItem> held(CosmicNivItemCategory category) {
        return category == CosmicNivItemCategory.TOOL ? tools : weapons;
    }
}
