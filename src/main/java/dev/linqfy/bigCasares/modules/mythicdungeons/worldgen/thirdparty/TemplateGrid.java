package dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.thirdparty;

import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.dimension.nbt.Nbt.CompoundTag;
import dev.linqfy.bigCasares.dimension.nbt.Nbt.IntTag;
import dev.linqfy.bigCasares.dimension.nbt.Nbt.ListTag;
import dev.linqfy.bigCasares.dimension.nbt.Nbt.StringTag;
import dev.linqfy.bigCasares.dimension.nbt.Nbt.Tag;
import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;
import dev.linqfy.bigCasares.dimension.worldgen.StructureTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Mutable voxel view of a structure template. Cells that are not set stay structure void
 * (the world block is kept). Iteration order is y, z, x so every pass is deterministic.
 */
final class TemplateGrid {

    static final BlockStateSpec AIR = BlockStateSpec.of("minecraft:air");

    record Cell(int x, int y, int z, BlockStateSpec state, CompoundTag nbt) {
        String name() {
            return state.block().asString();
        }
    }

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final TreeMap<Long, Cell> cells = new TreeMap<>();

    TemplateGrid(int sizeX, int sizeY, int sizeZ) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
    }

    /** Reads the first palette of {@code template}; {@code structure_void} cells stay unset. */
    static TemplateGrid read(StructureTemplate template) {
        CompoundTag root = template.root();
        int[] size = ints(root.getList("size"));
        TemplateGrid grid = new TemplateGrid(size[0], size[1], size[2]);
        ListTag paletteTag = root.getList("palette") != null ? root.getList("palette")
            : (ListTag) root.getList("palettes").values().getFirst();
        List<BlockStateSpec> palette = new ArrayList<>();
        for (Tag entry : paletteTag.values()) {
            CompoundTag state = (CompoundTag) entry;
            TreeMap<String, String> properties = new TreeMap<>();
            CompoundTag props = state.getCompound("Properties");
            if (props != null) {
                for (Map.Entry<String, Tag> property : props.values().entrySet()) {
                    properties.put(property.getKey(), ((StringTag) property.getValue()).value());
                }
            }
            palette.add(BlockStateSpec.of(state.getString("Name"), properties));
        }
        for (Tag tag : root.getList("blocks").values()) {
            CompoundTag block = (CompoundTag) tag;
            int[] pos = ints(block.getList("pos"));
            BlockStateSpec state = palette.get(((IntTag) block.get("state")).value());
            if (!state.block().asString().equals("minecraft:structure_void")) {
                grid.set(pos[0], pos[1], pos[2], state, block.getCompound("nbt"));
            }
        }
        return grid;
    }

    int sizeX() {
        return sizeX;
    }

    int sizeY() {
        return sizeY;
    }

    int sizeZ() {
        return sizeZ;
    }

    boolean inside(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < sizeX && y < sizeY && z < sizeZ;
    }

    private static long key(int x, int y, int z) {
        return ((long) y * 4096L + z) * 4096L + x;
    }

    Cell get(int x, int y, int z) {
        return cells.get(key(x, y, z));
    }

    String name(int x, int y, int z) {
        Cell cell = get(x, y, z);
        return cell == null ? null : cell.name();
    }

    void set(int x, int y, int z, BlockStateSpec state) {
        set(x, y, z, state, null);
    }

    void set(int x, int y, int z, BlockStateSpec state, CompoundTag nbt) {
        if (!inside(x, y, z)) {
            throw new IllegalArgumentException("Outside template: " + x + "," + y + "," + z);
        }
        cells.put(key(x, y, z), new Cell(x, y, z, state, nbt));
    }

    void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockStateSpec state) {
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                    set(x, y, z, state);
                }
            }
        }
    }

    /** Leaves the cell as structure void. */
    void clear(int x, int y, int z) {
        cells.remove(key(x, y, z));
    }

    /** Snapshot of the cells in y, z, x order; the action may modify the grid. */
    void forEach(Consumer<Cell> action) {
        new ArrayList<>(cells.values()).forEach(action);
    }

    List<Cell> cells() {
        return List.copyOf(cells.values());
    }

    StructureTemplate build(NamespacedId id) {
        StructureTemplate.Builder builder = StructureTemplate.builder(id, sizeX, sizeY, sizeZ);
        for (Cell cell : cells.values()) {
            builder.set(cell.x(), cell.y(), cell.z(), cell.state(), cell.nbt());
        }
        return builder.build();
    }

    private static int[] ints(ListTag list) {
        int[] values = new int[list.values().size()];
        for (int index = 0; index < values.length; index++) {
            values[index] = ((IntTag) list.values().get(index)).value();
        }
        return values;
    }
}
