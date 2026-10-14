package dev.linqfy.bigCasares.modules.cosmicniv;

import java.util.Locale;
import java.util.regex.Pattern;

public final class CosmicNivKeys {
    private static final Pattern VALID_KEY = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private CosmicNivKeys() {
    }

    /**
     * Normalizes {@code netherite_sword}, {@code NETHERITE_SWORD} or {@code minecraft:netherite_sword} into
     * {@code minecraft:netherite_sword}.
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Key must not be empty");
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        if (key.indexOf(':') < 0) {
            key = "minecraft:" + key;
        }
        if (!VALID_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid key: " + raw);
        }
        return key;
    }
}
