package dev.linqfy.bigCasares.modules.mythicdungeons.worldgen;

import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.dimension.datapack.GeneratedDatapack;
import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;
import dev.linqfy.bigCasares.dimension.worldgen.StructureTemplate;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicDungeonTheme.Layout;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicExtraPiece.Role;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicJigsawSimulator.Placed;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicPassability.Voxels;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicTemplateRewriter.Jigsaw;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MythicJigsawAssemblyTest {

    static final int SURFACE_Y = 62;
    static final int WORLD_MIN_Y = -64;
    static final int SEEDS = 64;
    /** Every n-th seed is also walked on foot from the entrance to the altar. */
    static final int WALK_EVERY = 4;
    static final int MIN_MEDIAN_PIECES = 20;
    static final int MAX_MEDIAN_CHESTS = 6;
    static final int MIN_CHESTS = 2;
    static final int MIN_MEDIAN_SPAWNERS = 6;

    private static GeneratedDatapack pack;

    @BeforeAll
    static void build() {
        pack = MythicDungeonDatapack.build();
    }

    @AfterAll
    static void release() {
        pack = null;
    }

    /** What a culture's simulated dungeons look like; also used for the third-party pack. */
    record Stats(int medianPieces, int minPieces, int maxPieces, int medianRadius, int medianChests,
                 int medianSpawners, Set<String> used) {
        @Override
        public String toString() {
            return "pieces median " + medianPieces + " (" + minPieces + ".." + maxPieces + "), radius median " + medianRadius
                + ", chests median " + medianChests + ", spawners median " + medianSpawners + ", templates used " + used.size();
        }
    }

    @Test
    void everyCultureReachesItsArenaAndSprawls() {
        for (MythicDungeonTheme theme : MythicDungeonTheme.all()) {
            MythicPackIndex index = new MythicPackIndex(pack, theme.culture());
            Stats stats = checkCulture(index, theme);
            assertTrue(stats.medianPieces() >= MIN_MEDIAN_PIECES, theme.culture() + " " + stats);
        }
    }

    /**
     * Simulates {@link #SEEDS} dungeons of one culture and checks every rule of the assembled layout:
     * exactly one arena, no overlaps, family heights, distance, chest/spawner counts, connections open on
     * both sides and (every {@link #WALK_EVERY}th seed) the altar reachable on foot from the entrance.
     */
    static Stats checkCulture(MythicPackIndex index, MythicDungeonTheme theme) {
        Layout layout = theme.layout();
        MythicJigsawSimulator simulator = new MythicJigsawSimulator(index, MythicDungeonDatapack.JIGSAW_SIZE,
            layout.maxDistanceFromCenter());
        Map<StructureTemplate, int[]> counts = new IdentityHashMap<>();
        Map<StructureTemplate, Voxels> voxels = MythicDungeonWorld.cache();
        List<Integer> pieceCounts = new ArrayList<>();
        List<Integer> radii = new ArrayList<>();
        List<Integer> chestCounts = new ArrayList<>();
        List<Integer> spawnerCounts = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (long seed = 0; seed < SEEDS; seed++) {
            MythicJigsawSimulator.Result result = simulator.run(theme.id("start").asString(), seed, SURFACE_Y, WORLD_MIN_Y);
            List<Placed> pieces = result.pieces();
            String where = theme.culture() + " seed " + seed;
            List<String> paths = pieces.stream().map(Placed::path).toList();
            used.addAll(paths);
            assertEquals(1, paths.stream().filter(path -> path.endsWith("/pieces/arena")).count(), where + " " + paths);
            for (int a = 0; a < pieces.size(); a++) {
                for (int b = a + 1; b < pieces.size(); b++) {
                    assertFalse(pieces.get(a).box().intersects(pieces.get(b).box()),
                        where + " overlap " + pieces.get(a).path() + " / " + pieces.get(b).path());
                }
            }
            Placed start = pieces.getFirst();
            int radius = 0;
            int chests = 0;
            int spawners = 0;
            for (Placed piece : pieces) {
                MythicJigsawSimulator.Box box = piece.box();
                int reach = Math.max(Math.max(Math.abs(box.minX() - result.startCentreX()), Math.abs(box.maxX() - result.startCentreX())),
                    Math.max(Math.abs(box.minZ() - result.startCentreZ()), Math.abs(box.maxZ() - result.startCentreZ())));
                radius = Math.max(radius, reach);
                assertTrue(reach <= layout.maxDistanceFromCenter(), where + " " + piece.path() + " is " + reach + " from the start");
                int[] count = counts.computeIfAbsent(piece.template(), MythicJigsawAssemblyTest::blockCounts);
                chests += count[0];
                spawners += count[1];
                if (piece == start) {
                    continue;
                }
                if (layout == Layout.UNDERGROUND) {
                    assertTrue(box.maxY() < SURFACE_Y, where + " " + piece.path() + " reaches y " + box.maxY());
                } else {
                    int walking = SURFACE_Y + MythicSurfacePieces.STAND;
                    for (Jigsaw jigsaw : simulator.jigsaws(piece.template())) {
                        if (jigsaw.horizontal() && !(jigsaw.name().equals("minecraft:empty") && jigsaw.pool().equals("minecraft:empty"))) {
                            assertEquals(walking, piece.world(jigsaw.x(), jigsaw.y(), jigsaw.z())[1],
                                where + " " + piece.path() + " doorway off the common walking level");
                        }
                    }
                    if (piece.path().endsWith("/pieces/arena")) {
                        assertEquals(walking - MythicPieces.ARENA_ENTRY_STAND, box.minY(), where + " sunken great hall floor");
                    }
                }
            }
            assertTrue(chests >= MIN_CHESTS, where + " chests " + chests);
            // seals are one-block plugs, not pieces of the dungeon
            pieceCounts.add((int) pieces.stream().filter(piece -> !piece.path().endsWith("/pieces/seal")).count());
            radii.add(radius);
            chestCounts.add(chests);
            spawnerCounts.add(spawners);
            if (seed % WALK_EVERY == 0) {
                MythicDungeonWorld world = new MythicDungeonWorld(pieces, voxels);
                for (Placed piece : pieces) {
                    if (piece.parentCell() == null || piece.path().endsWith("/pieces/seal")) {
                        // seals are the fallback of a doorway that got no piece: they close it on purpose
                        continue;
                    }
                    for (int[] cell : List.of(piece.parentCell(), piece.childCell())) {
                        if (cell[1] == piece.parentCell()[1] && cell[1] == piece.childCell()[1]) {
                            for (int dy = 0; dy < 3; dy++) {
                                assertTrue(MythicPassability.passable(world.at(cell[0], cell[1] + dy, cell[2])),
                                    where + " connection of " + piece.path() + " blocked at " + cell[0] + "," + (cell[1] + dy)
                                        + "," + cell[2]);
                            }
                        }
                    }
                }
                Set<Long> reached = world.walkFromEntrance(result.limit());
                int[] altar = world.altar();
                assertTrue(MythicPassability.besideReached(reached, altar[0], altar[1], altar[2]),
                    where + ": the altar cannot be reached on foot from the entrance (" + reached.size() + " cells walked)");
            }
        }
        Stats stats = new Stats(median(pieceCounts), Collections.min(pieceCounts), Collections.max(pieceCounts),
            median(radii), median(chestCounts), median(spawnerCounts), used);
        assertTrue(stats.medianChests() <= MAX_MEDIAN_CHESTS, theme.culture() + " " + stats);
        assertTrue(stats.medianSpawners() >= MIN_MEDIAN_SPAWNERS, theme.culture() + " " + stats);
        System.out.println("[mythic-dungeons] " + theme.culture() + " " + stats);
        return stats;
    }

    static int median(List<Integer> values) {
        List<Integer> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return sorted.get(sorted.size() / 2);
    }

    /** Chests and spawners in a template. */
    static int[] blockCounts(StructureTemplate template) {
        Voxels voxels = Voxels.of(template);
        int chests = 0;
        int spawners = 0;
        for (int x = 0; x < voxels.sizeX(); x++) {
            for (int y = 0; y < voxels.sizeY(); y++) {
                for (int z = 0; z < voxels.sizeZ(); z++) {
                    String name = voxels.name(x, y, z);
                    if ("minecraft:chest".equals(name)) {
                        chests++;
                    } else if ("minecraft:spawner".equals(name)) {
                        spawners++;
                    }
                }
            }
        }
        return new int[]{chests, spawners};
    }

    @Test
    void extraPiecesMergeAndKeepTheArenaReachable() {
        MythicTemplateSource source = culture -> culture.equals("greek") || culture.equals("norse") ? fakePieces() : List.of();
        GeneratedDatapack extended = MythicDungeonDatapack.build(List.of(source));
        assertNotEquals(MythicDungeonDatapack.build().contentHash(), extended.contentHash());
        assertEquals(extended.contentHash(), MythicDungeonDatapack.build(List.of(source)).contentHash());

        MythicPackIndex greek = new MythicPackIndex(extended, "greek");
        Set<String> greekExtras = greek.templates.keySet().stream().filter(id -> id.contains("/extra/")).collect(Collectors.toSet());
        // Surface family: no descent starts; the complete building becomes a wing building.
        assertEquals(Set.of("bigc_myth:greek/extra/side_room/fake_room", "bigc_myth:greek/extra/corridor/fake_corridor",
            "bigc_myth:greek/extra/wing_building/fake_castle"), greekExtras);
        MythicPackIndex norse = new MythicPackIndex(extended, "norse");
        Set<String> norseExtras = norse.templates.keySet().stream().filter(id -> id.contains("/extra/")).collect(Collectors.toSet());
        assertEquals(Set.of("bigc_myth:norse/extra/side_room/fake_room", "bigc_myth:norse/extra/corridor/fake_corridor",
            "bigc_myth:norse/extra/entrance/fake_entrance", "bigc_myth:norse/extra/full_dungeon/fake_castle"), norseExtras);
        assertTrue(new MythicPackIndex(extended, "medieval").templates.keySet().stream().noneMatch(id -> id.contains("/extra/")));

        int fullDungeons = 0;
        int extrasUsed = 0;
        for (MythicDungeonTheme theme : List.of(MythicDungeonTheme.GREEK, MythicDungeonTheme.NORSE)) {
            MythicPackIndex index = theme == MythicDungeonTheme.GREEK ? greek : norse;
            MythicJigsawSimulator simulator = new MythicJigsawSimulator(index, MythicDungeonDatapack.JIGSAW_SIZE,
                theme.layout().maxDistanceFromCenter());
            for (long seed = 0; seed < 80; seed++) {
                List<Placed> pieces = simulator.assemble(theme.id("start").asString(), seed, SURFACE_Y, WORLD_MIN_Y);
                List<String> paths = pieces.stream().map(Placed::path).toList();
                assertEquals(1, paths.stream().filter(path -> path.endsWith("/pieces/arena")).count(), theme.culture() + " seed " + seed + " " + paths);
                for (int a = 0; a < pieces.size(); a++) {
                    for (int b = a + 1; b < pieces.size(); b++) {
                        assertFalse(pieces.get(a).box().intersects(pieces.get(b).box()), theme.culture() + " seed " + seed);
                    }
                }
                if (paths.getFirst().contains("/extra/full_dungeon/")) {
                    fullDungeons++;
                }
                extrasUsed += (int) paths.stream().filter(path -> path.contains("/extra/")).count();
            }
        }
        assertTrue(fullDungeons > 0 && extrasUsed > 0, "extras were exercised: " + fullDungeons + " " + extrasUsed);
    }

    @Test
    void aFullDungeonDescentAtTheEdgeStillReachesTheArena() {
        // The descent as far from the centre as the contract allows: the underground spine must still fit in 128.
        StructureTemplate castle = withJigsaws("edge_castle", 48, 8, 48,
            new String[][]{{"fake:down", "23", "0", "39", "down_south"}, {"fake:gate", "23", "1", "0", "north_up"}});
        StructureTemplate tooFar = withJigsaws("far_castle", 48, 8, 48, new String[][]{{"fake:down", "23", "0", "40", "down_south"}});
        assertThrows(IllegalArgumentException.class, () -> MythicDungeonDatapack.build(List.of(culture -> List.of(
            new MythicExtraPiece("far_castle", tooFar, Role.FULL_DUNGEON, "fake:down", null, 1)))));
        MythicExtraPiece piece = new MythicExtraPiece("edge_castle", castle, Role.FULL_DUNGEON, "fake:down", null, 150);
        GeneratedDatapack extended = MythicDungeonDatapack.build(List.of(culture -> culture.equals("andean") ? List.of(piece) : List.of()));
        MythicPackIndex index = new MythicPackIndex(extended, "andean");
        MythicJigsawSimulator simulator = new MythicJigsawSimulator(index, MythicDungeonDatapack.JIGSAW_SIZE,
            MythicDungeonTheme.ANDEAN.layout().maxDistanceFromCenter());
        int used = 0;
        for (long seed = 0; seed < 40; seed++) {
            List<Placed> pieces = simulator.assemble("bigc_myth:andean/start", seed, SURFACE_Y, WORLD_MIN_Y);
            if (pieces.getFirst().path().contains("edge_castle")) {
                used++;
                assertEquals(1, pieces.stream().filter(p -> p.path().endsWith("/pieces/arena")).count(), "seed " + seed);
            }
        }
        assertTrue(used > 0);
    }

    @Test
    void invalidExtraPiecesAreRejectedAndSkippedByTheInstaller() {
        MythicCanvas canvas = new MythicCanvas(NamespacedId.of("fake", "modded"), MythicDungeonTheme.GREEK, 5, 5, 5);
        canvas.set(2, 2, 2, BlockStateSpec.of("othermod:magic_block"));
        canvas.jigsaw(2, 1, 0, "north_up", NamespacedId.of("fake", "entry"), MythicPieces.EMPTY, MythicPieces.EMPTY, MythicCanvas.AIR, 0);
        MythicTemplateSource modded = culture -> List.of(new MythicExtraPiece("modded", canvas.build(), Role.SIDE_ROOM,
            "fake:entry", null, 5));
        assertThrows(IllegalArgumentException.class, () -> MythicDungeonDatapack.build(List.of(modded)));

        MythicTemplateSource tooWide = culture -> List.of(new MythicExtraPiece("wide", box("wide", 13, 6, 6,
            "fake:entry", 1, 1, 0, "north_up"), Role.SIDE_ROOM, "fake:entry", null, 5));
        assertThrows(IllegalArgumentException.class, () -> MythicDungeonDatapack.build(List.of(tooWide)));

        MythicTemplateSource arenaSized = culture -> List.of(new MythicExtraPiece("arena_sized", withJigsaws("arena_sized", 35, 6, 35,
            new String[][]{{"fake:down", "17", "0", "17", "down_south"}}), Role.FULL_DUNGEON, "fake:down", null, 5));
        assertThrows(IllegalArgumentException.class, () -> MythicDungeonDatapack.build(List.of(arenaSized)));

        MythicTemplateSource broken = culture -> {
            throw new IllegalStateException("cannot read templates");
        };
        MythicTemplateSource good = culture -> culture.equals("greek") ? fakePieces() : List.of();
        List<MythicTemplateSource> usable = MythicDatapackInstaller.usableSources(List.of(broken, modded, good),
            Logger.getAnonymousLogger());
        assertEquals(List.of(good), usable);
    }

    static List<MythicExtraPiece> fakePieces() {
        StructureTemplate room = box("fake_room", 9, 6, 7, "fake:entry", 4, 1, 0, "north_up");
        StructureTemplate corridor = withJigsaws("fake_corridor", 7, 7, 9,
            new String[][]{{"fake:in", "3", "1", "0", "north_up"}, {"fake:out", "3", "1", "8", "south_up"},
                {"fake:side", "0", "1", "4", "west_up"}});
        StructureTemplate entrance = withJigsaws("fake_entrance", 11, 8, 11,
            new String[][]{{"fake:down", "5", "0", "5", "down_south"}, {"fake:gate", "5", "1", "0", "north_up"}});
        StructureTemplate castle = withJigsaws("fake_castle", 31, 12, 31,
            new String[][]{{"fake:down", "15", "0", "15", "down_north"}, {"fake:gate", "0", "1", "15", "west_up"},
                {"fake:gate2", "30", "1", "15", "east_up"}});
        return List.of(
            new MythicExtraPiece("fake_room", room, Role.SIDE_ROOM, "fake:entry", null, 6),
            new MythicExtraPiece("fake_corridor", corridor, Role.CORRIDOR, "fake:in", "fake:out", 40),
            new MythicExtraPiece("fake_entrance", entrance, Role.SURFACE_ENTRANCE, "fake:down", null, 4),
            new MythicExtraPiece("fake_castle", castle, Role.FULL_DUNGEON, "fake:down", null, 4));
    }

    private static StructureTemplate box(String name, int sx, int sy, int sz, String jigsaw, int x, int y, int z, String orientation) {
        return withJigsaws(name, sx, sy, sz, new String[][]{{jigsaw, "" + x, "" + y, "" + z, orientation}});
    }

    /** A hollow stone box with open 3x4 doorways at its jigsaws carrying foreign pool names, as a third-party template would. */
    private static StructureTemplate withJigsaws(String name, int sx, int sy, int sz, String[][] jigsaws) {
        MythicCanvas canvas = new MythicCanvas(NamespacedId.of("fake", name), MythicDungeonTheme.GREEK, sx, sy, sz);
        canvas.fill(0, 0, 0, sx - 1, sy - 1, sz - 1, BlockStateSpec.of("minecraft:stone"));
        canvas.air(1, 1, 1, sx - 2, sy - 2, sz - 2);
        for (String[] jigsaw : jigsaws) {
            int x = Integer.parseInt(jigsaw[1]);
            int y = Integer.parseInt(jigsaw[2]);
            int z = Integer.parseInt(jigsaw[3]);
            if (!jigsaw[4].startsWith("down")) {
                boolean alongX = jigsaw[4].startsWith("north") || jigsaw[4].startsWith("south");
                canvas.air(alongX ? x - 1 : x, y, alongX ? z : z - 1, alongX ? x + 1 : x, Math.min(y + 3, sy - 1), alongX ? z : z + 1);
            }
            canvas.jigsaw(x, y, z, jigsaw[4], NamespacedId.parse(jigsaw[0]), NamespacedId.of("othermod", "target"),
                NamespacedId.of("othermod", "pool"), MythicCanvas.AIR, 0);
        }
        return canvas.build();
    }
}
