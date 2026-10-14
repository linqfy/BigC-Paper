package dev.linqfy.bigCasares.modules.cosmicniv;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Everything that can identify an item stack: its material, its BigCasares catalog id ({@code bigcasares:item_id}),
 * its {@code item_model} component and the persistent data values the config asks about. Used to recognize custom
 * items before the model values them.
 *
 * @param material       namespaced material key, for example {@code minecraft:iron_sword}
 * @param catalogId      BigCasares catalog id, or {@code null}
 * @param itemModel      namespaced item model, or {@code null} when the item uses its default model
 * @param persistentData string persistent data values, by namespaced key
 * @param enchantments   enchantment levels, by namespaced key
 */
public record CosmicNivItemIdentity(
    String material,
    String catalogId,
    String itemModel,
    Map<String, String> persistentData,
    Map<String, Integer> enchantments
) {
    public CosmicNivItemIdentity {
        material = CosmicNivKeys.normalize(material);
        catalogId = catalogId == null || catalogId.isBlank() ? null : catalogId.trim();
        itemModel = itemModel == null || itemModel.isBlank() ? null : CosmicNivKeys.normalize(itemModel);
        Map<String, String> data = new LinkedHashMap<>();
        Objects.requireNonNull(persistentData, "persistentData").forEach((key, value) -> {
            if (key != null && value != null) {
                data.put(CosmicNivKeys.normalize(key), value);
            }
        });
        persistentData = Collections.unmodifiableMap(data);
        enchantments = Map.copyOf(Objects.requireNonNull(enchantments, "enchantments"));
    }

    public static CosmicNivItemIdentity of(String material) {
        return new CosmicNivItemIdentity(material, null, null, Map.of(), Map.of());
    }
}
