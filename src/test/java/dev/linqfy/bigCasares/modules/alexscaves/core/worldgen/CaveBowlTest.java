package dev.linqfy.bigCasares.modules.alexscaves.core.worldgen;

import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBowl.Carve;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBowl.Kind;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaveBowlTest {
    @Test
    void simplexNoiseStaysInRangeAndIsDeterministic() {
        double min = 1;
        double max = -1;
        for (int i = 0; i < 20000; i++) {
            float value = SimplexNoise3D.sample(i * 7, i % 13, i * 3, 40.0F);
            assertEquals(value, SimplexNoise3D.sample(i * 7, i % 13, i * 3, 40.0F));
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        assertTrue(min >= -1.05 && max <= 1.05, "range " + min + ".." + max);
        assertTrue(max - min > 1.0, "the noise actually varies");
    }

    @Test
    void dinoBowlIsWideAtTheCentreHeightAndClosedAboveAndBelow() {
        CaveBowl bowl = new CaveBowl(Kind.DINO_BOWL, 0, -1, 0, 66, 100);
        int atCentre = 0;
        int atEdge = 0;
        for (int x = -120; x <= 120; x += 3) {
            for (int z = -120; z <= 120; z += 3) {
                if (bowl.carve(x, -1, z) == Carve.AIR) {
                    atCentre++;
                }
                if (bowl.carve(x, 31, z) == Carve.AIR) {
                    atEdge++;
                }
                assertEquals(Carve.NONE, bowl.carve(x, -1 + 40, z), "closed above the bowl height");
                assertEquals(Carve.NONE, bowl.carve(x, -1 - 40, z), "closed below the bowl height");
            }
        }
        assertTrue(atCentre > atEdge, "the bowl narrows towards its rim: " + atCentre + " vs " + atEdge);
        assertTrue(atCentre > 500, "the bowl is large: " + atCentre);
        assertEquals(Carve.AIR, bowl.carve(0, -1, 0));
        assertEquals(Carve.NONE, bowl.carve(150, -1, 0), "nothing beyond the radius");
    }

    @Test
    void ferrocaveIsABallWithSolidOrbsInside() {
        CaveBowl cave = new CaveBowl(Kind.FERROCAVE, 0, 0, 0, 90, 60);
        int open = 0;
        int insideSolid = 0;
        for (int x = -60; x <= 60; x += 2) {
            for (int z = -60; z <= 60; z += 2) {
                for (int y = -40; y <= 40; y += 4) {
                    Carve carve = cave.carve(x, y, z);
                    assertTrue(carve == Carve.AIR || carve == Carve.NONE);
                    if (carve == Carve.AIR) {
                        open++;
                    } else if (x * x + z * z < 15 * 15 && Math.abs(y) < 20) {
                        insideSolid++;
                    }
                }
            }
        }
        assertTrue(open > 1000, "a large cave: " + open);
        assertTrue(insideSolid > 0, "the mod's inner orbs stay solid");
        assertEquals(Carve.NONE, cave.carve(0, 60, 0), "closed above");
    }

    @Test
    void acidPitHasPillarsAndALakeBelowTheAcidLevel() {
        CaveBowl pit = new CaveBowl(Kind.ACID_PIT, 0, -10, 0, 50, 90);
        Map<Carve, Integer> counts = new EnumMap<>(Carve.class);
        for (int x = -90; x <= 90; x += 3) {
            for (int z = -90; z <= 90; z += 3) {
                for (int y = -40; y <= 20; y += 2) {
                    Carve carve = pit.carve(x, y, z);
                    counts.merge(carve, 1, Integer::sum);
                    if (carve == Carve.ACID) {
                        assertTrue(y < CaveBowl.ACID_LEVEL, "acid only below the lake level");
                    }
                    if (carve == Carve.AIR) {
                        assertTrue(y >= CaveBowl.ACID_LEVEL, "air only above the lake level");
                    }
                }
            }
        }
        assertTrue(counts.getOrDefault(Carve.AIR, 0) > 500, "open pit: " + counts);
        assertTrue(counts.getOrDefault(Carve.PILLAR, 0) > 0, "pillars: " + counts);
        assertTrue(counts.getOrDefault(Carve.ACID, 0) > 0, "lake: " + counts);
    }

    @Test
    void sizesFollowTheModPerKind() {
        Random random = new Random(7);
        for (int i = 0; i < 200; i++) {
            Optional<CaveBowl> ferro = CaveBowl.plan(Kind.FERROCAVE, random, 0, 0, y -> true, (x, z) -> true);
            assertTrue(ferro.isPresent());
            assertTrue(ferro.get().centerY() >= -20 && ferro.get().centerY() < 20);
            assertTrue(ferro.get().radius() >= 45 && ferro.get().radius() < 70);
            Optional<CaveBowl> acid = CaveBowl.plan(Kind.ACID_PIT, random, 0, 0, y -> true, (x, z) -> true);
            assertTrue(acid.get().centerY() >= -25 && acid.get().centerY() < 5);
            assertEquals(90, acid.get().radius());
            assertEquals(50 - 5, acid.get().height());
        }
        assertTrue(Kind.DINO_BOWL.reachChunks() >= new CaveBowl(Kind.DINO_BOWL, 0, -1, 0, 75, 100).widthChunks());
    }

    @Test
    void caveIsSizedByHowFarTheBiomeContinues() {
        Optional<CaveBowl> full = CaveBowl.plan(Kind.DINO_BOWL, new Random(1), 0, 0, y -> true, (x, z) -> true);
        assertTrue(full.isPresent());
        assertEquals(100, full.get().radius());
        assertEquals(80 - 5, full.get().height());

        Optional<CaveBowl> narrow = CaveBowl.plan(Kind.DINO_BOWL, new Random(1), 0, 0, y -> true,
            (x, z) -> Math.abs(x) < 40 && Math.abs(z) < 40);
        assertTrue(narrow.isPresent());
        assertTrue(narrow.get().radius() < 60, "narrower biome, smaller bowl: " + narrow.get().radius());

        assertTrue(CaveBowl.plan(Kind.DINO_BOWL, new Random(1), 0, 0, y -> true, (x, z) -> false).isEmpty(),
            "no start outside the biome");
        assertTrue(CaveBowl.plan(Kind.DINO_BOWL, new Random(1), 0, 0, y -> false, (x, z) -> true).isEmpty(),
            "no start without room");
    }

    @Test
    void startBiomeIsCheckedAtTheChunkMiddleForEveryKind() {
        for (Kind kind : Kind.values()) {
            // Biome only at the chunk's min corner: the mod's stub is at the middle, so there is no start.
            assertTrue(CaveBowl.plan(kind, new Random(3), 32, 48, y -> true, (x, z) -> x == 32 && z == 48).isEmpty(),
                kind + " must not start on a corner-only biome");
            // Biome at the middle column (and around it): a start.
            assertTrue(CaveBowl.plan(kind, new Random(3), 32, 48, y -> true,
                (x, z) -> Math.abs(x - 40) < 30 && Math.abs(z - 56) < 30).isPresent(), kind + " starts at the middle");
            // Biome everywhere except the middle column.
            assertTrue(CaveBowl.plan(kind, new Random(3), 32, 48, y -> true, (x, z) -> !(x == 40 && z == 56)).isEmpty(),
                kind + " needs the middle column");
        }
    }

    @Test
    void startBiomeIsCheckedAtTheStructureHeight() {
        for (Kind kind : Kind.values()) {
            int centerY = kind.sizes(new Random(3))[0];
            assertTrue(CaveBowl.plan(kind, new Random(3), 0, 0, y -> y != centerY, (x, z) -> true).isEmpty(),
                kind + " needs the biome at its generation y");
        }
    }

    @Test
    void placementGivesOneStartPerRegionAndRepeats() {
        List<int[]> first = CaveBowl.origins(42L, 10, -7, 6, 4, 2, 1);
        List<int[]> again = CaveBowl.origins(42L, 10, -7, 6, 4, 2, 1);
        assertEquals(first.size(), again.size());
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i)[0], again.get(i)[0]);
            assertEquals(first.get(i)[1], again.get(i)[1]);
        }
        for (int[] origin : first) {
            // Each start sits in the first (spacing - separation) chunks of its region.
            assertTrue(Math.floorMod(origin[0], 4) < 2 && Math.floorMod(origin[1], 4) < 2);
        }
        assertTrue(first.size() >= 9);
    }

    @Test
    void boundsNeverSkipACarvedBlock() {
        List<CaveBowl> caves = List.of(new CaveBowl(Kind.DINO_BOWL, 3, -1, -5, 66, 100),
            new CaveBowl(Kind.FERROCAVE, 0, 7, 0, 90, 60), new CaveBowl(Kind.ACID_PIT, -4, -12, 9, 45, 90));
        for (CaveBowl cave : caves) {
            for (int x = -130; x <= 130; x += 3) {
                for (int z = -130; z <= 130; z += 3) {
                    for (int y = -100; y <= 100; y += 2) {
                        if (cave.carve(x, y, z) == Carve.NONE) {
                            continue;
                        }
                        assertTrue(cave.columnMayCarve(x, z), cave.kind() + " column " + x + "," + z);
                        assertTrue(y >= cave.carveMinY() && y <= cave.carveMaxY(), cave.kind() + " y " + y);
                    }
                }
            }
        }
    }

    @Test
    void chunksFarFromTheStartAreNotReached() {
        CaveBowl bowl = new CaveBowl(Kind.DINO_BOWL, 0, -1, 0, 66, 100);
        assertTrue(bowl.reaches(0, 0));
        assertTrue(bowl.reaches(6, -6));
        assertFalse(bowl.reaches(8, 0));
        assertTrue(bowl.minY(-64) >= -64 && bowl.maxY() > 0);
    }
}
