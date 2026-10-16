package dev.linqfy.bigCasares.dimension.datapack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.dimension.json.Json;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Collects datapack files. Writing the same path twice is allowed only with
 * identical bytes (shared biomes of two instances); anything else is a conflict
 * that fails compilation instead of silently overwriting content. Tags are merged.
 */
public final class DatapackBuilder {

    private final TreeMap<String, byte[]> files = new TreeMap<>();
    private final Map<String, Set<String>> tags = new LinkedHashMap<>();

    /** Registry folder names for Minecraft 26.2 data packs. */
    public enum Registry {
        DIMENSION("dimension"),
        DIMENSION_TYPE("dimension_type"),
        WORLD_CLOCK("world_clock"),
        TIMELINE("timeline"),
        NOISE("worldgen/noise"),
        DENSITY_FUNCTION("worldgen/density_function"),
        NOISE_SETTINGS("worldgen/noise_settings"),
        BIOME("worldgen/biome"),
        CONFIGURED_FEATURE("worldgen/configured_feature"),
        PLACED_FEATURE("worldgen/placed_feature"),
        CONFIGURED_CARVER("worldgen/configured_carver"),
        STRUCTURE("worldgen/structure"),
        STRUCTURE_SET("worldgen/structure_set"),
        TEMPLATE_POOL("worldgen/template_pool"),
        PROCESSOR_LIST("worldgen/processor_list"),
        LOOT_TABLE("loot_table"),
        ADVANCEMENT("advancement"),
        JUKEBOX_SONG("jukebox_song"),
        RECIPE("recipe");

        private final String folder;

        Registry(String folder) {
            this.folder = folder;
        }

        public String folder() {
            return folder;
        }
    }

    public DatapackBuilder json(Registry registry, NamespacedId id, JsonElement json) {
        return file("data/" + id.namespace() + "/" + registry.folder() + "/" + id.path() + ".json", Json.bytes(json));
    }

    public DatapackBuilder structureTemplate(NamespacedId id, byte[] nbt) {
        return file("data/" + id.namespace() + "/structure/" + id.path() + ".nbt", nbt);
    }

    /** Adds entries to a tag ({@code registryFolder} e.g. {@code worldgen/biome}, {@code timeline}). */
    public DatapackBuilder tag(String registryFolder, NamespacedId tag, Collection<String> values) {
        String path = "data/" + tag.namespace() + "/tags/" + registryFolder + "/" + tag.path() + ".json";
        tags.computeIfAbsent(path, ignored -> new LinkedHashSet<>()).addAll(values);
        return this;
    }

    public DatapackBuilder file(String path, byte[] content) {
        String normalized = normalize(path);
        Objects.requireNonNull(content, "content");
        if (tags.containsKey(normalized)) {
            throw new IllegalArgumentException("Path is managed as a tag: " + normalized);
        }
        byte[] existing = files.get(normalized);
        if (existing != null && !Arrays.equals(existing, content)) {
            throw new IllegalArgumentException("Conflicting datapack file: " + normalized);
        }
        files.put(normalized, content.clone());
        return this;
    }

    public boolean contains(String path) {
        return files.containsKey(normalize(path)) || tags.containsKey(normalize(path));
    }

    public GeneratedDatapack build(String description) {
        TreeMap<String, byte[]> output = new TreeMap<>(files);
        tags.forEach((path, values) -> {
            JsonArray array = new JsonArray();
            values.stream().sorted().forEach(array::add);
            JsonObject tag = Json.object().put("values", array).build();
            if (output.put(path, Json.bytes(tag)) != null) {
                throw new IllegalArgumentException("Conflicting datapack tag file: " + path);
            }
        });
        JsonObject pack = Json.object().put("pack", Json.object()
            .put("description", description)
            .put("pack_format", GeneratedDatapack.PACK_FORMAT_MAJOR)
            .put("min_format", Json.array(GeneratedDatapack.PACK_FORMAT_MAJOR, GeneratedDatapack.PACK_FORMAT_MINOR))
            .put("max_format", Json.array(GeneratedDatapack.PACK_FORMAT_MAJOR, GeneratedDatapack.PACK_FORMAT_MINOR))
            .build()).build();
        output.put("pack.mcmeta", Json.bytes(pack));
        return new GeneratedDatapack(output);
    }

    private static String normalize(String path) {
        String value = Objects.requireNonNull(path, "path").replace('\\', '/');
        if (value.isBlank() || value.startsWith("/") || value.contains("..") || value.contains("//")) {
            throw new IllegalArgumentException("Unsafe datapack path: " + path);
        }
        return value;
    }
}
