package dev.linqfy.bigCasares.modules.geyser;

/**
 * @param consumption how the Bedrock client consumes the item, or {@code null} when it is not consumable; the Java
 *                    item must carry the matching consumable component
 */
public record GeyserCustomItemDefinition(
    String javaBaseIdentifier,
    String javaModelIdentifier,
    String bedrockIdentifier,
    String icon,
    String displayName,
    GeyserItemConsumption consumption
) {
    public GeyserCustomItemDefinition(String javaBaseIdentifier, String javaModelIdentifier,
                                      String bedrockIdentifier, String icon, String displayName) {
        this(javaBaseIdentifier, javaModelIdentifier, bedrockIdentifier, icon, displayName, null);
    }

    public GeyserCustomItemDefinition {
        javaBaseIdentifier = requireText(javaBaseIdentifier, "javaBaseIdentifier");
        javaModelIdentifier = requireText(javaModelIdentifier, "javaModelIdentifier");
        bedrockIdentifier = requireText(bedrockIdentifier, "bedrockIdentifier");
        icon = requireText(icon, "icon");
        displayName = requireText(displayName, "displayName");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }
}
