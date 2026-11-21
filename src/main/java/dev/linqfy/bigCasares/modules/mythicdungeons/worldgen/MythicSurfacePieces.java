package dev.linqfy.bigCasares.modules.mythicdungeons.worldgen;

import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;
import dev.linqfy.bigCasares.dimension.worldgen.StructureTemplate;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicCanvas.Face;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicPieces.Exit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pieces of the {@link MythicDungeonTheme.Layout#SURFACE} family: a building complex on the ground.
 *
 * <p>Ground convention: layers y = 0 and y = 1 are a solid foundation, y = {@link #FLOOR} is the floor
 * and every doorway is at walking level {@link #STAND}. The start (the courtyard) is projected with
 * {@code start_height} 0 onto {@code WORLD_SURFACE_WG} at its centre, so its layer y = 0 replaces the
 * top terrain block there; every other piece hangs off doorways at the same walking level, so all floors
 * are at one common height. The terrain adaptation (beard) then raises or cuts the ground to the
 * foundation of every piece. Towers rise from the same floor. The boss arena is the one exception: its
 * raised doorway puts its floor 3 below the common floor (a sunken great hall).</p>
 *
 * <p>Wing heads (pieces that may hang directly off a spine piece) are at most 9 wide along their entry
 * face and entered at its centre; spine side doorways are at least 4 blocks from both ends of their face.</p>
 */
final class MythicSurfacePieces {

    static final int FLOOR = 2;
    static final int STAND = FLOOR + 1;
    static final int PLAZA = 41;

    private MythicSurfacePieces() {
    }

    private static MythicCanvas canvas(MythicDungeonTheme theme, String name, int sx, int sy, int sz) {
        return new MythicCanvas(MythicPieces.template(theme, name), theme, sx, sy, sz);
    }

    /** Solid foundation under a rectangle: y = 0 and y = 1. */
    static void foundation(MythicCanvas canvas, int x1, int z1, int x2, int z2) {
        MythicDungeonTheme.Palette palette = canvas.palette();
        canvas.fill(x1, 0, z1, x2, 0, z2, palette.wallAlt());
        canvas.fill(x1, 1, z1, x2, 1, z2, palette.wall());
    }

    /** Foundation plus a closed building whose walls rise to {@code top} (the ceiling layer). */
    private static void building(MythicCanvas canvas, int sx, int sz, int top) {
        foundation(canvas, 0, 0, sx - 1, sz - 1);
        canvas.room(0, FLOOR, 0, sx - 1, top, sz - 1);
    }

    /** Foundation, floor, perimeter wall up to {@code wallTop} and open sky above the yard. */
    private static void yard(MythicCanvas canvas, int sx, int sz, int wallTop) {
        MythicDungeonTheme.Palette palette = canvas.palette();
        foundation(canvas, 0, 0, sx - 1, sz - 1);
        for (int x = 0; x < sx; x++) {
            for (int z = 0; z < sz; z++) {
                boolean edge = x == 0 || z == 0 || x == sx - 1 || z == sz - 1;
                canvas.set(x, FLOOR, z, edge ? palette.wall() : ((x + z) % 4 == 0 ? palette.floorAlt() : palette.floor()));
                for (int y = STAND; y < canvas.sizeY(); y++) {
                    canvas.set(x, y, z, edge && y <= wallTop ? canvas.wallBlock() : MythicCanvas.AIR);
                }
                if (edge && (x + z) % 2 == 0 && wallTop + 1 < canvas.sizeY()) {
                    canvas.set(x, wallTop + 1, z, palette.wall());
                }
            }
        }
    }

    /** Alternating merlons on the rectangle's rim at height {@code y}. */
    private static void crenels(MythicCanvas canvas, int x1, int z1, int x2, int z2, int y) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                boolean rim = x == x1 || x == x2 || z == z1 || z == z2;
                if (rim && (x + z) % 2 == 0) {
                    canvas.set(x, y, z, canvas.palette().wall());
                }
            }
        }
    }

    /** Slab roof covering the building top. */
    private static void slabRoof(MythicCanvas canvas, int y) {
        canvas.fill(0, y, 0, canvas.sizeX() - 1, y, canvas.sizeZ() - 1, canvas.palette().slab("bottom"));
    }

    /** Lamp post: two pillar blocks with a light on top. */
    private static void lamp(MythicCanvas canvas, int x, int z) {
        canvas.set(x, STAND, z, canvas.palette().pillar());
        canvas.set(x, STAND + 1, z, canvas.palette().pillar());
        canvas.set(x, STAND + 2, z, canvas.palette().light());
    }

    /** A public (non-jigsaw) gate: 3-wide opening with two steps up from the ground outside. */
    private static void postern(MythicCanvas canvas, Face face, int along) {
        String inward = switch (face) {
            case NORTH -> "south";
            case SOUTH -> "north";
            case WEST -> "east";
            case EAST -> "west";
        };
        for (int lateral = -1; lateral <= 1; lateral++) {
            int[] wall = cell(canvas, face, along + lateral, 0);
            int[] inner = cell(canvas, face, along + lateral, 1);
            canvas.set(wall[0], 1, wall[1], canvas.palette().stairs(inward, false));
            canvas.air(wall[0], FLOOR, wall[1], wall[0], STAND + 2, wall[1]);
            canvas.set(inner[0], FLOOR, inner[1], canvas.palette().stairs(inward, false));
        }
        int[] top = cell(canvas, face, along, 0);
        canvas.set(top[0], STAND + 3, top[1], canvas.palette().accent2());
    }

    /** Template x/z of a cell {@code depth} blocks inside {@code face} at {@code along}. */
    private static int[] cell(MythicCanvas canvas, Face face, int along, int depth) {
        return switch (face) {
            case NORTH -> new int[]{along, depth};
            case SOUTH -> new int[]{along, canvas.sizeZ() - 1 - depth};
            case WEST -> new int[]{depth, along};
            case EAST -> new int[]{canvas.sizeX() - 1 - depth, along};
        };
    }

    /**
     * Start: 41x41 walled courtyard with corner towers and the culture's centrepiece. Spine gate in the
     * north face, wing gates in the other three, four public posterns with steps.
     */
    static StructureTemplate plaza(MythicDungeonTheme theme, NamespacedId spine, NamespacedId wing) {
        int max = PLAZA - 1;
        int centre = PLAZA / 2;
        MythicCanvas canvas = canvas(theme, "plaza", PLAZA, 18, PLAZA);
        MythicDungeonTheme.Palette palette = theme.palette();
        yard(canvas, PLAZA, PLAZA, 7);
        for (int i = 1; i < max; i++) {
            canvas.set(centre, FLOOR, i, palette.accent2()).set(i, FLOOR, centre, palette.accent2());
        }
        for (int[] corner : new int[][]{{0, 0}, {max - 6, 0}, {0, max - 6}, {max - 6, max - 6}}) {
            int x1 = corner[0];
            int z1 = corner[1];
            canvas.room(x1, FLOOR, z1, x1 + 6, 14, z1 + 6);
            crenels(canvas, x1, z1, x1 + 6, z1 + 6, 15);
            canvas.set(x1 + 3, 14, z1 + 3, palette.light());
            // Doorway from the yard into the tower.
            int doorX = x1 == 0 ? x1 + 6 : x1;
            canvas.air(doorX, STAND, z1 + 2, doorX, STAND + 3, z1 + 4);
        }
        for (int[] spot : new int[][]{{12, 12}, {max - 12, 12}, {12, max - 12}, {max - 12, max - 12}}) {
            lamp(canvas, spot[0], spot[1]);
        }
        for (int i = 10; i < max - 9; i += 4) {
            canvas.set(0, STAND + 3, i, palette.light()).set(max, STAND + 3, i, palette.light());
        }
        centrepiece(canvas, theme, centre, centre);
        postern(canvas, Face.NORTH, 9);
        postern(canvas, Face.EAST, 9);
        postern(canvas, Face.SOUTH, max - 9);
        postern(canvas, Face.WEST, max - 9);
        MythicPieces.exits(canvas, theme, STAND, List.of(Exit.spine(Face.NORTH, centre, spine),
            Exit.branch(Face.SOUTH, centre, wing), Exit.branch(Face.WEST, centre, wing), Exit.branch(Face.EAST, centre, wing)));
        return canvas.build();
    }

    /** The plaza's monument: altar of Zeus, well, obelisk, skull altar, fountain. */
    private static void centrepiece(MythicCanvas canvas, MythicDungeonTheme theme, int cx, int cz) {
        MythicDungeonTheme.Palette palette = theme.palette();
        int y = STAND;
        switch (theme.entrance()) {
            case TEMPLE -> {
                canvas.fill(cx - 2, y, cz - 2, cx + 2, y, cz + 2, palette.wallAlt());
                canvas.fill(cx - 1, y + 1, cz - 1, cx + 1, y + 1, cz + 1, palette.accent2());
                canvas.set(cx, y + 2, cz, palette.accent());
                canvas.set(cx, y + 3, cz, palette.clutter());
                for (int[] spot : new int[][]{{-6, -6}, {6, -6}, {-6, 6}, {6, 6}}) {
                    statue(canvas, cx + spot[0], cz + spot[1]);
                }
            }
            case RUINED_KEEP -> {
                for (int x = cx - 2; x <= cx + 2; x++) {
                    for (int z = cz - 2; z <= cz + 2; z++) {
                        boolean rim = Math.abs(x - cx) == 2 || Math.abs(z - cz) == 2;
                        canvas.set(x, y, z, rim ? palette.wall() : BlockStateSpec.of("minecraft:water"));
                    }
                }
                for (int[] corner : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
                    canvas.fill(cx + corner[0], y + 1, cz + corner[1], cx + corner[0], y + 3, cz + corner[1], palette.pillar());
                }
                canvas.fill(cx - 2, y + 4, cz - 2, cx + 2, y + 4, cz + 2, palette.slab("bottom"));
                canvas.set(cx, y + 3, cz, palette.hangingLight());
            }
            case PYRAMID -> {
                canvas.fill(cx - 1, y, cz - 1, cx + 1, y, cz + 1, palette.wall());
                canvas.fill(cx, y + 1, cz, cx, y + 10, cz, palette.pillar());
                canvas.set(cx, y + 11, cz, palette.accent());
                for (int[] spot : new int[][]{{-3, 0}, {3, 0}, {0, -3}, {0, 3}}) {
                    canvas.set(cx + spot[0], FLOOR, cz + spot[1], palette.light());
                }
            }
            case STEPPED_PYRAMID -> {
                canvas.fill(cx - 3, y, cz - 3, cx + 3, y, cz + 3, palette.wall());
                canvas.fill(cx - 2, y + 1, cz - 2, cx + 2, y + 1, cz + 2, palette.wallAlt());
                canvas.fill(cx - 1, y + 2, cz - 1, cx + 1, y + 2, cz + 1, palette.accent2());
                canvas.set(cx, y + 3, cz, palette.accent());
                canvas.set(cx, y + 4, cz, palette.clutter());
                for (int i = -3; i <= 3; i++) {
                    canvas.set(cx + i, y, cz - 3, palette.stairs("south", false));
                    canvas.set(cx + i, y, cz + 3, palette.stairs("north", false));
                }
            }
            case DOMED_PAVILION -> {
                for (int x = cx - 3; x <= cx + 3; x++) {
                    for (int z = cz - 3; z <= cz + 3; z++) {
                        boolean rim = Math.abs(x - cx) == 3 || Math.abs(z - cz) == 3;
                        canvas.set(x, y, z, rim ? palette.accent2() : BlockStateSpec.of("minecraft:water"));
                    }
                }
                canvas.fill(cx, y, cz, cx, y + 2, cz, palette.pillar());
                canvas.set(cx, y + 3, cz, palette.accent());
                canvas.set(cx, y + 4, cz, palette.light());
            }
            default -> MythicUndergroundPieces.setPiece(canvas, theme, cx, cz, FLOOR);
        }
    }

    /** Statue: plinth, body and a gilded head. */
    private static void statue(MythicCanvas canvas, int x, int z) {
        MythicDungeonTheme.Palette palette = canvas.palette();
        canvas.set(x, STAND, z, palette.accent2());
        canvas.set(x, STAND + 1, z, palette.pillar());
        canvas.set(x, STAND + 2, z, palette.pillar());
        canvas.set(x, STAND + 3, z, palette.accent());
    }

    /** Column of the culture's style from the floor up to {@code STAND + height - 1}. */
    private static void column(MythicCanvas canvas, MythicDungeonTheme theme, int x, int z, int height) {
        MythicDungeonTheme.Palette palette = theme.palette();
        int top = STAND + height - 1;
        switch (theme.entrance()) {
            case PYRAMID -> {
                canvas.fill(x, STAND, z, x, top, z, palette.pillar());
                canvas.set(x, top + 1, z, palette.accent());
            }
            case DOMED_PAVILION -> {
                canvas.fill(x, STAND, z, x, top, z, BlockStateSpec.of("minecraft:jungle_log", Map.of("axis", "y")));
                BlockStateSpec leaves = BlockStateSpec.of("minecraft:jungle_leaves", Map.of("persistent", "true"));
                canvas.set(x, top + 1, z, leaves);
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    canvas.set(x + d[0], top, z + d[1], leaves);
                }
            }
            case STEPPED_PYRAMID -> {
                canvas.fill(x, STAND, z, x, top - 1, z, palette.wall());
                canvas.set(x, top, z, palette.accent2());
                canvas.set(x, top + 1, z, palette.clutter());
            }
            case RUINED_KEEP -> {
                canvas.fill(x, STAND, z, x, top - 1, z, palette.pillar());
                canvas.set(x, top, z, palette.light());
            }
            default -> canvas.pillar(x, STAND, z, top);
        }
    }

    /** Spine 1: 21x17x15 gatehouse with four corner turrets; wings east and west. */
    static StructureTemplate gatehouse(MythicDungeonTheme theme, NamespacedId next, NamespacedId wing) {
        MythicCanvas canvas = canvas(theme, "gatehouse", 21, 17, 15);
        MythicDungeonTheme.Palette palette = theme.palette();
        building(canvas, 21, 15, 11);
        crenels(canvas, 0, 0, 20, 14, 12);
        for (int[] corner : new int[][]{{0, 0}, {16, 0}, {0, 10}, {16, 10}}) {
            for (int x = corner[0]; x <= corner[0] + 4; x++) {
                for (int z = corner[1]; z <= corner[1] + 4; z++) {
                    boolean rim = x == corner[0] || x == corner[0] + 4 || z == corner[1] || z == corner[1] + 4;
                    canvas.fill(x, 12, z, x, 14, z, rim ? canvas.wallBlock() : MythicCanvas.AIR);
                }
            }
            crenels(canvas, corner[0], corner[1], corner[0] + 4, corner[1] + 4, 15);
            canvas.set(corner[0] + 2, 12, corner[1] + 2, palette.light());
        }
        for (int x : new int[]{1, 19}) {
            for (int z : new int[]{3, 11}) {
                canvas.pillar(x, STAND, z, 10);
            }
        }
        for (int x = 1; x < 20; x++) {
            canvas.set(x, 9, 0, palette.accent2()).set(x, 9, 14, palette.accent2());
        }
        canvas.ceilingLights(4, 3, 16, 11, 11, 4);
        canvas.spawner(19, STAND, 13, theme.spawnerMob());
        canvas.chest(1, STAND, 13, "east", theme.commonLoot());
        MythicPieces.entry(canvas, theme, 10, STAND);
        MythicPieces.exits(canvas, theme, STAND, List.of(Exit.spine(Face.SOUTH, 10, next),
            Exit.branch(Face.WEST, 7, wing), Exit.branch(Face.EAST, 7, wing)));
        return canvas.build();
    }

    /** Spine 2: 23x15x27 open-air processional avenue lined with the culture's columns; four wing doors. */
    static StructureTemplate avenue(MythicDungeonTheme theme, NamespacedId next, NamespacedId wing) {
        MythicCanvas canvas = canvas(theme, "avenue", 23, 15, 27);
        MythicDungeonTheme.Palette palette = theme.palette();
        yard(canvas, 23, 27, 6);
        for (int z = 1; z < 26; z++) {
            for (int x = 9; x <= 13; x++) {
                canvas.set(x, FLOOR, z, x == 9 || x == 13 ? palette.accent2() : palette.floorAlt());
            }
        }
        for (int z = 5; z < 26; z += 8) {
            canvas.set(11, FLOOR, z, palette.light());
        }
        // Columns stand two blocks from the side walls, six apart: 5-wide gaps everywhere.
        for (int x : new int[]{3, 19}) {
            for (int z = 3; z <= 21; z += 6) {
                column(canvas, theme, x, z, 7);
            }
            if (theme.entrance() == MythicDungeonTheme.EntranceStyle.TEMPLE) {
                canvas.fill(x, STAND + 7, 3, x, STAND + 7, 21, palette.slab("bottom"));
            }
        }
        canvas.spawner(1, STAND, 12, theme.spawnerMob());
        canvas.spawner(21, STAND, 12, theme.spawnerMob());
        MythicPieces.entry(canvas, theme, 11, STAND);
        MythicPieces.exits(canvas, theme, STAND, List.of(Exit.spine(Face.SOUTH, 11, next),
            Exit.branch(Face.WEST, 6, wing), Exit.branch(Face.WEST, 18, wing),
            Exit.branch(Face.EAST, 6, wing), Exit.branch(Face.EAST, 18, wing)));
        return canvas.build();
    }

    /** Spine 3: 17x14x15 vestibule in front of the boss hall; wings east and west. */
    static StructureTemplate vestibule(MythicDungeonTheme theme, NamespacedId next, NamespacedId wing) {
        MythicCanvas canvas = canvas(theme, "vestibule", 17, 14, 15);
        MythicDungeonTheme.Palette palette = theme.palette();
        building(canvas, 17, 15, 12);
        crenels(canvas, 0, 0, 16, 14, 13);
        for (int x : new int[]{1, 15}) {
            for (int z : new int[]{3, 11}) {
                canvas.pillar(x, STAND, z, 11);
            }
        }
        for (int z = 1; z < 14; z++) {
            canvas.set(8, FLOOR, z, palette.accent2());
        }
        canvas.ceilingLights(2, 3, 14, 11, 12, 4);
        canvas.set(8, 11, 4, palette.hangingLight()).set(8, 11, 10, palette.hangingLight());
        canvas.spawner(1, STAND, 13, theme.spawnerMob());
        canvas.spawner(15, STAND, 1, theme.spawnerMob());
        canvas.chest(15, STAND, 13, "west", theme.treasureLoot());
        MythicPieces.entry(canvas, theme, 8, STAND);
        MythicPieces.exits(canvas, theme, STAND, List.of(Exit.spine(Face.SOUTH, 8, next),
            Exit.branch(Face.WEST, 7, wing), Exit.branch(Face.EAST, 7, wing)));
        return canvas.build();
    }

    /** 7x10x13 covered walkway with barred windows. */
    static StructureTemplate corridor(MythicDungeonTheme theme, NamespacedId branch) {
        MythicCanvas canvas = canvas(theme, "corridor", 7, 10, 13);
        MythicDungeonTheme.Palette palette = theme.palette();
        building(canvas, 7, 13, 8);
        slabRoof(canvas, 9);
        for (int z : new int[]{4, 8}) {
            canvas.set(0, 5, z, BlockStateSpec.of("minecraft:iron_bars")).set(6, 5, z, BlockStateSpec.of("minecraft:iron_bars"));
        }
        canvas.set(3, 8, 3, palette.light()).set(3, 8, 9, palette.light());
        MythicPieces.entry(canvas, theme, 3, STAND);
        MythicPieces.exits(canvas, theme, STAND, List.of(Exit.branch(Face.SOUTH, 3, branch)));
        return canvas.build();
    }

    /** 9x11x9 pavilion entered from the north; {@code faces} are its exits. */
    static StructureTemplate junction(MythicDungeonTheme theme, String name, List<Face> faces, NamespacedId branch) {
        MythicCanvas canvas = canvas(theme, name, 9, 11, 9);
        MythicDungeonTheme.Palette palette = theme.palette();
        building(canvas, 9, 9, 9);
        slabRoof(canvas, 10);
        for (int[] corner : new int[][]{{1, 1}, {7, 1}, {1, 7}, {7, 7}}) {
            canvas.pillar(corner[0], STAND, corner[1], 8);
        }
        canvas.set(4, 9, 4, palette.light());
        canvas.set(4, FLOOR, 4, palette.accent2());
        List<Exit> exits = new ArrayList<>();
        for (Face face : faces) {
            exits.add(Exit.branch(face, 4, branch));
        }
        MythicPieces.entry(canvas, theme, 4, STAND);
        MythicPieces.exits(canvas, theme, STAND, exits);
        return canvas.build();
    }

    /**
     * 17x15x21 great hall with the culture's set-piece: cella, chapel, hypostyle, council hall, domed hall.
     * The front (z 1..12) is an open floor with the spawners on its walls; furniture stands in the back,
     * either side of a 5-wide aisle to the south doorway.
     */
    static StructureTemplate hall(MythicDungeonTheme theme, NamespacedId branch) {
        MythicCanvas canvas = canvas(theme, "hall", 17, 15, 21);
        MythicDungeonTheme.Palette palette = theme.palette();
        building(canvas, 17, 21, 12);
        crenels(canvas, 0, 0, 16, 20, 13);
        canvas.ceilingLights(2, 2, 14, 18, 12, 4);
        int y = STAND;
        switch (theme.entrance()) {
            case TEMPLE -> {
                for (int z : new int[]{4, 15, 18}) {
                    canvas.pillar(1, y, z, 11).pillar(15, y, z, 11);
                }
                statue(canvas, 3, 16);
                statue(canvas, 13, 16);
                canvas.fill(2, FLOOR, 18, 4, FLOOR, 18, palette.accent());
                canvas.fill(12, FLOOR, 18, 14, FLOOR, 18, palette.accent());
            }
            case RUINED_KEEP -> {
                for (int z = 14; z <= 17; z += 3) {
                    for (int x : new int[]{2, 3, 4, 12, 13, 14}) {
                        canvas.set(x, y, z, palette.stairs("north", false));
                    }
                }
                canvas.fill(2, y, 19, 4, y, 19, palette.accent2()).fill(12, y, 19, 14, y, 19, palette.accent2());
                canvas.set(3, y + 1, 19, palette.light()).set(13, y + 1, 19, palette.light());
                for (int z : new int[]{4, 8, 13, 17}) {
                    BlockStateSpec glass = BlockStateSpec.of(z % 2 == 0 ? "minecraft:red_stained_glass" : "minecraft:blue_stained_glass");
                    canvas.fill(0, 7, z, 0, 9, z, glass).fill(16, 7, z, 16, 9, z, glass);
                }
            }
            case PYRAMID -> {
                for (int x : new int[]{2, 4, 12, 14}) {
                    for (int z : new int[]{14, 17}) {
                        canvas.fill(x, y, z, x, 10, z, palette.pillar());
                        canvas.set(x, 11, z, palette.accent2());
                    }
                }
            }
            case STEPPED_PYRAMID -> {
                canvas.set(3, y, 17, palette.stairs("east", false));
                canvas.fill(3, y, 16, 3, y + 1, 16, palette.accent()).fill(3, y, 18, 3, y + 1, 18, palette.accent());
                canvas.fill(2, y, 16, 2, y + 2, 18, palette.accent2());
                for (int z = 2; z <= 18; z += 2) {
                    if (z < 9 || z > 11) {
                        canvas.fill(0, 6, z, 0, 9, z, palette.accent2()).fill(16, 6, z, 16, 9, z, palette.accent2());
                    }
                }
            }
            case DOMED_PAVILION -> {
                for (int x = 2; x <= 14; x++) {
                    for (int z = 2; z <= 18; z++) {
                        boolean border = x == 2 || x == 14 || z == 2 || z == 18;
                        canvas.set(x, y, z, BlockStateSpec.of(border ? "minecraft:yellow_carpet" : "minecraft:red_carpet"));
                    }
                }
                canvas.set(8, 11, 10, palette.hangingLight());
                canvas.fill(7, 12, 9, 9, 12, 11, palette.light());
            }
            default -> {
                for (int z : new int[]{4, 16}) {
                    canvas.pillar(1, y, z, 11).pillar(15, y, z, 11);
                }
            }
        }
        canvas.spawner(1, y, 2, theme.spawnerMob());
        canvas.spawner(15, y, 6, theme.spawnerMob());
        MythicPieces.entry(canvas, theme, 8, STAND);
        MythicPieces.exits(canvas, theme, STAND, List.of(Exit.branch(Face.WEST, 10, branch),
            Exit.branch(Face.EAST, 10, branch), Exit.branch(Face.SOUTH, 8, branch)));
        return canvas.build();
    }

    /**
     * 19x13x19 open-air court: altar court, cloister, sacred pools, ball court, garden. The central cross
     * (5 wide) between the four doorways stays free; the culture's features fill the four quadrants.
     */
    static StructureTemplate court(MythicDungeonTheme theme, NamespacedId branch) {
        MythicCanvas canvas = canvas(theme, "court", 19, 13, 19);
        MythicDungeonTheme.Palette palette = theme.palette();
        yard(canvas, 19, 19, 6);
        int y = STAND;
        int[][] quadrants = {{4, 4}, {14, 4}, {4, 14}, {14, 14}};
        switch (theme.entrance()) {
            case TEMPLE -> {
                for (int[] spot : quadrants) {
                    statue(canvas, spot[0], spot[1]);
                    canvas.set(spot[0], FLOOR, spot[1] + 1, palette.accent());
                }
                canvas.set(9, FLOOR, 9, palette.accent());
            }
            case RUINED_KEEP -> {
                for (int[] spot : new int[][]{{2, 2}, {5, 2}, {2, 5}, {16, 2}, {13, 2}, {16, 5}, {2, 16}, {2, 13}, {5, 16}}) {
                    canvas.pillar(spot[0], y, spot[1], 7);
                }
                for (int x = 13; x <= 15; x++) {
                    for (int z = 13; z <= 15; z++) {
                        boolean rim = x != 14 || z != 14;
                        canvas.set(x, y, z, rim ? palette.wall() : BlockStateSpec.of("minecraft:water"));
                    }
                }
            }
            case PYRAMID -> {
                for (int[] spot : quadrants) {
                    for (int x = spot[0] - 1; x <= spot[0] + 1; x++) {
                        for (int z = spot[1] - 1; z <= spot[1] + 1; z++) {
                            canvas.set(x, FLOOR, z, BlockStateSpec.of("minecraft:water"));
                        }
                    }
                    canvas.set(spot[0], y, spot[1], BlockStateSpec.of("minecraft:lily_pad"));
                }
                for (int[] spot : new int[][]{{2, 2}, {16, 2}, {2, 16}, {16, 16}}) {
                    column(canvas, theme, spot[0], spot[1], 6);
                }
            }
            case STEPPED_PYRAMID -> {
                for (int z = 2; z <= 16; z++) {
                    if (z >= 6 && z <= 12) {
                        continue;
                    }
                    canvas.set(1, y, z, palette.wall()).set(1, y + 1, z, palette.stairs("east", false));
                    canvas.set(2, y, z, palette.stairs("east", false));
                    canvas.set(17, y, z, palette.wall()).set(17, y + 1, z, palette.stairs("west", false));
                    canvas.set(16, y, z, palette.stairs("west", false));
                }
                canvas.set(0, y + 4, 4, palette.accent()).set(18, y + 4, 14, palette.accent());
                canvas.set(9, FLOOR, 9, palette.accent());
            }
            case DOMED_PAVILION -> {
                String[] flowers = {"minecraft:poppy", "minecraft:dandelion", "minecraft:azure_bluet", "minecraft:cornflower"};
                for (int[] spot : quadrants) {
                    for (int x = spot[0] - 2; x <= spot[0] + 2; x++) {
                        for (int z = spot[1] - 2; z <= spot[1] + 2; z++) {
                            boolean channel = x == spot[0] || z == spot[1];
                            canvas.set(x, FLOOR, z, channel ? BlockStateSpec.of("minecraft:water")
                                : BlockStateSpec.of("minecraft:grass_block"));
                            if (!channel) {
                                canvas.set(x, y, z, BlockStateSpec.of(flowers[Math.floorMod(x + z, flowers.length)]));
                            }
                        }
                    }
                    canvas.set(spot[0], FLOOR, spot[1], palette.accent2());
                    canvas.set(spot[0], y, spot[1], MythicCanvas.AIR);
                    column(canvas, theme, spot[0], spot[1], 6);
                }
            }
            default -> {
                for (int[] spot : quadrants) {
                    statue(canvas, spot[0], spot[1]);
                }
            }
        }
        canvas.set(9, FLOOR, 4, palette.light()).set(9, FLOOR, 14, palette.light());
        canvas.set(4, FLOOR, 9, palette.light()).set(14, FLOOR, 9, palette.light());
        MythicPieces.entry(canvas, theme, 9, STAND);
        MythicPieces.exits(canvas, theme, STAND, List.of(Exit.branch(Face.WEST, 9, branch),
            Exit.branch(Face.EAST, 9, branch), Exit.branch(Face.SOUTH, 9, branch)));
        return canvas.build();
    }

    /** 9x24x9 dead-end tower (watchtower, minaret, pylon) with a ladder to a lookout over the complex. */
    static StructureTemplate tower(MythicDungeonTheme theme) {
        MythicCanvas canvas = canvas(theme, "tower", 9, 24, 9);
        MythicDungeonTheme.Palette palette = theme.palette();
        building(canvas, 9, 9, 20);
        canvas.fill(1, 11, 1, 7, 11, 7, palette.floor());
        canvas.wall(0, 21, 0, 8, 21, 8);
        canvas.air(1, 21, 1, 7, 21, 7);
        crenels(canvas, 0, 0, 8, 8, 22);
        BlockStateSpec ladder = BlockStateSpec.of("minecraft:ladder", Map.of("facing", "north"));
        canvas.fill(4, STAND, 7, 4, 20, 7, ladder);
        for (int y : new int[]{9, 16}) {
            // No window in the south wall: it holds the ladder.
            canvas.air(4, y, 0, 4, y + 1, 0).air(0, y, 4, 0, y + 1, 4).air(8, y, 4, 8, y + 1, 4);
        }
        canvas.set(2, 11, 6, palette.light()).set(6, 20, 2, palette.light()).set(2, 20, 2, palette.light());
        canvas.set(2, 21, 2, palette.light());
        switch (theme.entrance()) {
            case DOMED_PAVILION -> {
                canvas.fill(3, 22, 3, 5, 22, 5, palette.roof());
                canvas.set(4, 23, 4, BlockStateSpec.of("minecraft:lightning_rod", Map.of("facing", "up")));
            }
            case PYRAMID, STEPPED_PYRAMID -> canvas.set(6, 22, 6, palette.accent());
            default -> canvas.set(6, 22, 6, palette.pillar());
        }
        canvas.set(6, 12, 2, palette.clutter());
        MythicPieces.entry(canvas, theme, 4, STAND);
        return canvas.build();
    }

    /** 9x11x11 dead-end treasury: one treasure chest on a gilded floor. */
    static StructureTemplate treasury(MythicDungeonTheme theme) {
        MythicCanvas canvas = canvas(theme, "treasury", 9, 11, 11);
        MythicDungeonTheme.Palette palette = theme.palette();
        building(canvas, 9, 11, 9);
        slabRoof(canvas, 10);
        for (int x = 2; x <= 6; x += 2) {
            canvas.set(x, FLOOR, 8, palette.accent());
        }
        canvas.chest(4, STAND, 9, "north", theme.treasureLoot());
        canvas.pillar(1, STAND, 9, 7).pillar(7, STAND, 9, 7);
        canvas.set(4, 9, 3, palette.light()).set(4, 9, 7, palette.light());
        MythicPieces.entry(canvas, theme, 4, STAND);
        return canvas.build();
    }

    /** 15x11x19 dead-end barracks: open drill floor in front, barred cells at the back. */
    static StructureTemplate barracks(MythicDungeonTheme theme) {
        MythicCanvas canvas = canvas(theme, "barracks", 15, 11, 19);
        MythicDungeonTheme.Palette palette = theme.palette();
        building(canvas, 15, 19, 9);
        slabRoof(canvas, 10);
        BlockStateSpec bars = BlockStateSpec.of("minecraft:iron_bars", Map.of("east", "true", "west", "true"));
        canvas.fill(1, STAND, 12, 13, STAND + 2, 12, bars);
        canvas.wall(5, STAND, 13, 5, 8, 17);
        canvas.wall(9, STAND, 13, 9, 8, 17);
        // A 3-wide gap into each cell.
        canvas.air(2, STAND, 12, 4, STAND + 2, 12).air(6, STAND, 12, 8, STAND + 2, 12).air(10, STAND, 12, 12, STAND + 2, 12);
        canvas.set(1, STAND, 17, palette.clutter()).set(13, STAND, 17, palette.clutter());
        canvas.set(7, STAND, 17, palette.clutter());
        canvas.spawner(1, STAND, 6, theme.spawnerMob());
        canvas.spawner(13, STAND, 6, theme.spawnerMob());
        canvas.ceilingLights(3, 3, 11, 15, 9, 4);
        MythicPieces.entry(canvas, theme, 7, STAND);
        return canvas.build();
    }

    /** 9x12x11 dead-end shrine with the culture's idol and an offering chest. */
    static StructureTemplate shrine(MythicDungeonTheme theme) {
        MythicCanvas canvas = canvas(theme, "shrine", 9, 12, 11);
        MythicDungeonTheme.Palette palette = theme.palette();
        building(canvas, 9, 11, 10);
        slabRoof(canvas, 11);
        int y = STAND;
        switch (theme.entrance()) {
            case TEMPLE -> {
                canvas.set(4, y, 9, palette.accent2());
                canvas.fill(4, y + 1, 9, 4, y + 2, 9, palette.pillar());
                canvas.set(4, y + 3, 9, palette.accent());
            }
            case PYRAMID -> {
                canvas.fill(3, y, 9, 5, y, 9, palette.accent2());
                canvas.set(4, y + 1, 9, palette.accent());
            }
            case STEPPED_PYRAMID -> {
                canvas.set(4, y, 9, palette.accent());
                canvas.set(3, y, 9, palette.accent2()).set(5, y, 9, palette.accent2());
                canvas.set(4, y + 1, 9, palette.clutter());
            }
            case DOMED_PAVILION -> {
                canvas.fill(3, y, 10, 5, y + 4, 10, palette.accent2());
                canvas.set(4, y, 9, palette.light());
            }
            default -> {
                canvas.fill(3, y, 9, 5, y, 9, palette.accent2());
                canvas.set(4, y + 1, 9, palette.accent());
            }
        }
        canvas.chest(2, y, 9, "east", theme.treasureLoot());
        canvas.set(4, 10, 5, palette.light());
        canvas.set(4, 9, 3, palette.hangingLight());
        MythicPieces.entry(canvas, theme, 4, STAND);
        return canvas.build();
    }
}
