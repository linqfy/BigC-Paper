package dev.linqfy.bigCasares.dimension;

import java.util.Objects;

/**
 * Stable identity of a custom dimension.
 *
 * <p>The key becomes the datapack {@code dimension} (level stem) id, the
 * dimension-type id and the Bukkit world key. Paper names the Bukkit world
 * {@code <level-name>_<namespace>_<path>} and stores it under
 * {@code <level>/dimensions/<namespace>/<path>}; changing a key therefore
 * orphans the existing world data and must be treated like a migration.</p>
 */
public record DimensionKey(NamespacedId id) implements Comparable<DimensionKey> {

    public DimensionKey {
        Objects.requireNonNull(id, "id");
        if ("minecraft".equals(id.namespace())) {
            throw new IllegalArgumentException("Custom dimensions cannot use the minecraft namespace: " + id);
        }
        if (id.path().contains("/")) {
            // Paper derives the world folder/name from the path; keep it flat.
            throw new IllegalArgumentException("Dimension paths must not contain '/': " + id);
        }
    }

    public static DimensionKey of(String namespace, String path) {
        return new DimensionKey(NamespacedId.of(namespace, path));
    }

    public static DimensionKey parse(String value) {
        return new DimensionKey(NamespacedId.parse(value));
    }

    public String namespace() {
        return id.namespace();
    }

    public String path() {
        return id.path();
    }

    /** Bukkit world name Paper assigns to a datapack-defined level stem. */
    public String bukkitWorldName(String levelName) {
        return Objects.requireNonNull(levelName, "levelName") + "_" + id.namespace() + "_" + id.path();
    }

    public String asString() {
        return id.asString();
    }

    @Override
    public int compareTo(DimensionKey other) {
        return id.compareTo(other.id);
    }

    @Override
    public String toString() {
        return id.asString();
    }
}
