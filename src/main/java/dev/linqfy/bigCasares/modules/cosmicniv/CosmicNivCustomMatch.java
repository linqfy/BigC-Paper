package dev.linqfy.bigCasares.modules.cosmicniv;

/**
 * How a custom item is recognized. Every condition that is set must hold; at least one must be set.
 *
 * @param material           required material, or {@code null}
 * @param catalogId          required BigCasares catalog id, or {@code null}
 * @param itemModel          required item model, or {@code null}
 * @param persistentDataKey  persistent data key that must be present as a string, or {@code null}
 * @param persistentDataValue value that key must have, or {@code null} for any value
 */
public record CosmicNivCustomMatch(
    String material,
    String catalogId,
    String itemModel,
    String persistentDataKey,
    String persistentDataValue
) {
    public CosmicNivCustomMatch {
        material = material == null ? null : CosmicNivKeys.normalize(material);
        catalogId = catalogId == null || catalogId.isBlank() ? null : catalogId.trim();
        itemModel = itemModel == null ? null : CosmicNivKeys.normalize(itemModel);
        persistentDataKey = persistentDataKey == null ? null : CosmicNivKeys.normalize(persistentDataKey);
        if (persistentDataValue != null && persistentDataKey == null) {
            throw new IllegalArgumentException("persistent-data value needs a persistent-data key");
        }
        if (material == null && catalogId == null && itemModel == null && persistentDataKey == null) {
            throw new IllegalArgumentException(
                "needs at least one of material, bigcasares-item, item-model or persistent-data");
        }
    }

    public boolean matches(CosmicNivItemIdentity identity) {
        if (material != null && !material.equals(identity.material())) {
            return false;
        }
        if (catalogId != null && !catalogId.equals(identity.catalogId())) {
            return false;
        }
        if (itemModel != null && !itemModel.equals(identity.itemModel())) {
            return false;
        }
        if (persistentDataKey != null) {
            String value = identity.persistentData().get(persistentDataKey);
            if (value == null || (persistentDataValue != null && !persistentDataValue.equals(value))) {
                return false;
            }
        }
        return true;
    }
}
