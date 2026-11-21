package dev.linqfy.bigCasares.modules.alexscaves.core.worldgen;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModGenerationTest {
    @Test
    void oreVeinGrowsWithSizeAndVisitsEachBlockOnce() {
        Set<Long> small = new HashSet<>();
        Set<Long> large = new HashSet<>();
        int[] visits = new int[1];
        VanillaOre.place(new SplittableRandom(3), 0, 0, 0, 14, (x, y, z) -> {
            visits[0]++;
            small.add(key(x, y, z));
        });
        assertEquals(small.size(), visits[0], "no block twice");
        VanillaOre.place(new SplittableRandom(3), 0, 0, 0, 64, (x, y, z) -> large.add(key(x, y, z)));
        assertTrue(small.size() > 3, "a real vein: " + small.size());
        assertTrue(large.size() > small.size() * 2, "size 64 patches are much bigger: " + large.size() + " vs " + small.size());
        for (long k : large) {
            int x = (int) (k >> 40);
            assertTrue(Math.abs(x) <= 16, "stays near the origin");
        }
    }

    @Test
    void placementHeightsStayInRange() {
        SplittableRandom random = new SplittableRandom(9);
        int middle = 0;
        for (int i = 0; i < 5000; i++) {
            int uniform = ModPlacement.uniform(random, -64, 256);
            assertTrue(uniform >= -64 && uniform <= 256);
            int trapezoid = ModPlacement.trapezoid(random, -100, 100);
            assertTrue(trapezoid >= -100 && trapezoid <= 100);
            if (Math.abs(trapezoid) < 50) {
                middle++;
            }
        }
        assertTrue(middle > 3000, "trapezoid peaks in the middle: " + middle);
        assertTrue(ModPlacement.rarity(random, 1));
    }

    @Test
    void lakeShapeLeavesTheTopHalfForAirAndHasABorder() {
        boolean[] grid = LakeFeature.shape(new SplittableRandom(5));
        int cells = 0;
        int borders = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 8; y++) {
                    if (grid[LakeFeature.index(x, y, z)]) {
                        cells++;
                        assertTrue(x > 0 && x < 15 && z > 0 && z < 15 && y > 0 && y < 7, "inside the box");
                    } else if (LakeFeature.border(grid, x, y, z)) {
                        borders++;
                    }
                }
            }
        }
        assertTrue(cells > 20, "a lake: " + cells);
        assertTrue(borders > cells / 2, "walled all round: " + borders);
        assertFalse(LakeFeature.border(grid, 0, 0, 0) && grid[LakeFeature.index(0, 0, 0)]);
    }

    @Test
    void rotatedTemplatesKeepTheirFootprint() {
        // Vanilla getZeroPositionWithTransform for a 9 x 5 template (last indices 8 and 4).
        assertEquals(java.util.List.of(10, 20), toList(RuinsFeature.zeroCorner(10, 20, 8, 4,
            org.bukkit.block.structure.StructureRotation.NONE)));
        assertEquals(java.util.List.of(14, 20), toList(RuinsFeature.zeroCorner(10, 20, 8, 4,
            org.bukkit.block.structure.StructureRotation.CLOCKWISE_90)));
        assertEquals(java.util.List.of(18, 24), toList(RuinsFeature.zeroCorner(10, 20, 8, 4,
            org.bukkit.block.structure.StructureRotation.CLOCKWISE_180)));
        assertEquals(java.util.List.of(10, 28), toList(RuinsFeature.zeroCorner(10, 20, 8, 4,
            org.bukkit.block.structure.StructureRotation.COUNTERCLOCKWISE_90)));
    }

    @Test
    void rotatedBlocksLandInsideTheFootprint() {
        int sizeX = 9;
        int sizeZ = 5;
        for (org.bukkit.block.structure.StructureRotation rotation : org.bukkit.block.structure.StructureRotation.values()) {
            boolean quarter = rotation == org.bukkit.block.structure.StructureRotation.CLOCKWISE_90
                || rotation == org.bukkit.block.structure.StructureRotation.COUNTERCLOCKWISE_90;
            int footX = quarter ? sizeZ : sizeX;
            int footZ = quarter ? sizeX : sizeZ;
            int[] zero = RuinsFeature.zeroCorner(100, 200, sizeX - 1, sizeZ - 1, rotation);
            for (int x = 0; x < sizeX; x++) {
                for (int z = 0; z < sizeZ; z++) {
                    int[] at = StructureRotations.rotate(x, z, rotation);
                    int bx = zero[0] + at[0];
                    int bz = zero[1] + at[1];
                    assertTrue(bx >= 100 && bx < 100 + footX && bz >= 200 && bz < 200 + footZ, rotation + " " + x + "," + z);
                }
            }
        }
    }

    @Test
    void convertedTemplatesShipWithTheJar() throws java.io.IOException {
        for (String group : new String[] {"magnetic_ruins", "toxic_ruins", "caveman_house"}) {
            String index = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Path.of("src/main/resources/alexscaves/structures/index.json")));
            assertTrue(index.contains(group + "_0"), group);
            assertTrue(java.nio.file.Files.exists(java.nio.file.Path.of(
                "src/main/resources/alexscaves/datapack/data/alexscaves/loot_table/chests/" + group + ".json")), group);
        }
    }

    private static java.util.List<Integer> toList(int[] values) {
        return java.util.List.of(values[0], values[1]);
    }

    private static long key(int x, int y, int z) {
        return ((long) x << 40) ^ ((long) (y & 0xFFFFF) << 20) ^ (z & 0xFFFFF);
    }
}
