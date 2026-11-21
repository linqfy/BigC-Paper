package dev.linqfy.bigCasares.modules.alexscaves.primordial;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrimordialFeaturesTest {
    private static final int SURFACE = 70;
    private static final int MIN_Y = -64;

    @Test
    void onlyRockDirtAndOresAreCarved() {
        assertTrue(PrimordialFeatures.Cavern.carvable(Material.STONE));
        assertTrue(PrimordialFeatures.Cavern.carvable(Material.DEEPSLATE_IRON_ORE));
        assertFalse(PrimordialFeatures.Cavern.carvable(Material.WATER));
        assertFalse(PrimordialFeatures.Cavern.carvable(Material.LAVA));
        assertFalse(PrimordialFeatures.Cavern.carvable(Material.BEDROCK));
        assertFalse(PrimordialFeatures.Cavern.carvable(Material.SPAWNER));
    }

    @Test
    void treesOnlyGrowOnSoil() {
        assertTrue(PrimordialFeatures.isSoil(Material.GRASS_BLOCK));
        assertTrue(PrimordialFeatures.isSoil(Material.PACKED_MUD));
        assertFalse(PrimordialFeatures.isSoil(Material.SAND));
        assertFalse(PrimordialFeatures.isSoil(Material.NOTE_BLOCK), "not on limestone");
    }

    @Test
    void branchDirectionsAreTheEightNeighbours() {
        Set<String> seen = new HashSet<>();
        for (int[] d : PrimordialFeatures.BRANCH_DIRECTIONS) {
            assertTrue(Math.abs(d[0]) <= 1 && Math.abs(d[1]) <= 1 && (d[0] != 0 || d[1] != 0));
            seen.add(d[0] + "," + d[1]);
        }
        assertEquals(8, seen.size());
    }

    @Test
    void sandstoneBandsRepeatEveryTwentyBlocks() {
        assertTrue(PrimordialFeatures.Terrain.isBand(42), "mod band at 62 - 20");
        assertTrue(PrimordialFeatures.Terrain.isBand(2) && PrimordialFeatures.Terrain.isBand(3), "third band is two thick");
        assertFalse(PrimordialFeatures.Terrain.isBand(0));
        assertTrue(PrimordialFeatures.Terrain.isBand(-18));
        assertFalse(PrimordialFeatures.Terrain.isBand(7));
    }

    @Test
    void volcanoSizesAndCurveFollowTheMod() {
        java.util.Random random = new java.util.Random(4);
        for (int i = 0; i < 200; i++) {
            Volcano.Cone cone = Volcano.cone(0, 0, random);
            assertTrue(cone.radius() >= 30 && cone.radius() < 45);
            assertTrue(cone.height() >= cone.radius() + 8 && cone.height() < cone.radius() + 13);
        }
        assertTrue(Volcano.Cone.curve(1.0) > Volcano.Cone.curve(0.5), "highest at the centre");
        assertTrue(Volcano.Cone.curve(0.0) < 0.05, "flat at the rim");
    }
}
