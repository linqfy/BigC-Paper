package dev.linqfy.bigCasares.dimension;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A validated Minecraft resource location ({@code namespace:path}).
 *
 * <p>Used for every data-driven resource the framework emits (biomes, features,
 * noises, timelines, loot tables). It is deliberately independent from Bukkit's
 * {@code NamespacedKey} so that definitions stay testable without a server.</p>
 */
public record NamespacedId(String namespace, String path) implements Comparable<NamespacedId> {

    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9_./-]+");

    public NamespacedId {
        namespace = Objects.requireNonNull(namespace, "namespace").trim().toLowerCase(Locale.ROOT);
        path = Objects.requireNonNull(path, "path").trim().toLowerCase(Locale.ROOT);
        if (!NAMESPACE.matcher(namespace).matches()) {
            throw new IllegalArgumentException("Invalid resource namespace: " + namespace);
        }
        if (!PATH.matcher(path).matches() || path.startsWith("/") || path.endsWith("/")
            || path.contains("//") || path.contains("..")) {
            throw new IllegalArgumentException("Invalid resource path: " + path);
        }
    }

    public static NamespacedId of(String namespace, String path) {
        return new NamespacedId(namespace, path);
    }

    public static NamespacedId minecraft(String path) {
        return new NamespacedId("minecraft", path);
    }

    /** Parses {@code namespace:path}; a missing namespace defaults to {@code minecraft}. */
    public static NamespacedId parse(String value) {
        String trimmed = Objects.requireNonNull(value, "value").trim();
        int separator = trimmed.indexOf(':');
        if (separator < 0) {
            return minecraft(trimmed);
        }
        if (trimmed.indexOf(':', separator + 1) >= 0) {
            throw new IllegalArgumentException("Invalid resource location: " + value);
        }
        return new NamespacedId(trimmed.substring(0, separator), trimmed.substring(separator + 1));
    }

    /** Creates a child id whose path is {@code path + "/" + child}. */
    public NamespacedId child(String child) {
        return new NamespacedId(namespace, path + "/" + child);
    }

    public String asString() {
        return namespace + ":" + path;
    }

    @Override
    public int compareTo(NamespacedId other) {
        int byNamespace = namespace.compareTo(other.namespace);
        return byNamespace != 0 ? byNamespace : path.compareTo(other.path);
    }

    @Override
    public String toString() {
        return asString();
    }
}
