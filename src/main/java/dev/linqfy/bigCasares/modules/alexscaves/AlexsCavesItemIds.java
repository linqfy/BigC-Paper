package dev.linqfy.bigCasares.modules.alexscaves;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Catalog ids of the module, generated into {@code alexscaves/items.txt} by
 * {@code tools/alexscaves/generate_content.py}. The item catalog reads it to seed
 * the default YAML files and to accept the {@code alexs-caves} mechanic.
 */
public final class AlexsCavesItemIds {
    public static final String MECHANIC = "alexs-caves";
    public static final String PREFIX = "ac_";
    static final String RESOURCE = "alexscaves/items.txt";
    private static final List<String> IDS = load();
    private static final Set<String> ID_SET = Set.copyOf(IDS);

    private AlexsCavesItemIds() {
    }

    public static List<String> ids() {
        return IDS;
    }

    public static boolean contains(String id) {
        return ID_SET.contains(id);
    }

    /** File names under {@code content/items/} seeded from the JAR. */
    public static List<String> defaultFiles() {
        return IDS.stream().map(id -> id + ".yml").toList();
    }

    private static List<String> load() {
        try (InputStream input = AlexsCavesItemIds.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Falta " + RESOURCE + " en el JAR");
            }
            List<String> ids = new ArrayList<>();
            for (String line : new String(input.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                if (!trimmed.startsWith(PREFIX) || !trimmed.matches("[a-z0-9_]+")) {
                    throw new IllegalStateException("Id inválido en " + RESOURCE + ": " + trimmed);
                }
                ids.add(trimmed);
            }
            return List.copyOf(ids);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
