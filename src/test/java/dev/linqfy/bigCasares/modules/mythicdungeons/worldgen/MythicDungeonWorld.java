package dev.linqfy.bigCasares.modules.mythicdungeons.worldgen;

import dev.linqfy.bigCasares.dimension.worldgen.StructureTemplate;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicJigsawSimulator.Placed;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicPassability.Voxels;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An assembled dungeon as one voxel world: every cell is the class of the piece that covers it; cells
 * outside every piece count as solid (unknown terrain). Used to walk from the surface to the altar.
 */
final class MythicDungeonWorld implements MythicPassability.World {

    private final List<Placed> pieces;
    private final Map<StructureTemplate, Voxels> voxels;

    MythicDungeonWorld(List<Placed> pieces, Map<StructureTemplate, Voxels> cache) {
        this.pieces = pieces;
        this.voxels = cache;
        for (Placed piece : pieces) {
            cache.computeIfAbsent(piece.template(), Voxels::of);
        }
    }

    static Map<StructureTemplate, Voxels> cache() {
        return new IdentityHashMap<>();
    }

    @Override
    public byte at(int x, int y, int z) {
        for (Placed piece : pieces) {
            if (piece.box().contains(x, y, z)) {
                int[] local = piece.local(x, y, z);
                return voxels.get(piece.template()).at(local[0], local[1], local[2]);
            }
        }
        return MythicPassability.SOLID;
    }

    /** Standable cells on the outer edge of the start piece: where a player walks in from outside. */
    List<int[]> entranceCells() {
        Placed start = pieces.getFirst();
        MythicJigsawSimulator.Box box = start.box();
        List<int[]> cells = new ArrayList<>();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                boolean edge = x == box.minX() || x == box.maxX() || z == box.minZ() || z == box.maxZ();
                if (!edge) {
                    continue;
                }
                for (int y = box.minY() + 1; y <= box.maxY(); y++) {
                    if (MythicPassability.standable(this, x, y, z)) {
                        cells.add(new int[]{x, y, z});
                    }
                }
            }
        }
        return cells;
    }

    Set<Long> walkFromEntrance(MythicJigsawSimulator.Box limit) {
        MythicPassability.Box bounds = new MythicPassability.Box(limit.minX(), limit.minY(), limit.minZ(),
            limit.maxX(), limit.maxY() + 3, limit.maxZ());
        return MythicPassability.walk(this, entranceCells(), bounds);
    }

    /** World cell of the arena's lodestone. */
    int[] altar() {
        for (Placed piece : pieces) {
            if (piece.path().endsWith("/pieces/arena")) {
                return piece.world(MythicPieces.ALTAR_X, 1, MythicPieces.ALTAR_Z);
            }
        }
        throw new IllegalStateException("no arena");
    }
}
