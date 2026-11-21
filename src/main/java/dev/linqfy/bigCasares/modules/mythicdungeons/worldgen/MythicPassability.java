package dev.linqfy.bigCasares.modules.mythicdungeons.worldgen;

import dev.linqfy.bigCasares.dimension.nbt.Nbt.CompoundTag;
import dev.linqfy.bigCasares.dimension.nbt.Nbt.IntTag;
import dev.linqfy.bigCasares.dimension.nbt.Nbt.ListTag;
import dev.linqfy.bigCasares.dimension.nbt.Nbt.Tag;
import dev.linqfy.bigCasares.dimension.worldgen.StructureTemplate;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Walkability and clearance rules for dungeon pieces, on a coarse voxel model.
 *
 * <ul>
 *     <li>Player: stands in a cell when the cell and the one above are passable and the cell below
 *     supports it (or the cell holds a ladder); on stairs and bottom slabs a third free cell is required.
 *     Steps go to a horizontal neighbour at most one block up or down; going up needs a free cell above
 *     the head at the starting cell (jumping or climbing the step), going down the same at the target.
 *     Ladders can be climbed.</li>
 *     <li>Large mob: a {@link #MOB_WIDTH}x{@link #MOB_WIDTH} footprint, {@link #MOB_HEIGHT} tall box resting
 *     on the highest support under it, moving one block at a time and at most one block up or down:
 *     passages it can travel are at least 5 wide and 5 high.</li>
 *     <li>Spawner: a fully free {@link #SPAWN_BOX}x{@link #SPAWN_BOX}x{@link #SPAWN_HEIGHT} volume on solid
 *     floor at the spawner's level within its spawn range, and an open floor square of at least
 *     {@link #SPAWN_ROOM} with {@link #SPAWN_HEADROOM} headroom at that level.</li>
 * </ul>
 * Unset template cells (structure void) count as solid: the world block is unknown.
 */
public final class MythicPassability {

    public static final int MOB_WIDTH = 5;
    public static final int MOB_HEIGHT = 5;
    public static final int SPAWN_BOX = 5;
    public static final int SPAWN_HEIGHT = 4;
    public static final int SPAWN_RANGE = 4;
    public static final int SPAWN_ROOM = 11;
    public static final int SPAWN_HEADROOM = 6;
    public static final int DOOR_WIDTH = 3;
    public static final int DOOR_HEIGHT = 4;

    static final byte SOLID = 0;
    static final byte OPEN = 1;
    static final byte WATER = 2;
    static final byte LADDER = 3;
    /** Solid support whose top is raised (stairs, bottom slabs): needs one more free cell above. */
    static final byte STEP = 4;

    private MythicPassability() {
    }

    /** Coarse class of a block id (with its {@code type} property for slabs). */
    static byte classify(String name, String slabType) {
        if (name == null) {
            return SOLID;
        }
        String id = name.startsWith("minecraft:") ? name.substring("minecraft:".length()) : name;
        if (id.equals("air") || id.equals("cave_air") || id.equals("void_air") || id.endsWith("_carpet")
            || id.equals("light") || id.equals("short_grass") || id.equals("glow_lichen") || id.equals("lily_pad")
            || id.equals("poppy") || id.equals("dandelion") || id.equals("azure_bluet") || id.equals("cornflower")
            || id.equals("torch") || id.equals("wall_torch") || id.endsWith("_pressure_plate") || id.equals("rail")) {
            return OPEN;
        }
        if (id.equals("water") || id.equals("bubble_column")) {
            return WATER;
        }
        if (id.equals("ladder") || id.equals("vine")) {
            return LADDER;
        }
        if (id.endsWith("_stairs") || (id.endsWith("_slab") && "bottom".equals(slabType))) {
            return STEP;
        }
        return SOLID;
    }

    /** Dense voxel classes of a template; jigsaw blocks count as their final state. */
    public static final class Voxels {
        final int sizeX;
        final int sizeY;
        final int sizeZ;
        private final byte[] cells;
        private final String[] names;

        Voxels(int sizeX, int sizeY, int sizeZ) {
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.cells = new byte[sizeX * sizeY * sizeZ];
            this.names = new String[sizeX * sizeY * sizeZ];
        }

        public static Voxels of(StructureTemplate template) {
            CompoundTag root = template.root();
            int[] size = MythicTemplateRewriter.size(template);
            Voxels voxels = new Voxels(size[0], size[1], size[2]);
            List<Tag> palette = MythicTemplateRewriter.firstPalette(root).values();
            for (Tag tag : root.getList("blocks").values()) {
                CompoundTag block = (CompoundTag) tag;
                ListTag pos = block.getList("pos");
                CompoundTag state = (CompoundTag) palette.get(((IntTag) block.get("state")).value());
                String name = state.getString("Name");
                String slabType = null;
                if (state.getCompound("Properties") != null) {
                    slabType = state.getCompound("Properties").getString("type");
                }
                if ("minecraft:jigsaw".equals(name)) {
                    CompoundTag nbt = block.getCompound("nbt");
                    String finalState = nbt == null ? null : nbt.getString("final_state");
                    name = finalState == null || finalState.isBlank() ? "minecraft:air" : stripProperties(finalState);
                    slabType = finalState != null && finalState.contains("type=bottom") ? "bottom" : null;
                }
                if ("minecraft:structure_void".equals(name)) {
                    continue;
                }
                int x = ((IntTag) pos.values().get(0)).value();
                int y = ((IntTag) pos.values().get(1)).value();
                int z = ((IntTag) pos.values().get(2)).value();
                voxels.cells[voxels.index(x, y, z)] = classify(name, slabType);
                voxels.names[voxels.index(x, y, z)] = name;
            }
            return voxels;
        }

        private static String stripProperties(String state) {
            int bracket = state.indexOf('[');
            return bracket < 0 ? state : state.substring(0, bracket);
        }

        private int index(int x, int y, int z) {
            return (y * sizeZ + z) * sizeX + x;
        }

        boolean inside(int x, int y, int z) {
            return x >= 0 && y >= 0 && z >= 0 && x < sizeX && y < sizeY && z < sizeZ;
        }

        public byte at(int x, int y, int z) {
            return inside(x, y, z) ? cells[index(x, y, z)] : SOLID;
        }

        /** Block id at a cell, {@code null} when unset or outside. */
        public String name(int x, int y, int z) {
            return inside(x, y, z) ? names[index(x, y, z)] : null;
        }

        public int sizeX() {
            return sizeX;
        }

        public int sizeY() {
            return sizeY;
        }

        public int sizeZ() {
            return sizeZ;
        }
    }

    /** Read access to a voxel world; {@link Voxels} or an assembled dungeon. */
    public interface World {
        byte at(int x, int y, int z);
    }

    private static World world(Voxels voxels) {
        return voxels::at;
    }

    static boolean passable(byte cell) {
        return cell == OPEN || cell == WATER || cell == LADDER;
    }

    static boolean supports(byte cell) {
        return cell == SOLID || cell == STEP;
    }

    public static boolean standable(World world, int x, int y, int z) {
        if (!passable(world.at(x, y, z)) || !passable(world.at(x, y + 1, z))) {
            return false;
        }
        byte below = world.at(x, y - 1, z);
        if (world.at(x, y, z) == LADDER) {
            return true;
        }
        if (!supports(below)) {
            return false;
        }
        return below != STEP || passable(world.at(x, y + 2, z));
    }

    private static final int[][] HORIZONTAL = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** Every cell a player reaches from the standable {@code starts}; keys from {@link #key}. */
    public static Set<Long> walk(World world, Collection<int[]> starts, Box bounds) {
        Set<Long> seen = new HashSet<>();
        Deque<int[]> queue = new ArrayDeque<>();
        for (int[] start : starts) {
            if (standable(world, start[0], start[1], start[2]) && seen.add(key(start[0], start[1], start[2]))) {
                queue.add(start);
            }
        }
        while (!queue.isEmpty()) {
            int[] cell = queue.removeFirst();
            int x = cell[0];
            int y = cell[1];
            int z = cell[2];
            for (int[] d : HORIZONTAL) {
                int nx = x + d[0];
                int nz = z + d[1];
                for (int dy = -1; dy <= 1; dy++) {
                    int ny = y + dy;
                    if (!bounds.contains(nx, ny, nz) || !standable(world, nx, ny, nz)) {
                        continue;
                    }
                    if (dy == 1 && !passable(world.at(x, y + 2, z))) {
                        continue;
                    }
                    if (dy == -1 && !passable(world.at(nx, y + 1, nz))) {
                        continue;
                    }
                    if (seen.add(key(nx, ny, nz))) {
                        queue.add(new int[]{nx, ny, nz});
                    }
                }
            }
            if (world.at(x, y, z) == LADDER && passable(world.at(x, y + 2, z)) && bounds.contains(x, y + 1, z)
                && standable(world, x, y + 1, z) && seen.add(key(x, y + 1, z))) {
                queue.add(new int[]{x, y + 1, z});
            }
            if (world.at(x, y - 1, z) == LADDER && bounds.contains(x, y - 1, z) && standable(world, x, y - 1, z)
                && seen.add(key(x, y - 1, z))) {
                queue.add(new int[]{x, y - 1, z});
            }
        }
        return seen;
    }

    public static Set<Long> walk(Voxels voxels, Collection<int[]> starts) {
        return walk(world(voxels), starts, new Box(0, 0, 0, voxels.sizeX - 1, voxels.sizeY + 2, voxels.sizeZ - 1));
    }

    /** Inclusive bounds of a flood. */
    public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        boolean contains(int x, int y, int z) {
            return x >= minX && y >= minY && z >= minZ && x <= maxX && y <= maxY && z <= maxZ;
        }
    }

    public static long key(int x, int y, int z) {
        return ((long) (y + 2048) << 42) | ((long) (z + (1 << 20)) << 21) | (x + (1 << 20));
    }

    /** True when some reachable cell is beside {@code (x, y, z)} at the same height (to open a chest, hit a spawner). */
    public static boolean besideReached(Set<Long> reached, int x, int y, int z) {
        for (int[] d : HORIZONTAL) {
            if (reached.contains(key(x + d[0], y, z + d[1]))) {
                return true;
            }
        }
        return false;
    }

    /** Large-mob box positions (min x, y, min z) reached from boxes that cover any start cell at its height. */
    public static Set<Long> mobWalk(Voxels voxels, Collection<int[]> starts) {
        Set<Long> seen = new HashSet<>();
        Deque<int[]> queue = new ArrayDeque<>();
        for (int[] start : starts) {
            for (int x0 = start[0] - MOB_WIDTH + 1; x0 <= start[0]; x0++) {
                for (int z0 = start[2] - MOB_WIDTH + 1; z0 <= start[2]; z0++) {
                    if (mobFits(voxels, x0, start[1], z0) && seen.add(key(x0, start[1], z0))) {
                        queue.add(new int[]{x0, start[1], z0});
                    }
                }
            }
        }
        while (!queue.isEmpty()) {
            int[] box = queue.removeFirst();
            for (int[] d : HORIZONTAL) {
                for (int dy = -1; dy <= 1; dy++) {
                    int nx = box[0] + d[0];
                    int ny = box[1] + dy;
                    int nz = box[2] + d[1];
                    if (mobFits(voxels, nx, ny, nz) && seen.add(key(nx, ny, nz))) {
                        queue.add(new int[]{nx, ny, nz});
                    }
                }
            }
        }
        return seen;
    }

    /** True when some reached mob box covers {@code (x, z)} at height {@code y}. */
    public static boolean mobCovers(Set<Long> boxes, int x, int y, int z) {
        for (int x0 = x - MOB_WIDTH + 1; x0 <= x; x0++) {
            for (int z0 = z - MOB_WIDTH + 1; z0 <= z; z0++) {
                if (boxes.contains(key(x0, y, z0))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Box at rest: free inside, inside the template, and resting on support under at least one column. */
    static boolean mobFits(Voxels voxels, int x0, int y, int z0) {
        if (x0 < 0 || z0 < 0 || y < 1 || x0 + MOB_WIDTH > voxels.sizeX || z0 + MOB_WIDTH > voxels.sizeZ
            || y + MOB_HEIGHT > voxels.sizeY) {
            return false;
        }
        boolean supported = false;
        for (int x = x0; x < x0 + MOB_WIDTH; x++) {
            for (int z = z0; z < z0 + MOB_WIDTH; z++) {
                for (int dy = 0; dy < MOB_HEIGHT; dy++) {
                    if (!passable(voxels.at(x, y + dy, z))) {
                        return false;
                    }
                }
                supported |= supports(voxels.at(x, y - 1, z));
            }
        }
        return supported;
    }

    /**
     * True when a free {@link #SPAWN_BOX}-square, {@link #SPAWN_HEIGHT}-tall air volume on solid floor
     * at the spawner's level lies right beside it (within one block beyond its spawn range, so the volume
     * can sit next to the spawner instead of around it).
     */
    public static boolean spawnerHasRoom(Voxels voxels, int sx, int sy, int sz) {
        int reach = SPAWN_RANGE + 1;
        for (int x0 = sx - reach; x0 + SPAWN_BOX - 1 <= sx + reach; x0++) {
            for (int z0 = sz - reach; z0 + SPAWN_BOX - 1 <= sz + reach; z0++) {
                if (freeVolume(voxels, x0, sy, z0)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean freeVolume(Voxels voxels, int x0, int y, int z0) {
        for (int x = x0; x < x0 + SPAWN_BOX; x++) {
            for (int z = z0; z < z0 + SPAWN_BOX; z++) {
                if (!voxels.inside(x, y, z) || voxels.at(x, y - 1, z) != SOLID) {
                    return false;
                }
                for (int dy = 0; dy < SPAWN_HEIGHT; dy++) {
                    if (voxels.at(x, y + dy, z) != OPEN) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Side of the largest square of open floor at level {@code y} with {@code headroom} free cells. */
    public static int openSquare(Voxels voxels, int y, int headroom) {
        int[][] best = new int[voxels.sizeX][voxels.sizeZ];
        int result = 0;
        for (int x = 0; x < voxels.sizeX; x++) {
            for (int z = 0; z < voxels.sizeZ; z++) {
                boolean open = supports(voxels.at(x, y - 1, z));
                for (int dy = 0; open && dy < headroom; dy++) {
                    open = voxels.at(x, y + dy, z) == OPEN;
                }
                if (open) {
                    best[x][z] = x == 0 || z == 0 ? 1 : 1 + Math.min(best[x - 1][z], Math.min(best[x][z - 1], best[x - 1][z - 1]));
                    result = Math.max(result, best[x][z]);
                }
            }
        }
        return result;
    }

    /** A spawner passes when it has a free volume nearby and stands in a room with a large open floor. */
    public static boolean spawnerOk(Voxels voxels, int sx, int sy, int sz) {
        return spawnerHasRoom(voxels, sx, sy, sz) && openSquare(voxels, sy, SPAWN_HEADROOM) >= SPAWN_ROOM;
    }

    /** The doorway around a horizontal connector: {@link #DOOR_WIDTH} wide, {@link #DOOR_HEIGHT} tall, open. */
    public static boolean doorwayOpen(Voxels voxels, int x, int y, int z, String front) {
        boolean alongX = front.equals("north") || front.equals("south");
        for (int lateral = -1; lateral <= 1; lateral++) {
            for (int dy = 0; dy < DOOR_HEIGHT; dy++) {
                int cx = alongX ? x + lateral : x;
                int cz = alongX ? z : z + lateral;
                if (!passable(voxels.at(cx, y + dy, cz))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The cell one step inside the piece from a horizontal connector on its face. */
    public static int[] inward(int x, int y, int z, String front) {
        return switch (front) {
            case "north" -> new int[]{x, y, z + 1};
            case "south" -> new int[]{x, y, z - 1};
            case "west" -> new int[]{x + 1, y, z};
            default -> new int[]{x - 1, y, z};
        };
    }
}
