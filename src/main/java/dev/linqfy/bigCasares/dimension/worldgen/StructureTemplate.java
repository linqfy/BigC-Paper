package dev.linqfy.bigCasares.dimension.worldgen;

import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.dimension.nbt.Nbt;
import dev.linqfy.bigCasares.dimension.nbt.Nbt.CompoundTag;
import dev.linqfy.bigCasares.dimension.nbt.Nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * A structure template ({@code data/<ns>/structure/<path>.nbt}) built in code or
 * loaded from an existing file. Templates are placed by jigsaw structures and
 * features; Minecraft data-fixes older {@code DataVersion}s on load.
 */
public final class StructureTemplate {

    /** Data version of Minecraft 26.2. */
    public static final int DATA_VERSION_26_2 = 4903;

    private final NamespacedId id;
    private final CompoundTag root;

    private StructureTemplate(NamespacedId id, CompoundTag root) {
        this.id = Objects.requireNonNull(id, "id");
        this.root = Objects.requireNonNull(root, "root");
    }

    public static Builder builder(NamespacedId id, int sizeX, int sizeY, int sizeZ) {
        return new Builder(id, sizeX, sizeY, sizeZ);
    }

    /** Wraps an existing template, e.g. one read from a resource. */
    public static StructureTemplate of(NamespacedId id, CompoundTag root) {
        if (!(root.get("palette") instanceof Nbt.ListTag) && !(root.get("palettes") instanceof Nbt.ListTag)) {
            throw new IllegalArgumentException("Structure template " + id + " has no palette");
        }
        return new StructureTemplate(id, root);
    }

    public NamespacedId id() {
        return id;
    }

    public CompoundTag root() {
        return root;
    }

    public byte[] toBytes() {
        return Nbt.writeGzip(root);
    }

    /** Block ids referenced by the palette(s); used for validation and remapping. */
    public List<String> paletteBlocks() {
        List<String> blocks = new ArrayList<>();
        forEachPalette(palette -> {
            for (Tag entry : palette.values()) {
                if (entry instanceof CompoundTag state && state.getString("Name") != null) {
                    blocks.add(state.getString("Name"));
                }
            }
            return palette;
        });
        return blocks;
    }

    /**
     * Returns a copy whose palette states are rewritten by {@code mapper}. The mapper
     * receives a {@code Name}/{@code Properties} compound and returns a replacement.
     */
    public StructureTemplate remapPalette(Function<CompoundTag, CompoundTag> mapper) {
        Objects.requireNonNull(mapper, "mapper");
        CompoundTag[] holder = {root};
        forEachPalette(palette -> {
            List<Tag> remapped = new ArrayList<>(palette.values().size());
            for (Tag entry : palette.values()) {
                remapped.add(entry instanceof CompoundTag state ? mapper.apply(state) : entry);
            }
            return new Nbt.ListTag(palette.elementType(), remapped);
        }, holder);
        return new StructureTemplate(id, holder[0]);
    }

    private void forEachPalette(Function<Nbt.ListTag, Nbt.ListTag> action) {
        forEachPalette(action, new CompoundTag[]{root});
    }

    private static void forEachPalette(Function<Nbt.ListTag, Nbt.ListTag> action, CompoundTag[] holder) {
        CompoundTag current = holder[0];
        if (current.get("palette") instanceof Nbt.ListTag palette) {
            current = current.with("palette", action.apply(palette));
        }
        if (current.get("palettes") instanceof Nbt.ListTag palettes) {
            List<Tag> rewritten = new ArrayList<>();
            for (Tag entry : palettes.values()) {
                rewritten.add(entry instanceof Nbt.ListTag palette ? action.apply(palette) : entry);
            }
            current = current.with("palettes", new Nbt.ListTag(palettes.elementType(), rewritten));
        }
        holder[0] = current;
    }

    public static final class Builder {
        private final NamespacedId id;
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final Map<String, Integer> paletteIndex = new LinkedHashMap<>();
        private final List<BlockStateSpec> palette = new ArrayList<>();
        private final TreeMap<Long, PlacedBlock> blocks = new TreeMap<>();

        private record PlacedBlock(int x, int y, int z, int state, CompoundTag blockEntity) {
        }

        private Builder(NamespacedId id, int sizeX, int sizeY, int sizeZ) {
            this.id = Objects.requireNonNull(id, "id");
            if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0 || sizeX > 48 || sizeY > 48 || sizeZ > 48) {
                throw new IllegalArgumentException("Template size must be within 1..48 per axis");
            }
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
        }

        public Builder set(int x, int y, int z, BlockStateSpec state) {
            return set(x, y, z, state, null);
        }

        /** Places a block; {@code blockEntity} is e.g. a chest with a {@code LootTable} key. */
        public Builder set(int x, int y, int z, BlockStateSpec state, CompoundTag blockEntity) {
            if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) {
                throw new IllegalArgumentException("Template position out of bounds: " + x + "," + y + "," + z);
            }
            int index = paletteIndex.computeIfAbsent(state.asString(), ignored -> {
                palette.add(state);
                return palette.size() - 1;
            });
            long key = ((long) y * 4096L + z) * 4096L + x;
            blocks.put(key, new PlacedBlock(x, y, z, index, blockEntity));
            return this;
        }

        public Builder fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockStateSpec state) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                    for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                        set(x, y, z, state);
                    }
                }
            }
            return this;
        }

        public boolean isSet(int x, int y, int z) {
            return blocks.containsKey(((long) y * 4096L + z) * 4096L + x);
        }

        public StructureTemplate build() {
            List<Tag> paletteTags = new ArrayList<>();
            for (BlockStateSpec state : palette) {
                Nbt.CompoundBuilder entry = Nbt.compound().putString("Name", state.block().asString());
                if (!state.properties().isEmpty()) {
                    Nbt.CompoundBuilder props = Nbt.compound();
                    state.properties().forEach(props::putString);
                    entry.put("Properties", props.build());
                }
                paletteTags.add(entry.build());
            }
            List<Tag> blockTags = new ArrayList<>();
            for (PlacedBlock block : blocks.values()) {
                Nbt.CompoundBuilder entry = Nbt.compound()
                    .put("pos", Nbt.intList(block.x(), block.y(), block.z()))
                    .putInt("state", block.state());
                if (block.blockEntity() != null) {
                    entry.put("nbt", block.blockEntity());
                }
                blockTags.add(entry.build());
            }
            CompoundTag root = Nbt.compound()
                .putInt("DataVersion", DATA_VERSION_26_2)
                .put("size", Nbt.intList(sizeX, sizeY, sizeZ))
                .put("palette", new Nbt.ListTag((byte) 10, paletteTags))
                .put("blocks", new Nbt.ListTag((byte) 10, blockTags))
                .put("entities", new Nbt.ListTag((byte) 0, List.of()))
                .build();
            return new StructureTemplate(id, root);
        }
    }
}
