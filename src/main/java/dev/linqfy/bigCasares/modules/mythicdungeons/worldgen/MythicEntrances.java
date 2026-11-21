package dev.linqfy.bigCasares.modules.mythicdungeons.worldgen;

import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;
import dev.linqfy.bigCasares.dimension.worldgen.StructureTemplate;

import java.util.Map;
import java.util.Random;

/**
 * Surface start pieces. Every entrance is a 15x15 footprint whose layer y = 0 replaces the
 * top terrain block, players walk in at y = 1 from the north, and a 3x3 hole at
 * x 6..8 / z 5..7 leads into the shaft through a downward jigsaw at (7, 0, 6).
 */
final class MythicEntrances {

    static final int FOOTPRINT = 15;
    static final int HOLE_X = 7;
    static final int HOLE_Z = 6;

    private MythicEntrances() {
    }

    static StructureTemplate entrance(MythicDungeonTheme theme, NamespacedId shaftPool) {
        int height = switch (theme.entrance()) {
            case TEMPLE -> 17;
            case LONGHOUSE -> 13;
            case RUINED_KEEP -> 14;
            case PYRAMID -> 9;
            case ZIGGURAT -> 13;
            case TORII_SHRINE -> 12;
            case STEPPED_PYRAMID -> 15;
            case DOMED_PAVILION -> 15;
            case CAVE_MOUTH -> 11;
        };
        MythicCanvas canvas = new MythicCanvas(MythicPieces.template(theme, "entrance"), theme, FOOTPRINT, height, FOOTPRINT);
        MythicDungeonTheme.Palette palette = theme.palette();
        canvas.air(0, 1, 0, 14, height - 1, 14);
        for (int x = 0; x < FOOTPRINT; x++) {
            for (int z = 0; z < FOOTPRINT; z++) {
                boolean border = x == 0 || z == 0 || x == 14 || z == 14;
                canvas.set(x, 0, z, border ? palette.floorAlt() : palette.floor());
            }
        }
        switch (theme.entrance()) {
            case TEMPLE -> temple(canvas);
            case LONGHOUSE -> longhouse(canvas);
            case RUINED_KEEP -> keep(canvas);
            case PYRAMID -> pyramid(canvas);
            case ZIGGURAT -> ziggurat(canvas);
            case TORII_SHRINE -> toriiShrine(canvas);
            case STEPPED_PYRAMID -> steppedPyramid(canvas);
            case DOMED_PAVILION -> domedPavilion(canvas);
            case CAVE_MOUTH -> caveMouth(canvas);
        }
        hole(canvas, theme, shaftPool);
        return canvas.build();
    }

    private static void hole(MythicCanvas canvas, MythicDungeonTheme theme, NamespacedId shaftPool) {
        MythicDungeonTheme.Palette palette = theme.palette();
        canvas.air(HOLE_X - 1, 0, HOLE_Z - 1, HOLE_X + 1, 3, HOLE_Z + 1);
        for (int z = HOLE_Z - 1; z <= HOLE_Z + 1; z++) {
            canvas.set(HOLE_X - 2, 1, z, palette.slab("bottom")).set(HOLE_X + 2, 1, z, palette.slab("bottom"));
        }
        for (int x = HOLE_X - 2; x <= HOLE_X + 2; x++) {
            canvas.set(x, 1, HOLE_Z + 2, palette.slab("bottom"));
            canvas.set(x, 0, HOLE_Z - 2, palette.accent2());
        }
        canvas.jigsaw(HOLE_X, 0, HOLE_Z, "down_south", MythicPieces.EMPTY, MythicPieces.shaftTop(theme), shaftPool,
            MythicCanvas.AIR, MythicPieces.SPINE_SELECTION, MythicPieces.SPINE_PLACEMENT);
    }

    private static BlockStateSpec block(String id) {
        return BlockStateSpec.of(id);
    }

    /** Greek: peripteral temple with quartz columns, entablature and a gable roof with pediments. */
    private static void temple(MythicCanvas canvas) {
        MythicDungeonTheme.Palette palette = canvas.palette();
        int[] columns = {2, 5, 9, 12};
        for (int x : columns) {
            for (int z : columns) {
                boolean perimeter = x == 2 || x == 12 || z == 2 || z == 12;
                if (perimeter) {
                    canvas.set(x, 1, z, palette.accent2());
                    canvas.pillar(x, 2, z, 8);
                }
            }
        }
        for (int x = 2; x <= 12; x++) {
            for (int z = 2; z <= 12; z++) {
                canvas.set(x, 9, z, palette.ceiling());
            }
        }
        canvas.set(4, 9, 4, palette.light()).set(10, 9, 4, palette.light());
        canvas.set(4, 9, 10, palette.light()).set(10, 9, 10, palette.light());
        for (int i = 0; i <= 6; i++) {
            int y = 10 + i;
            int left = 1 + i;
            int right = 13 - i;
            for (int z = 1; z <= 13; z++) {
                if (left == right) {
                    canvas.set(left, y, z, palette.slab("bottom"));
                    continue;
                }
                canvas.set(left, y, z, palette.roofStairs("east", false));
                canvas.set(right, y, z, palette.roofStairs("west", false));
                if (z == 1 || z == 13) {
                    for (int x = left + 1; x < right; x++) {
                        canvas.set(x, y, z, palette.wall());
                    }
                }
            }
        }
        canvas.set(7, 12, 1, palette.accent()).set(7, 12, 13, palette.accent());
        canvas.set(7, 16, 1, BlockStateSpec.of("minecraft:lightning_rod", Map.of("facing", "up")));
        canvas.set(7, 16, 13, BlockStateSpec.of("minecraft:lightning_rod", Map.of("facing", "up")));
        for (int x = 1; x <= 13; x++) {
            canvas.set(x, 0, 1, palette.stairs("south", false));
        }
    }

    /** Norse: stone-footed spruce longhouse with a steep roof, gable horns and runestones. */
    private static void longhouse(MythicCanvas canvas) {
        MythicDungeonTheme.Palette palette = canvas.palette();
        for (int x = 3; x <= 11; x++) {
            for (int z = 1; z <= 13; z++) {
                boolean wall = x == 3 || x == 11 || z == 1 || z == 13;
                if (!wall) {
                    continue;
                }
                canvas.set(x, 1, z, canvas.wallBlock());
                for (int y = 2; y <= 4; y++) {
                    boolean post = (x == 3 || x == 11) && z % 4 == 1 || (z == 1 || z == 13) && (x == 3 || x == 11);
                    canvas.set(x, y, z, post ? palette.pillar() : palette.floor());
                }
            }
        }
        canvas.air(6, 1, 1, 8, 3, 1);
        canvas.set(5, 1, 1, palette.pillar()).set(9, 1, 1, palette.pillar());
        for (int i = 0; i <= 5; i++) {
            int y = 5 + i;
            int left = 2 + i;
            int right = 12 - i;
            for (int z = 0; z <= 14; z++) {
                if (left == right) {
                    canvas.set(left, y, z, palette.roof());
                    continue;
                }
                canvas.set(left, y, z, palette.roofStairs("east", false));
                canvas.set(right, y, z, palette.roofStairs("west", false));
                if (z == 1 || z == 13) {
                    for (int x = left + 1; x < right; x++) {
                        canvas.set(x, y, z, palette.roof());
                    }
                }
            }
        }
        canvas.set(7, 11, 0, palette.pillar()).set(7, 11, 14, palette.pillar());
        canvas.set(7, 0, 10, palette.light()).set(7, 0, 11, palette.light());
        for (int[] edge : new int[][]{{6, 10}, {8, 10}, {6, 11}, {8, 11}}) {
            canvas.set(edge[0], 1, edge[1], palette.slab("bottom"));
        }
        canvas.set(5, 4, 9, palette.hangingLight()).set(9, 4, 9, palette.hangingLight());
        canvas.set(5, 4, 3, palette.hangingLight()).set(9, 4, 3, palette.hangingLight());
        for (int x : new int[]{1, 13}) {
            canvas.set(x, 1, 3, palette.accent2());
            canvas.set(x, 2, 3, palette.wallAlt());
            canvas.set(x, 3, 3, palette.accent());
        }
        for (int z = 4; z <= 12; z += 4) {
            canvas.set(1, 0, z, palette.accent()).set(13, 0, z, palette.accent());
        }
    }

    /** Medieval: ruined square keep with jagged walls, crenellations, beams, bars and webs. */
    private static void keep(MythicCanvas canvas) {
        MythicDungeonTheme.Palette palette = canvas.palette();
        Random random = canvas.random();
        for (int x = 2; x <= 12; x++) {
            for (int z = 2; z <= 12; z++) {
                boolean wall = x == 2 || x == 12 || z == 2 || z == 12;
                if (!wall) {
                    continue;
                }
                int height = 13 - random.nextInt(6);
                for (int y = 1; y <= height; y++) {
                    canvas.set(x, y, z, random.nextInt(5) == 0 ? palette.floor() : canvas.wallBlock());
                }
                if (height == 13 && (x + z) % 2 == 0) {
                    canvas.set(x, 13, z, MythicCanvas.AIR);
                }
            }
        }
        canvas.air(6, 1, 2, 8, 4, 2);
        canvas.set(7, 5, 2, palette.accent2());
        BlockStateSpec barsX = BlockStateSpec.of("minecraft:iron_bars", Map.of("east", "true", "west", "true"));
        BlockStateSpec barsZ = BlockStateSpec.of("minecraft:iron_bars", Map.of("north", "true", "south", "true"));
        canvas.set(7, 7, 12, barsX).set(2, 7, 7, barsZ).set(12, 7, 7, barsZ);
        for (int[] corner : new int[][]{{3, 3}, {11, 3}, {3, 11}, {11, 11}}) {
            canvas.fill(corner[0], 1, corner[1], corner[0], 6, corner[1], palette.pillar());
            canvas.set(corner[0], 5, corner[1] == 3 ? 4 : 10, palette.clutter());
        }
        for (int x = 3; x <= 11; x++) {
            if (x != 7) {
                canvas.set(x, 6, 3, palette.ceiling()).set(x, 6, 11, palette.ceiling());
            }
        }
        canvas.set(5, 5, 3, palette.hangingLight()).set(9, 5, 11, palette.hangingLight());
        canvas.set(4, 1, 10, palette.clutter()).set(10, 1, 4, palette.clutter());
        for (int i = 0; i < 6; i++) {
            int x = random.nextBoolean() ? random.nextInt(2) : 13 + random.nextInt(2);
            int z = random.nextInt(15);
            canvas.set(x, 1, z, palette.floorAlt());
        }
    }

    /** Egyptian: sloped sandstone pyramid with a gold capstone, inner chamber and obelisks. */
    private static void pyramid(MythicCanvas canvas) {
        MythicDungeonTheme.Palette palette = canvas.palette();
        for (int y = 1; y <= 7; y++) {
            int min = y;
            int max = 14 - y;
            for (int x = min; x <= max; x++) {
                for (int z = min; z <= max; z++) {
                    boolean chamber = y <= 3 && x >= 4 && x <= 10 && z >= 4 && z <= 10;
                    if (chamber) {
                        continue;
                    }
                    if (min == max) {
                        canvas.set(x, y, z, palette.accent());
                    } else if ((x == min || x == max) && (z == min || z == max)) {
                        canvas.set(x, y, z, palette.wall());
                    } else if (z == min) {
                        canvas.set(x, y, z, palette.stairs("south", false));
                    } else if (z == max) {
                        canvas.set(x, y, z, palette.stairs("north", false));
                    } else if (x == min) {
                        canvas.set(x, y, z, palette.stairs("east", false));
                    } else if (x == max) {
                        canvas.set(x, y, z, palette.stairs("west", false));
                    } else {
                        canvas.set(x, y, z, y == 3 ? palette.accent2() : palette.wall());
                    }
                }
            }
        }
        canvas.air(6, 1, 1, 8, 3, 3);
        for (int z = 1; z <= 3; z++) {
            canvas.fill(5, 1, z, 5, 3, z, palette.pillar());
            canvas.fill(9, 1, z, 9, 3, z, palette.pillar());
        }
        for (int i = 4; i <= 10; i++) {
            canvas.set(i, 2, 3, i >= 6 && i <= 8 ? MythicCanvas.AIR : palette.ceiling());
            canvas.set(3, 2, i, palette.ceiling()).set(11, 2, i, palette.ceiling()).set(i, 2, 11, palette.ceiling());
        }
        canvas.set(3, 2, 7, palette.light()).set(11, 2, 7, palette.light()).set(7, 2, 11, palette.light());
        for (int x : new int[]{0, 14}) {
            canvas.fill(x, 1, 0, x, 4, 0, palette.pillar());
            canvas.set(x, 5, 0, palette.accent());
        }
    }

    /** Mesopotamian: three-tier mud-brick ziggurat with lapis bands and a rooftop shrine. */
    private static void ziggurat(MythicCanvas canvas) {
        MythicDungeonTheme.Palette palette = canvas.palette();
        tier(canvas, 1, 4, 1, 13, palette.wallAlt());
        canvas.air(2, 1, 2, 12, 3, 12);
        tier(canvas, 5, 7, 3, 11, palette.wallAlt());
        tier(canvas, 8, 10, 5, 9, palette.wallAlt());
        canvas.air(6, 8, 6, 8, 9, 8);
        canvas.air(7, 8, 9, 7, 9, 9);
        for (int i = 1; i <= 13; i++) {
            canvas.set(i, 3, 1, palette.accent()).set(i, 3, 13, palette.accent());
            canvas.set(1, 3, i, palette.accent()).set(13, 3, i, palette.accent());
        }
        for (int i = 3; i <= 11; i++) {
            canvas.set(i, 7, 3, palette.accent2()).set(i, 7, 11, palette.accent2());
            canvas.set(3, 7, i, palette.accent2()).set(11, 7, i, palette.accent2());
        }
        canvas.set(7, 11, 7, palette.accent()).set(7, 12, 7, palette.light());
        canvas.air(6, 1, 1, 8, 3, 1);
        canvas.fill(5, 1, 1, 5, 4, 1, palette.accent()).fill(9, 1, 1, 9, 4, 1, palette.accent());
        for (int i = 2; i <= 12; i += 5) {
            canvas.set(1, 2, i, palette.light()).set(13, 2, i, palette.light());
        }
        for (int x = 2; x <= 12; x += 2) {
            canvas.set(x, 1, 2, palette.pillar()).set(x, 1, 12, palette.pillar());
        }
    }

    /** Solid stepped tier with buttress stripes on its faces. */
    private static void tier(MythicCanvas canvas, int y1, int y2, int min, int max, BlockStateSpec core) {
        for (int y = y1; y <= y2; y++) {
            for (int x = min; x <= max; x++) {
                for (int z = min; z <= max; z++) {
                    boolean face = x == min || x == max || z == min || z == max;
                    boolean stripe = face && (x + z) % 3 == 0;
                    canvas.set(x, y, z, face ? (stripe ? canvas.palette().wallAlt() : canvas.palette().wall()) : core);
                }
            }
        }
    }

    /** Japanese: vermilion torii, stone lanterns and a shrine with a stepped hip roof. */
    private static void toriiShrine(MythicCanvas canvas) {
        MythicDungeonTheme.Palette palette = canvas.palette();
        for (int x = 0; x < FOOTPRINT; x++) {
            for (int z = 0; z < FOOTPRINT; z++) {
                boolean path = x >= 6 && x <= 8;
                canvas.set(x, 0, z, path ? palette.wall() : block("minecraft:moss_block"));
            }
        }
        for (int x : new int[]{3, 11}) {
            canvas.fill(x, 1, 1, x, 6, 1, palette.accent());
        }
        canvas.fill(4, 5, 1, 10, 5, 1, palette.accent());
        canvas.fill(2, 7, 1, 12, 7, 1, palette.accent());
        canvas.fill(1, 8, 1, 13, 8, 1, palette.slab("bottom"));
        canvas.set(7, 6, 1, palette.floorAlt());
        for (int x : new int[]{5, 9}) {
            canvas.set(x, 1, 2, block("minecraft:stone_bricks"));
            canvas.set(x, 2, 2, BlockStateSpec.of("minecraft:lantern", Map.of("hanging", "false")));
        }
        for (int x = 3; x <= 11; x++) {
            for (int z = 4; z <= 13; z++) {
                boolean wall = x == 3 || x == 11 || z == 4 || z == 13;
                if (!wall) {
                    continue;
                }
                boolean post = (x == 3 || x == 11) && (z == 4 || z == 13 || z == 8);
                for (int y = 1; y <= 4; y++) {
                    canvas.set(x, y, z, post ? palette.pillar() : (y == 1 ? palette.floorAlt() : palette.floor()));
                }
            }
        }
        canvas.air(6, 1, 4, 8, 3, 4);
        for (int x = 4; x <= 10; x++) {
            for (int z = 5; z <= 12; z++) {
                canvas.set(x, 5, z, palette.ceiling());
            }
        }
        canvas.set(7, 4, 9, palette.hangingLight()).set(5, 4, 11, palette.hangingLight()).set(9, 4, 11, palette.hangingLight());
        int[][] rings = {{2, 12, 3, 14}, {3, 11, 4, 13}, {4, 10, 5, 12}, {5, 9, 6, 11}, {6, 8, 7, 10}};
        for (int i = 0; i < rings.length; i++) {
            int[] ring = rings[i];
            int y = 5 + i;
            for (int x = ring[0]; x <= ring[1]; x++) {
                for (int z = ring[2]; z <= ring[3]; z++) {
                    boolean westOrEast = x == ring[0] || x == ring[1];
                    boolean northOrSouth = z == ring[2] || z == ring[3];
                    if (westOrEast && northOrSouth) {
                        canvas.set(x, y, z, palette.roof());
                    } else if (z == ring[2]) {
                        canvas.set(x, y, z, palette.roofStairs("south", false));
                    } else if (z == ring[3]) {
                        canvas.set(x, y, z, palette.roofStairs("north", false));
                    } else if (x == ring[0]) {
                        canvas.set(x, y, z, palette.roofStairs("east", false));
                    } else if (x == ring[1]) {
                        canvas.set(x, y, z, palette.roofStairs("west", false));
                    }
                }
            }
        }
        canvas.fill(7, 10, 8, 7, 10, 9, palette.slab("bottom"));
        canvas.set(5, 0, 8, palette.light()).set(9, 0, 8, palette.light());
    }

    /** Mesoamerican: steep stepped pyramid with a summit temple, gold crown and hanging vines. */
    private static void steppedPyramid(MythicCanvas canvas) {
        MythicDungeonTheme.Palette palette = canvas.palette();
        tier(canvas, 1, 4, 1, 13, palette.wallAlt());
        canvas.air(3, 1, 3, 11, 3, 11);
        tier(canvas, 5, 7, 3, 11, palette.wallAlt());
        tier(canvas, 8, 10, 5, 9, palette.wallAlt());
        for (int x = 5; x <= 9; x++) {
            for (int z = 5; z <= 9; z++) {
                boolean wall = x == 5 || x == 9 || z == 5 || z == 9;
                canvas.fill(x, 11, z, x, 12, z, wall ? palette.pillar() : MythicCanvas.AIR);
                canvas.set(x, 13, z, palette.wall());
            }
        }
        canvas.air(7, 11, 5, 7, 12, 5);
        canvas.set(7, 14, 7, palette.accent());
        canvas.set(5, 14, 5, palette.accent2()).set(9, 14, 5, palette.accent2());
        canvas.set(5, 14, 9, palette.accent2()).set(9, 14, 9, palette.accent2());
        trimTier(canvas, 4, 1, 13);
        trimTier(canvas, 7, 3, 11);
        trimTier(canvas, 10, 5, 9);
        canvas.air(6, 1, 1, 8, 3, 2);
        canvas.set(6, 4, 1, palette.accent()).set(8, 4, 1, palette.accent());
        for (int i = 2; i <= 12; i += 2) {
            if (i < 5 || i > 9) {
                vineColumn(canvas, i, 0, "south", 3);
            }
            vineColumn(canvas, i, 14, "north", 3);
            vineColumn(canvas, 0, i, "east", 3);
            vineColumn(canvas, 14, i, "west", 3);
        }
        canvas.set(2, 2, 7, palette.light()).set(12, 2, 7, palette.light()).set(7, 2, 12, palette.light());
    }

    private static void trimTier(MythicCanvas canvas, int y, int min, int max) {
        for (int i = min + 1; i < max; i++) {
            canvas.set(i, y, min, canvas.palette().stairs("south", false));
            canvas.set(i, y, max, canvas.palette().stairs("north", false));
            canvas.set(min, y, i, canvas.palette().stairs("east", false));
            canvas.set(max, y, i, canvas.palette().stairs("west", false));
        }
    }

    /** Vines hanging on a wall face; {@code side} is the direction of the supporting wall. */
    private static void vineColumn(MythicCanvas canvas, int x, int z, String side, int height) {
        BlockStateSpec vine = BlockStateSpec.of("minecraft:vine", Map.of(side, "true"));
        for (int y = 1; y <= height; y++) {
            canvas.set(x, y, z, vine);
        }
    }

    /** Arabian: arched sandstone pavilion with a copper dome and four minarets. */
    private static void domedPavilion(MythicCanvas canvas) {
        MythicDungeonTheme.Palette palette = canvas.palette();
        for (int x = 2; x <= 12; x++) {
            for (int z = 2; z <= 12; z++) {
                boolean wall = x == 2 || x == 12 || z == 2 || z == 12;
                for (int y = 1; y <= 6; y++) {
                    if (wall) {
                        canvas.set(x, y, z, y == 6 ? palette.pillar() : canvas.wallBlock());
                    }
                }
                canvas.set(x, 7, z, palette.ceiling());
                if (wall && (x + z) % 2 == 0) {
                    canvas.set(x, 8, z, palette.wall());
                }
            }
        }
        canvas.air(5, 7, 5, 9, 7, 9);
        for (int i = 6; i <= 8; i++) {
            canvas.air(i, 1, 2, i, 3, 2).air(i, 1, 12, i, 3, 12).air(2, 1, i, 2, 3, i).air(12, 1, i, 12, 3, i);
        }
        canvas.set(6, 4, 2, palette.stairs("east", true)).set(8, 4, 2, palette.stairs("west", true)).set(7, 4, 2, MythicCanvas.AIR);
        canvas.set(6, 4, 12, palette.stairs("east", true)).set(8, 4, 12, palette.stairs("west", true)).set(7, 4, 12, MythicCanvas.AIR);
        canvas.set(2, 4, 6, palette.stairs("south", true)).set(2, 4, 8, palette.stairs("north", true)).set(2, 4, 7, MythicCanvas.AIR);
        canvas.set(12, 4, 6, palette.stairs("south", true)).set(12, 4, 8, palette.stairs("north", true)).set(12, 4, 7, MythicCanvas.AIR);
        canvas.set(7, 5, 2, palette.accent2()).set(7, 5, 12, palette.accent2()).set(2, 5, 7, palette.accent2()).set(12, 5, 7, palette.accent2());
        double radius = 4.5;
        int domeTop = 7;
        for (int x = 2; x <= 12; x++) {
            for (int z = 2; z <= 12; z++) {
                for (int y = 8; y <= 12; y++) {
                    double distance = Math.sqrt((x - 7) * (x - 7) + (y - 7.5) * (y - 7.5) + (z - 7) * (z - 7));
                    if (distance <= radius && distance > radius - 1.2) {
                        canvas.set(x, y, z, palette.roof());
                        domeTop = Math.max(domeTop, x == 7 && z == 7 ? y : domeTop);
                    } else if (distance <= radius - 1.2) {
                        canvas.set(x, y, z, MythicCanvas.AIR);
                    }
                }
            }
        }
        canvas.set(7, domeTop + 1, 7, BlockStateSpec.of("minecraft:lightning_rod", Map.of("facing", "up")));
        for (int x : new int[]{1, 13}) {
            for (int z : new int[]{1, 13}) {
                for (int y = 1; y <= 10; y++) {
                    canvas.set(x, y, z, y % 4 == 0 ? palette.pillar() : palette.wall());
                }
                canvas.set(x, 11, z, palette.roof());
                canvas.set(x, 12, z, BlockStateSpec.of("minecraft:lightning_rod", Map.of("facing", "up")));
            }
        }
        canvas.set(4, 6, 4, palette.hangingLight()).set(10, 6, 4, palette.hangingLight());
        canvas.set(4, 6, 10, palette.hangingLight()).set(10, 6, 10, palette.hangingLight());
        for (int x = 3; x <= 11; x++) {
            for (int z = 3; z <= 11; z++) {
                if ((x + z) % 4 == 0) {
                    canvas.set(x, 0, z, palette.accent2());
                }
            }
        }
    }

    /** Andean: rocky "Salamanca" mound with a cave mouth and an amethyst-lit grotto. */
    private static void caveMouth(MythicCanvas canvas) {
        MythicDungeonTheme.Palette palette = canvas.palette();
        Random random = canvas.random();
        BlockStateSpec[] rock = {palette.wall(), palette.wallAlt(), block("minecraft:stone"), block("minecraft:tuff")};
        for (int x = 0; x < FOOTPRINT; x++) {
            for (int z = 0; z < FOOTPRINT; z++) {
                double distance = Math.hypot(x - 7, z - 7);
                int height = (int) Math.round(9.5 - distance * 0.7) + random.nextInt(2);
                height = Math.max(1, Math.min(10, height));
                for (int y = 1; y <= height; y++) {
                    BlockStateSpec state = y == height
                        ? (random.nextBoolean() ? palette.floorAlt() : palette.floor())
                        : rock[random.nextInt(rock.length)];
                    canvas.set(x, y, z, state);
                }
            }
        }
        for (int x = 3; x <= 11; x++) {
            for (int z = 3; z <= 11; z++) {
                for (int y = 1; y <= 5; y++) {
                    double dx = (x - 7) / 4.6;
                    double dz = (z - 7) / 4.6;
                    double dy = (y - 0.5) / 5.0;
                    if (dx * dx + dz * dz + dy * dy <= 1.0) {
                        canvas.set(x, y, z, MythicCanvas.AIR);
                    }
                }
            }
        }
        for (int z = 0; z <= 4; z++) {
            canvas.air(6, 1, z, 8, 3, z);
        }
        canvas.set(5, 3, 0, palette.accent()).set(9, 3, 0, palette.accent()).set(7, 4, 0, palette.accent());
        for (int[] spot : new int[][]{{4, 9}, {10, 9}, {4, 5}, {10, 5}, {7, 10}}) {
            canvas.set(spot[0], 1, spot[1], palette.clutter());
            canvas.set(spot[0], 0, spot[1], palette.accent());
        }
        canvas.set(7, 5, 7, palette.light()).set(5, 4, 9, palette.light()).set(9, 4, 9, palette.light());
        canvas.set(6, 1, 10, block("minecraft:moss_carpet")).set(8, 1, 10, block("minecraft:moss_carpet"));
    }
}
