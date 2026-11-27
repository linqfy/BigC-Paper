package dev.linqfy.bigCasares.modules.alexscaves.candy;

import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBowl;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.ModVoronoi;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CandyStructuresTest {

    @Test
    void structureRandomIsTheVanillaLargeFeatureSeed() {
        long seed = 1234L;
        Random random = new Random(seed);
        long a = random.nextLong();
        long b = random.nextLong();
        Random expected = new Random(5L * a ^ -7L * b ^ seed);
        assertEquals(expected.nextInt(), CandyStructures.largeFeatureRandom(seed, 5, -7).nextInt());
    }

    @Test
    void donutSaltIsTheIntTheModReads() {
        // The mod's salt 22331293834 does not fit an int; 1.20.1 keeps its low 32 bits.
        assertEquals(856457354, (int) 22331293834L);
        List<int[]> full = CaveBowl.origins(42L, 0, 0, 30, 11, 7, 856457354L);
        for (int[] start : full) {
            int inCell = Math.floorMod(start[0], 11);
            assertTrue(inCell < 11 - 7, "random_spread: within spacing - separation");
        }
    }

    @Test
    void donutFrostingFacesTheModSides() {
        // North: frosted towards -x; east: +z; south: +x; west: -z.
        assertEquals(3.0F, CandyStructures.DonutArch.frostingDepth(0, 10, 10, 7, 0));
        assertEquals(3.0F, CandyStructures.DonutArch.frostingDepth(1, 10, 10, 0, 13));
        assertEquals(3.0F, CandyStructures.DonutArch.frostingDepth(2, 10, 10, 13, 0));
        assertEquals(3.0F, CandyStructures.DonutArch.frostingDepth(3, 10, 10, 0, 7));
    }

    @Test
    void sprinklesVoronoiIsDeterministicAndBounded() {
        List<Double> hashes = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            ModVoronoi.Info info = ModVoronoi.get3(99L, 1.0, i * 0.3, -i * 0.2, i * 0.7);
            assertTrue(info.distance() >= 0.0 && info.distance() < 2.0, "near a cell: " + info.distance());
            assertTrue(info.hash() >= -1.0 && info.hash() <= 1.0);
            hashes.add(info.hash());
        }
        assertEquals(hashes.get(17), ModVoronoi.get3(99L, 1.0, 17 * 0.3, -17 * 0.2, 17 * 0.7).hash());
    }

    @Test
    void roadBoxesShiftAlongTheirAxis() {
        CandyStructures.GingerbreadTown.Road road = new CandyStructures.GingerbreadTown.Road(0, 0, 30, 5, 1, List.of());
        assertEquals(0, road.minX);
        assertEquals(30, road.maxX);
        int[] shifted = road.shifted(2);
        assertEquals(2, shifted[0]);
        assertEquals(32, shifted[1]);
        assertEquals(0, shifted[2]);
    }
}
