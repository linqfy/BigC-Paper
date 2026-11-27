package dev.linqfy.bigCasares.items.catalog;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class CustomItemCatalog {

    private final Map<String, CustomItemDefinition> definitions;
    private final String revision;

    public CustomItemCatalog(Map<String, CustomItemDefinition> definitions) {
        Objects.requireNonNull(definitions, "definitions");
        Map<String, CustomItemDefinition> normalized = new LinkedHashMap<>();
        definitions.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
            .forEach(entry -> {
                String key = normalizeId(entry.getKey());
                CustomItemDefinition definition = Objects.requireNonNull(entry.getValue(), "definition");
                if (!key.equals(definition.id())) {
                    throw new IllegalArgumentException("catalog key does not match definition id: " + key);
                }
                if (normalized.put(key, definition) != null) {
                    throw new IllegalArgumentException("duplicate item id: " + key);
                }
            });
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("catalog must contain at least one definition");
        }
        this.definitions = Map.copyOf(normalized);
        this.revision = revisionOf(this.definitions);
    }

    public Optional<CustomItemDefinition> find(String id) {
        return Optional.ofNullable(definitions.get(normalizeId(id)));
    }

    public CustomItemDefinition require(String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("unknown custom item id: " + id));
    }

    public Map<String, CustomItemDefinition> definitions() {
        return definitions;
    }

    public int size() {
        return definitions.size();
    }

    public String revision() {
        return revision;
    }

    private static String normalizeId(String value) {
        return Objects.requireNonNull(value, "id").trim().toLowerCase(Locale.ROOT);
    }

    private static String revisionOf(Map<String, CustomItemDefinition> definitions) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            definitions.values().stream()
                .sorted(Comparator.comparing(CustomItemDefinition::id))
                .forEach(definition -> digest.update(serialized(definition).getBytes(StandardCharsets.UTF_8)));
            byte[] bytes = digest.digest();
            StringBuilder revision = new StringBuilder(16);
            for (int index = 0; index < 8; index++) {
                revision.append(String.format(Locale.ROOT, "%02x", bytes[index]));
            }
            return revision.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String serialized(CustomItemDefinition definition) {
        return String.join("|",
            definition.id(),
            definition.mechanic(),
            definition.material(),
            definition.itemModel(),
            definition.legacyCustomModelData().isPresent()
                ? String.valueOf(definition.legacyCustomModelData().getAsInt()) : "",
            definition.display().translationKey(),
            definition.display().fallbackName(),
            String.join("\\n", definition.display().lore()),
            String.valueOf(definition.maxStackSize()),
            definition.foodDefinition().map(Object::toString).orElse(""),
            definition.recipeDefinition().map(Object::toString).orElse(""),
            definition.appearance().javaItemDefinition(),
            definition.appearance().bedrockTexture(),
            // Only items with armor add this part, so existing item revisions stay the same.
            definition.armorDefinition().map(armor -> armor.revisionKey() + "|").orElse("")
                + "\n"
        );
    }
}
