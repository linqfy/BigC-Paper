package dev.linqfy.bigCasares.modules.sift.feature;

import com.google.gson.JsonObject;
import dev.linqfy.bigCasares.dimension.nbt.Nbt;
import dev.linqfy.bigCasares.dimension.nbt.Nbt.CompoundTag;
import dev.linqfy.bigCasares.dimension.nbt.Nbt.IntTag;
import dev.linqfy.bigCasares.dimension.nbt.Nbt.ListTag;
import dev.linqfy.bigCasares.dimension.nbt.Nbt.StringTag;
import dev.linqfy.bigCasares.dimension.nbt.Nbt.Tag;
import dev.linqfy.bigCasares.modules.sift.SiftData;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads the mod's structure templates (NBT) and places them with vanilla's
 * rotation/mirror semantics. Block states stay in mod terms (e.g.
 * {@code the_sift:overgrown_willow_log[axis=x]}) until a feature remaps them.
 */
public final class SiftTemplates {

    public enum Rotation {
        NONE, CLOCKWISE_90, CLOCKWISE_180, COUNTERCLOCKWISE_90;

        public static Rotation random(java.util.Random random) {
            return values()[random.nextInt(4)];
        }
    }

    public enum Mirror {
        NONE, LEFT_RIGHT, FRONT_BACK
    }

    /** One template block in mod terms; {@code properties} are sorted. */
    public record TemplateBlock(int x, int y, int z, String block, Map<String, String> properties) {
        public String property(String key) {
            return properties.get(key);
        }
    }

    public record Template(int sizeX, int sizeY, int sizeZ, List<TemplateBlock> blocks) {
    }

    private final SiftData data;
    private final Map<String, Template> cache = new ConcurrentHashMap<>();
    private final Map<String, Map<Integer, String>> lootCache = new ConcurrentHashMap<>();

    public SiftTemplates(SiftData data) {
        this.data = data;
    }

    /** Template by mod path, e.g. {@code overgrown_willow/big_01}; cached, thread-safe. */
    public Optional<Template> template(String path) {
        Template cached = cache.get(path);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<byte[]> bytes = data.bytes("structure/" + path + ".nbt");
        if (bytes.isEmpty()) {
            return Optional.empty();
        }
        try {
            Template parsed = parse(Nbt.readGzip(bytes.get()));
            cache.putIfAbsent(path, parsed);
            return Optional.of(cache.get(path));
        } catch (IOException exception) {
            throw new UncheckedIOException("Corrupt Sift template " + path, exception);
        }
    }

    /**
     * Loot tables named by the block entities of a template (the {@code LootTable} tag of
     * chests and barrels), keyed by the index of the block in {@link Template#blocks()}.
     * {@link #transform} keeps block order, so the same index addresses the placed block
     * in the transformed list. Iteration is in ascending index order, so loot generation stays deterministic.
     * Templates without loot (or unknown ones) give an empty map.
     */
    public Map<Integer, String> lootTables(String path) {
        Map<Integer, String> cached = lootCache.get(path);
        if (cached != null) {
            return cached;
        }
        Optional<byte[]> bytes = data.bytes("structure/" + path + ".nbt");
        if (bytes.isEmpty()) {
            return Map.of();
        }
        try {
            lootCache.putIfAbsent(path, parseLootTables(Nbt.readGzip(bytes.get())));
            return lootCache.get(path);
        } catch (IOException exception) {
            throw new UncheckedIOException("Corrupt Sift template " + path, exception);
        }
    }

    /** JSON of a loot table by id ({@code the_sift:chests/abandoned_portal}); only the mod's own namespace is bundled. */
    public Optional<JsonObject> lootTableJson(String id) {
        String prefix = "the_sift:";
        if (!id.startsWith(prefix)) {
            return Optional.empty();
        }
        return data.optionalJson("loot_table/" + id.substring(prefix.length()) + ".json");
    }

    static Map<Integer, String> parseLootTables(CompoundTag root) {
        Map<Integer, String> loot = new TreeMap<>();
        List<Tag> entries = root.getList("blocks").values();
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag nbt = ((CompoundTag) entries.get(index)).getCompound("nbt");
            String table = nbt == null ? null : nbt.getString("LootTable");
            if (table != null) {
                loot.put(index, table);
            }
        }
        return Collections.unmodifiableMap(loot);
    }

    static Template parse(CompoundTag root) {
        int[] size = ints(root.getList("size"));
        ListTag paletteTag = root.getList("palette");
        if (paletteTag == null && root.getList("palettes") != null) {
            paletteTag = (ListTag) root.getList("palettes").values().getFirst();
        }
        List<String> names = new ArrayList<>();
        List<Map<String, String>> properties = new ArrayList<>();
        for (Tag entry : paletteTag.values()) {
            CompoundTag state = (CompoundTag) entry;
            names.add(state.getString("Name"));
            TreeMap<String, String> props = new TreeMap<>();
            CompoundTag propertyTag = state.getCompound("Properties");
            if (propertyTag != null) {
                propertyTag.values().forEach((key, value) -> props.put(key, ((StringTag) value).value()));
            }
            properties.add(Map.copyOf(props));
        }
        List<TemplateBlock> blocks = new ArrayList<>();
        for (Tag entry : root.getList("blocks").values()) {
            CompoundTag block = (CompoundTag) entry;
            int[] pos = ints(block.getList("pos"));
            int state = ((IntTag) block.get("state")).value();
            blocks.add(new TemplateBlock(pos[0], pos[1], pos[2], names.get(state), properties.get(state)));
        }
        return new Template(size[0], size[1], size[2], List.copyOf(blocks));
    }

    private static int[] ints(ListTag list) {
        int[] values = new int[list.values().size()];
        for (int index = 0; index < values.length; index++) {
            values[index] = ((IntTag) list.values().get(index)).value();
        }
        return values;
    }

    /**
     * Places a template like {@code StructureTemplate#filterBlocks}: mirror, then
     * rotate around {@code pivot} (template-local), then translate to {@code origin}.
     * Returns world positions with rotated/mirrored block properties.
     */
    public static List<TemplateBlock> transform(
        Template template, int originX, int originY, int originZ, int pivotX, int pivotZ, Rotation rotation, Mirror mirror
    ) {
        List<TemplateBlock> out = new ArrayList<>(template.blocks().size());
        for (TemplateBlock block : template.blocks()) {
            int x = block.x();
            int z = block.z();
            switch (mirror) {
                case LEFT_RIGHT -> z = -z;
                case FRONT_BACK -> x = -x;
                default -> { }
            }
            int rx;
            int rz;
            switch (rotation) {
                case CLOCKWISE_90 -> {
                    rx = pivotX + pivotZ - z;
                    rz = pivotZ - pivotX + x;
                }
                case CLOCKWISE_180 -> {
                    rx = pivotX + pivotX - x;
                    rz = pivotZ + pivotZ - z;
                }
                case COUNTERCLOCKWISE_90 -> {
                    rx = pivotX - pivotZ + z;
                    rz = pivotX + pivotZ - x;
                }
                default -> {
                    rx = x;
                    rz = z;
                }
            }
            out.add(new TemplateBlock(originX + rx, originY + block.y(), originZ + rz, block.block(),
                transformProperties(block.properties(), rotation, mirror)));
        }
        return out;
    }

    /** Applies mirror then rotation to {@code axis} and horizontal face booleans. */
    static Map<String, String> transformProperties(Map<String, String> properties, Rotation rotation, Mirror mirror) {
        if (properties.isEmpty()) {
            return properties;
        }
        TreeMap<String, String> result = new TreeMap<>(properties);
        if (mirror == Mirror.LEFT_RIGHT) {
            swap(result, "north", "south");
            swap(result, "north_bottom", "south_bottom");
        } else if (mirror == Mirror.FRONT_BACK) {
            swap(result, "east", "west");
            swap(result, "east_bottom", "west_bottom");
        }
        if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
            String axis = result.get("axis");
            if ("x".equals(axis)) {
                result.put("axis", "z");
            } else if ("z".equals(axis)) {
                result.put("axis", "x");
            }
        }
        int turns = switch (rotation) {
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
            default -> 0;
        };
        for (String suffix : List.of("", "_bottom")) {
            String[] faces = {"north" + suffix, "east" + suffix, "south" + suffix, "west" + suffix};
            if (!result.containsKey(faces[0])) {
                continue;
            }
            String[] values = new String[4];
            for (int index = 0; index < 4; index++) {
                values[(index + turns) % 4] = result.get(faces[index]);
            }
            for (int index = 0; index < 4; index++) {
                result.put(faces[index], values[index]);
            }
        }
        return Map.copyOf(result);
    }

    private static void swap(Map<String, String> map, String a, String b) {
        if (map.containsKey(a) && map.containsKey(b)) {
            String first = map.get(a);
            map.put(a, map.get(b));
            map.put(b, first);
        }
    }
}
