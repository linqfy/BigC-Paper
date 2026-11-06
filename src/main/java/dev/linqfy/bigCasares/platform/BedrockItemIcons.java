package dev.linqfy.bigCasares.platform;

import org.bukkit.Material;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Bedrock form icons for vanilla materials: a texture path every Bedrock client has, or none. Only paths listed in
 * {@code geyser/bedrock-textures.txt} (the vanilla pack) are used, so a button never shows a missing texture.
 */
public final class BedrockItemIcons {
    private static final String LIST = "geyser/bedrock-textures.txt";
    /** Java names whose Bedrock texture has another name; every target is checked against the list. */
    static final Map<String, String> RENAMED = Map.ofEntries(
        Map.entry("golden_apple", "textures/items/apple_golden"),
        Map.entry("enchanted_golden_apple", "textures/items/apple_golden"),
        Map.entry("golden_carrot", "textures/items/carrot_golden"),
        Map.entry("cooked_beef", "textures/items/beef_cooked"),
        Map.entry("cooked_chicken", "textures/items/chicken_cooked"),
        Map.entry("cooked_porkchop", "textures/items/porkchop_cooked"),
        Map.entry("cooked_cod", "textures/items/fish_cooked"),
        Map.entry("cooked_salmon", "textures/items/fish_salmon_cooked"),
        Map.entry("heart_of_the_sea", "textures/items/heartofthesea_closed"),
        Map.entry("slime_ball", "textures/items/slimeball"),
        Map.entry("golden_sword", "textures/items/gold_sword"),
        Map.entry("golden_pickaxe", "textures/items/gold_pickaxe"),
        Map.entry("golden_axe", "textures/items/gold_axe")
    );
    private static volatile Set<String> available;

    private BedrockItemIcons() {
    }

    /** The icon of {@code material}, when Bedrock's own pack has one. */
    public static Optional<String> path(Material material) {
        if (material == null) {
            return Optional.empty();
        }
        String name = material.name().toLowerCase(Locale.ROOT);
        Set<String> paths = available();
        String renamed = RENAMED.get(name);
        if (renamed != null && paths.contains(renamed)) {
            return Optional.of(renamed);
        }
        for (String folder : new String[] {"textures/items/", "textures/blocks/"}) {
            if (paths.contains(folder + name)) {
                return Optional.of(folder + name);
            }
        }
        return Optional.empty();
    }

    static Set<String> available() {
        Set<String> loaded = available;
        if (loaded == null) {
            loaded = load();
            available = loaded;
        }
        return loaded;
    }

    private static Set<String> load() {
        try (InputStream input = BedrockItemIcons.class.getClassLoader().getResourceAsStream(LIST)) {
            if (input == null) {
                return Set.of();
            }
            Set<String> paths = new HashSet<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    String path = line.strip();
                    if (!path.isEmpty() && !path.startsWith("#")) {
                        paths.add(path);
                    }
                }
            }
            return Set.copyOf(paths);
        } catch (IOException failure) {
            throw new UncheckedIOException("No se pudo leer " + LIST, failure);
        }
    }
}
