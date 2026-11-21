package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enlil;

import dev.linqfy.bigCasares.dimension.datapack.GeneratedDatapack;
import dev.linqfy.bigCasares.dimension.nbt.Nbt;
import dev.linqfy.bigCasares.dimension.nbt.Nbt.CompoundTag;
import dev.linqfy.bigCasares.dimension.nbt.Nbt.IntTag;
import dev.linqfy.bigCasares.dimension.nbt.Nbt.ListTag;
import dev.linqfy.bigCasares.dimension.nbt.Nbt.Tag;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicDungeonDatapack;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicDungeonTheme;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The flood against the real generated Mesopotamian arena (decoded from the built datapack): the room is measured
 * from the altar exactly as the encounter does it, and both water layers must be planned, sealed and kept inside.
 */
class EnlilRealArenaTest {

    /** Local altar position of the arena piece (see the worldgen arena builder). */
    private static final int ALTAR_X = 17;
    private static final int ALTAR_Y = 1;
    private static final int ALTAR_Z = 20;

    private static Grid arena;

    /** The arena template as block names, with a ring of open "outside world" around it. */
    private record Grid(int sizeX, int sizeY, int sizeZ, String[][][] names) implements EnlilArena.Passable {

        String name(int x, int y, int z) {
            if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) {
                return null; // outside the piece: treated as open, so any leak escapes
            }
            return names[x][y][z];
        }

        /** Air, or a position the template does not set (structure void), lets water and bodies through. */
        @Override
        public boolean passable(int x, int y, int z) {
            String name = name(x, y, z);
            return name == null || name.equals("minecraft:air") || name.equals("minecraft:cave_air")
                || name.equals("minecraft:structure_void");
        }

        boolean air(int x, int y, int z) {
            return passable(x, y, z);
        }
    }

    @BeforeAll
    static void decode() throws Exception {
        GeneratedDatapack pack = MythicDungeonDatapack.build();
        String path = "data/" + MythicDungeonTheme.NAMESPACE + "/structure/mesopotamian/pieces/arena.nbt";
        CompoundTag root = Nbt.readGzip(pack.file(path));
        List<Tag> size = root.getList("size").values();
        int sx = ((IntTag) size.get(0)).value();
        int sy = ((IntTag) size.get(1)).value();
        int sz = ((IntTag) size.get(2)).value();
        ListTag palette = root.getList("palette") != null ? root.getList("palette")
            : (ListTag) root.getList("palettes").values().getFirst();
        String[][][] names = new String[sx][sy][sz];
        for (Tag tag : root.getList("blocks").values()) {
            CompoundTag block = (CompoundTag) tag;
            List<Tag> pos = block.getList("pos").values();
            int state = ((IntTag) block.get("state")).value();
            names[((IntTag) pos.get(0)).value()][((IntTag) pos.get(1)).value()][((IntTag) pos.get(2)).value()] =
                ((CompoundTag) palette.values().get(state)).getString("Name");
        }
        arena = new Grid(sx, sy, sz, names);
    }

    @Test
    void theAltarIsWhereTheTestExpectsIt() {
        assertEquals("minecraft:lodestone", arena.name(ALTAR_X, ALTAR_Y, ALTAR_Z));
    }

    @Test
    void measuringFromTheAltarFindsTheWholeInterior() {
        int floor = EnlilArena.floorUnder(ALTAR_X, ALTAR_Y + 1, ALTAR_Z, arena);
        assertEquals(1, floor, "walking level is the first layer above the floor");
        EnlilArena measured = EnlilArena.probe(ALTAR_X, ALTAR_Z, floor, arena);
        assertEquals(1, measured.minX());
        assertEquals(arena.sizeX() - 2, measured.maxX());
        assertEquals(1, measured.minZ(), "the entrance stairs must not cut the room short");
        assertEquals(arena.sizeZ() - 2, measured.maxZ());
    }

    @Test
    void bothFloodLayersAreSealedAndStayInsideTheRoom() {
        int floor = EnlilArena.floorUnder(ALTAR_X, ALTAR_Y + 1, ALTAR_Z, arena);
        EnlilArena measured = EnlilArena.probe(ALTAR_X, ALTAR_Z, floor, arena);
        Set<EnlilFloodPlanner.Cell> first = null;
        for (int layer = 0; layer < EnlilTuning.FLOOD_LAYERS; layer++) {
            int y = floor + layer;
            Optional<List<EnlilFloodPlanner.Cell>> plan = EnlilFloodPlanner.plan(measured, ALTAR_X, ALTAR_Z,
                (x, z) -> arena.air(x, y, z));
            assertTrue(plan.isPresent(), "layer " + layer + " leaks");
            List<EnlilFloodPlanner.Cell> cells = plan.get();
            assertTrue(cells.size() > 900, "layer " + layer + " has " + cells.size() + " cells");
            for (EnlilFloodPlanner.Cell cell : cells) {
                assertTrue(cell.x() >= 1 && cell.x() <= arena.sizeX() - 2 && cell.z() >= 1
                    && cell.z() <= arena.sizeZ() - 2, "water outside the interior at " + cell);
                assertTrue(arena.air(cell.x(), y, cell.z()));
            }
            // the cells beside the entrance stairs (near the north wall) are flooded too
            assertTrue(cells.contains(new EnlilFloodPlanner.Cell(ALTAR_X - 3, 1)), "layer " + layer);
            if (first == null) {
                first = new HashSet<>(cells);
            }
        }
        // the raised doorway sits above the water: on both water layers the north wall (the door side) is closed
        for (int layer = 0; layer < EnlilTuning.FLOOD_LAYERS; layer++) {
            for (int x = 0; x < arena.sizeX(); x++) {
                assertTrue(!arena.air(x, floor + layer, 0), "hole in the door wall at x " + x + " layer " + layer);
            }
        }
        assertTrue(first.size() > 900);
    }
}
