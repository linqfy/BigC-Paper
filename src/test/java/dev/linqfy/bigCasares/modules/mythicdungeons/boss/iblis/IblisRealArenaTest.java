package dev.linqfy.bigCasares.modules.mythicdungeons.boss.iblis;

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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The room and the vessel spots against the real generated Arabian arena (decoded from the built datapack), measured
 * from the altar exactly as the fight does it.
 */
class IblisRealArenaTest {

    /** Local altar position of the arena piece (see the worldgen arena builder). */
    private static final int ALTAR_X = 17;
    private static final int ALTAR_Y = 1;
    private static final int ALTAR_Z = 20;

    private static Grid arena;

    private record Grid(int sizeX, int sizeY, int sizeZ, String[][][] names) implements IblisRoom.Passable {

        String name(int x, int y, int z) {
            if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) {
                return null;
            }
            return names[x][y][z];
        }

        @Override
        public boolean passable(int x, int y, int z) {
            String name = name(x, y, z);
            return name == null || name.equals("minecraft:air") || name.equals("minecraft:cave_air")
                || name.equals("minecraft:structure_void");
        }
    }

    @BeforeAll
    static void decode() throws Exception {
        GeneratedDatapack pack = MythicDungeonDatapack.build();
        String path = "data/" + MythicDungeonTheme.NAMESPACE + "/structure/arabian/pieces/arena.nbt";
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
    void theRoomIsTheWholeInteriorAndNothingBehindTheWalls() {
        assertEquals("minecraft:lodestone", arena.name(ALTAR_X, ALTAR_Y, ALTAR_Z));
        int floor = IblisRoom.floorUnder(ALTAR_X, ALTAR_Y + 1, ALTAR_Z, arena);
        assertEquals(1, floor);
        IblisRoom room = IblisRoom.probe(ALTAR_X, ALTAR_Z, floor, arena);
        assertEquals(1, room.minX());
        assertEquals(arena.sizeX() - 2, room.maxX());
        assertEquals(1, room.minZ());
        assertEquals(arena.sizeZ() - 2, room.maxZ());
        assertTrue(!room.contains(ALTAR_X + 0.5, floor, arena.sizeZ() + 2.0), "outside the south wall");
    }

    @Test
    void thereAreAtLeastFourFreeVesselSpotsOnTheFloor() {
        int floor = IblisRoom.floorUnder(ALTAR_X, ALTAR_Y + 1, ALTAR_Z, arena);
        IblisRoom room = IblisRoom.probe(ALTAR_X, ALTAR_Z, floor, arena);
        List<IblisPoint> spots = new ArrayList<>();
        for (IblisPoint candidate : room.vesselCandidates()) {
            int x = (int) Math.floor(candidate.x());
            int z = (int) Math.floor(candidate.z());
            boolean free = arena.passable(x, floor, z) && arena.passable(x, floor + 1, z)
                && !arena.passable(x, floor - 1, z);
            if (spots.size() < 4 && free && spots.stream().allMatch(other -> other.distance(candidate) >= 5.0)) {
                spots.add(candidate);
            }
        }
        assertEquals(4, spots.size(), "vessel spots " + spots);
        // spread around the room, not bunched on one side
        double sumX = 0;
        double sumZ = 0;
        for (IblisPoint spot : spots) {
            sumX += spot.x() - room.centerX();
            sumZ += spot.z() - room.centerZ();
        }
        assertTrue(Math.hypot(sumX / 4, sumZ / 4) < 3.0, "vessels balanced around the centre: " + spots);
    }
}
