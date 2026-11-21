package dev.linqfy.bigCasares.modules.mythicdungeons.worldgen;

import dev.linqfy.bigCasares.dimension.worldgen.StructureTemplate;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicPassability.Voxels;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicTemplateRewriter.Jigsaw;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The per-template playtest rules, for built-in and third-party pieces alike (public so the third-party
 * tests can use it):
 * <ul>
 *     <li>every connector doorway is at least 3 wide and 4 tall and open;</li>
 *     <li>a player can walk between all connectors (and, for a start piece, from its outer edge to its
 *     descent) and reach every chest, spawner and the altar;</li>
 *     <li>a 5x5x5 mob can travel between all connectors of a piece with two or more;</li>
 *     <li>every spawner has a free 5x5x4 volume beside it and stands in a room with an 11x11 open floor
 *     and 6 free blocks above it.</li>
 * </ul>
 */
public final class MythicTemplateRules {

    private MythicTemplateRules() {
    }

    /** Connector jigsaws: horizontal ones that carry a name or a pool (not neutralised gates). */
    static List<Jigsaw> connectors(StructureTemplate template) {
        List<Jigsaw> result = new ArrayList<>();
        for (Jigsaw jigsaw : MythicTemplateRewriter.jigsaws(template)) {
            boolean active = !jigsaw.name().equals("minecraft:empty") || !jigsaw.pool().equals("minecraft:empty");
            if (jigsaw.horizontal() && active) {
                result.add(jigsaw);
            }
        }
        return result;
    }

    public static List<String> violations(StructureTemplate template) {
        List<String> problems = new ArrayList<>();
        String id = template.id().asString();
        if (id.endsWith("/pieces/seal")) {
            return problems;
        }
        Voxels voxels = Voxels.of(template);
        List<Jigsaw> connectors = connectors(template);
        List<int[]> starts = new ArrayList<>();
        for (Jigsaw jigsaw : connectors) {
            if (!MythicPassability.doorwayOpen(voxels, jigsaw.x(), jigsaw.y(), jigsaw.z(), jigsaw.front())) {
                problems.add(id + ": doorway at " + jigsaw.x() + "," + jigsaw.y() + "," + jigsaw.z()
                    + " is not 3 wide and 4 tall");
            }
        }
        Jigsaw descent = MythicTemplateRewriter.jigsaws(template).stream()
            .filter(jigsaw -> jigsaw.front().equals("down") && !jigsaw.pool().equals("minecraft:empty"))
            .findFirst().orElse(null);
        if (!connectors.isEmpty()) {
            Jigsaw first = connectors.getFirst();
            starts.add(new int[]{first.x(), first.y(), first.z()});
        } else {
            for (int x = 0; x < voxels.sizeX(); x++) {
                for (int z = 0; z < voxels.sizeZ(); z++) {
                    if (x == 0 || z == 0 || x == voxels.sizeX() - 1 || z == voxels.sizeZ() - 1) {
                        for (int y = 1; y < voxels.sizeY(); y++) {
                            starts.add(new int[]{x, y, z});
                        }
                    }
                }
            }
        }
        Set<Long> reached = MythicPassability.walk(voxels, starts);
        for (Jigsaw jigsaw : connectors) {
            if (!reached.contains(MythicPassability.key(jigsaw.x(), jigsaw.y(), jigsaw.z()))) {
                problems.add(id + ": connector at " + jigsaw.x() + "," + jigsaw.y() + "," + jigsaw.z()
                    + " cannot be reached on foot from " + connectors.getFirst().x() + "," + connectors.getFirst().y()
                    + "," + connectors.getFirst().z());
            }
        }
        if (descent != null && !descentReached(reached, descent)) {
            problems.add(id + ": the descent cannot be reached on foot from the edge of the piece");
        }
        for (int x = 0; x < voxels.sizeX(); x++) {
            for (int y = 0; y < voxels.sizeY(); y++) {
                for (int z = 0; z < voxels.sizeZ(); z++) {
                    String name = voxels.name(x, y, z);
                    if (name == null) {
                        continue;
                    }
                    boolean target = name.equals("minecraft:chest") || name.equals("minecraft:spawner")
                        || name.equals("minecraft:lodestone");
                    if (target && !MythicPassability.besideReached(reached, x, y, z)) {
                        problems.add(id + ": " + name + " at " + x + "," + y + "," + z + " cannot be reached on foot");
                    }
                    if (name.equals("minecraft:spawner") && !MythicPassability.spawnerOk(voxels, x, y, z)) {
                        problems.add(id + ": spawner at " + x + "," + y + "," + z
                            + " lacks a free 5x5x4 volume or an 11x11 room with 6 headroom");
                    }
                }
            }
        }
        if (connectors.size() >= 2) {
            Jigsaw first = connectors.getFirst();
            Set<Long> boxes = MythicPassability.mobWalk(voxels,
                List.of(MythicPassability.inward(first.x(), first.y(), first.z(), first.front())));
            for (Jigsaw jigsaw : connectors.subList(1, connectors.size())) {
                int[] inner = MythicPassability.inward(jigsaw.x(), jigsaw.y(), jigsaw.z(), jigsaw.front());
                if (!MythicPassability.mobCovers(boxes, inner[0], inner[1], inner[2])) {
                    problems.add(id + ": no 5-wide, 5-high passage for large mobs from " + first.x() + "," + first.y()
                        + "," + first.z() + " to " + jigsaw.x() + "," + jigsaw.y() + "," + jigsaw.z());
                }
            }
        }
        return problems;
    }

    /** A player reaches the rim of the descent hole at the level of the hole's first step. */
    private static boolean descentReached(Set<Long> reached, Jigsaw descent) {
        // On the rim of the 3x3 hole, standing on the bottom layer: one step down onto the shaft's first stair.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean rim = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                if (rim && reached.contains(MythicPassability.key(descent.x() + dx, descent.y() + 1, descent.z() + dz))) {
                    return true;
                }
            }
        }
        return false;
    }
}
