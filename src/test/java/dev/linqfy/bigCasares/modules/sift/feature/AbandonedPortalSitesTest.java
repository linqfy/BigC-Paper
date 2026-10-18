package dev.linqfy.bigCasares.modules.sift.feature;

import dev.linqfy.bigCasares.modules.sift.feature.AbandonedPortalSites.Candidate;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbandonedPortalSitesTest {

    private static final long SEED = 987654321L;

    @Test
    void candidatesAreTheModsSeedSelectedChunksNearestFirst() {
        List<Candidate> candidates = AbandonedPortalSites.around(SEED, 100.0, -250.0);
        assertEquals(AbandonedPortalSites.KEPT, candidates.size());
        for (int index = 1; index < candidates.size(); index++) {
            assertTrue(candidates.get(index - 1).distanceSquared() <= candidates.get(index).distanceSquared());
        }
        // Every candidate is the chunk the generator selects for its cell, so the generator will try it.
        for (Candidate candidate : candidates) {
            assertTrue(SiftLandmarkPlacement.isSelectedChunk(SEED, candidate.chunkX() << 4, candidate.chunkZ() << 4,
                SiftLandmarkPlacement.ABANDONED_PORTAL_CELL_CHUNKS, SiftLandmarkPlacement.ABANDONED_PORTAL_SALT,
                SiftLandmarkPlacement.ABANDONED_PORTAL_CANDIDATES));
            double dx = candidate.centerX() + 0.5 - 100.0;
            double dz = candidate.centerZ() + 0.5 + 250.0;
            assertEquals(dx * dx + dz * dz, candidate.distanceSquared(), 1.0E-6);
        }
        assertEquals(candidates.size(), new HashSet<>(candidates).size());
    }

    @Test
    void theNearestCandidateIsTheNearestOfAllCellsAround() {
        // The 15 x 15 cells the mod looks at hold the nearest ruin of a sender anywhere in the middle cell.
        for (double[] sender : new double[][]{{0, 0}, {5000, -3000}, {-123.5, 77.25}}) {
            double best = Double.POSITIVE_INFINITY;
            int cellX = Math.floorDiv(Math.floorDiv((int) Math.floor(sender[0]), 16), 20);
            int cellZ = Math.floorDiv(Math.floorDiv((int) Math.floor(sender[1]), 16), 20);
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    SiftLandmarkPlacement.ChunkPos chunk = SiftLandmarkPlacement.candidateChunk(SEED, cellX + dx, cellZ + dz,
                        SiftLandmarkPlacement.ABANDONED_PORTAL_CELL_CHUNKS, SiftLandmarkPlacement.ABANDONED_PORTAL_SALT, 0);
                    double x = (chunk.x() << 4) + 8.5 - sender[0];
                    double z = (chunk.z() << 4) + 8.5 - sender[1];
                    best = Math.min(best, x * x + z * z);
                }
            }
            assertEquals(best, AbandonedPortalSites.around(SEED, sender[0], sender[1]).get(0).distanceSquared(), 1.0E-6);
        }
    }

    @Test
    void theCandidatesDependOnlyOnTheSeedAndTheSendersPosition() {
        assertEquals(AbandonedPortalSites.around(SEED, 1.0, 1.0), AbandonedPortalSites.around(SEED, 1.0, 1.0));
        assertTrue(!AbandonedPortalSites.around(SEED, 0.0, 0.0).equals(AbandonedPortalSites.around(SEED + 1, 0.0, 0.0)));
    }

    @Test
    void generationStopsAtTheKnownRuinTheBudgetAndTheSkippedOnes() {
        List<Candidate> candidates = List.of(new Candidate(1, 0, 100.0), new Candidate(2, 0, 400.0), new Candidate(3, 0, 900.0),
            new Candidate(4, 0, 1600.0), new Candidate(5, 0, 2500.0));
        // Nothing known: the budget limits.
        assertEquals(candidates.subList(0, 3), AbandonedPortalSites.toGenerate(candidates, Double.POSITIVE_INFINITY, 3, c -> false));
        // A ruin 900 away: only candidates nearer than it are worth generating.
        assertEquals(candidates.subList(0, 2), AbandonedPortalSites.toGenerate(candidates, 900.0, 32, c -> false));
        // Skipped candidates (tried, outside the border) do not use the budget.
        assertEquals(List.of(candidates.get(1), candidates.get(2)),
            AbandonedPortalSites.toGenerate(candidates, Double.POSITIVE_INFINITY, 2, c -> c.chunkX() == 1));
        assertTrue(AbandonedPortalSites.toGenerate(candidates, 50.0, 32, c -> false).isEmpty());
        assertTrue(AbandonedPortalSites.toGenerate(candidates, Double.POSITIVE_INFINITY, 0, c -> false).isEmpty());
    }
}
