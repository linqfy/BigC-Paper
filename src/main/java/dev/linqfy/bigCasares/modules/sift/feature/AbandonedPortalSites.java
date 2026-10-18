package dev.linqfy.bigCasares.modules.sift.feature;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Where abandoned portal ruins may stand, for {@code /dimension locate}: the port of the candidate search in the
 * mod's {@code SiftLocateCommand.locateAbandonedPortal}. Each cell of {@value SiftLandmarkPlacement#ABANDONED_PORTAL_CELL_CHUNKS}
 * chunks has one candidate chunk that is a pure function of the world seed ({@link SiftLandmarkPlacement}); the
 * ruin is raised there only if the terrain allows it, so a candidate is a place worth generating and looking at,
 * not a promise.
 */
public final class AbandonedPortalSites {

    /** How many cells around the sender's cell are searched in each direction. */
    static final int CELL_RADIUS = 7;
    /** The mod keeps this many nearest candidates. */
    static final int KEPT = 96;

    /** A candidate chunk, with its centre column and the horizontal distance to the sender, squared. */
    public record Candidate(int chunkX, int chunkZ, double distanceSquared) {

        public int centerX() {
            return (chunkX << 4) + 8;
        }

        public int centerZ() {
            return (chunkZ << 4) + 8;
        }
    }

    private AbandonedPortalSites() {
    }

    /** The {@value #KEPT} candidates nearest to the point among the 15 x 15 cells around it, nearest first. */
    public static List<Candidate> around(long seed, double x, double z) {
        int cell = SiftLandmarkPlacement.ABANDONED_PORTAL_CELL_CHUNKS;
        int centerCellX = Math.floorDiv(Math.floorDiv((int) Math.floor(x), 16), cell);
        int centerCellZ = Math.floorDiv(Math.floorDiv((int) Math.floor(z), 16), cell);
        List<Candidate> candidates = new ArrayList<>();
        for (int dx = -CELL_RADIUS; dx <= CELL_RADIUS; dx++) {
            for (int dz = -CELL_RADIUS; dz <= CELL_RADIUS; dz++) {
                for (int index = 0; index < SiftLandmarkPlacement.ABANDONED_PORTAL_CANDIDATES; index++) {
                    SiftLandmarkPlacement.ChunkPos chunk = SiftLandmarkPlacement.candidateChunk(seed, centerCellX + dx,
                        centerCellZ + dz, cell, SiftLandmarkPlacement.ABANDONED_PORTAL_SALT, index);
                    double distanceX = (chunk.x() << 4) + 8 + 0.5 - x;
                    double distanceZ = (chunk.z() << 4) + 8 + 0.5 - z;
                    candidates.add(new Candidate(chunk.x(), chunk.z(), distanceX * distanceX + distanceZ * distanceZ));
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::distanceSquared));
        return List.copyOf(candidates.subList(0, Math.min(KEPT, candidates.size())));
    }

    /**
     * The candidates worth generating now: nearest first, only those nearer than the ruin already known
     * ({@code knownDistanceSquared}, infinity when none), without those {@code skip} accepts (already tried or
     * out of bounds), at most {@code budget} of them. The mod generates these chunks and looks again.
     */
    public static List<Candidate> toGenerate(List<Candidate> candidates, double knownDistanceSquared, int budget,
                                             Predicate<Candidate> skip) {
        List<Candidate> chosen = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (chosen.size() >= budget || candidate.distanceSquared() >= knownDistanceSquared) {
                break;
            }
            if (!skip.test(candidate)) {
                chosen.add(candidate);
            }
        }
        return chosen;
    }
}
