package dev.linqfy.bigCasares.modules.villagermood;

import java.util.Optional;

/**
 * Pure rules of villager mood: cell geometry (1x1 confinement, escape holes, cliffs) and the arithmetic of
 * depression, annoyance and price surcharges.
 */
final class VillagerMoodPolicy {

    private static final int[][] CARDINAL = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] AROUND = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    /** How far below a hole's far side a villager may land when it squeezes out. */
    private static final int MAX_ESCAPE_FALL = 3;

    record Cell(int x, int y, int z) {
    }

    private final VillagerMoodSettings settings;

    VillagerMoodPolicy(VillagerMoodSettings settings) {
        this.settings = settings;
    }

    // --- geometry --------------------------------------------------------------------------------

    /**
     * A villager standing at the given feet block is confined when it can neither walk nor jump into any of the
     * four neighbouring blocks: the typical trading-hall cell.
     */
    static boolean isConfined(BlockProbe probe, int x, int y, int z) {
        boolean roomToJump = probe.passable(x, y + 2, z);
        for (int[] direction : CARDINAL) {
            int nx = x + direction[0];
            int nz = z + direction[1];
            boolean walk = probe.passable(nx, y, nz) && probe.passable(nx, y + 1, nz);
            boolean jump = roomToJump
                && !probe.passable(nx, y, nz) && !probe.tall(nx, y, nz)
                && probe.passable(nx, y + 1, nz) && probe.passable(nx, y + 2, nz);
            if (walk || jump) {
                return false;
            }
        }
        return true;
    }

    /**
     * Looks for a one-block-high hole in the cell wall (only the feet or only the head block of a neighbour is
     * open) with somewhere to stand right behind it. Returns that landing spot.
     */
    static Optional<Cell> findEscapeHole(BlockProbe probe, int x, int y, int z) {
        for (int[] direction : CARDINAL) {
            int nx = x + direction[0];
            int nz = z + direction[1];
            boolean feet = probe.passable(nx, y, nz);
            boolean head = probe.passable(nx, y + 1, nz);
            if (feet == head) {
                continue;
            }
            int bx = x + direction[0] * 2;
            int bz = z + direction[1] * 2;
            for (int by = y + 1; by >= y - MAX_ESCAPE_FALL; by--) {
                if (probe.passable(bx, by, bz) && probe.passable(bx, by + 1, bz) && !probe.passable(bx, by - 1, bz)) {
                    return Optional.of(new Cell(bx, by, bz));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Finds the nearest walkable edge (along the eight compass directions, on the villager's own level) behind
     * which the ground drops at least {@code minDrop} blocks. Returns the air block over the drop.
     */
    static Optional<Cell> findCliffEdge(BlockProbe probe, int x, int y, int z, int radius, int minDrop) {
        Cell best = null;
        int bestStep = Integer.MAX_VALUE;
        for (int[] direction : AROUND) {
            for (int step = 1; step <= radius && step < bestStep; step++) {
                int cx = x + direction[0] * step;
                int cz = z + direction[1] * step;
                if (!probe.passable(cx, y, cz) || !probe.passable(cx, y + 1, cz)) {
                    break;
                }
                if (!probe.passable(cx, y - 1, cz)) {
                    continue;
                }
                int drop = 1;
                while (drop <= minDrop && probe.passable(cx, y - drop, cz)) {
                    drop++;
                }
                if (drop > minDrop) {
                    best = new Cell(cx, y, cz);
                    bestStep = step;
                }
                // A shallow step down changes the walking level: stop following this direction.
                break;
            }
        }
        return Optional.ofNullable(best);
    }

    // --- depression ------------------------------------------------------------------------------

    int depressionLevel(long confinedTicks) {
        VillagerMoodSettings.Depression depression = settings.depression();
        if (depression.maxLevel() == 0 || confinedTicks < depression.confinedTicksBeforeDepression()) {
            return 0;
        }
        long levels = 1 + (confinedTicks - depression.confinedTicksBeforeDepression()) / depression.ticksPerLevel();
        return (int) Math.min(depression.maxLevel(), levels);
    }

    /** Confinement time grows while boxed in (capped one level past the maximum) and drains once free. */
    long nextConfinedTicks(long confinedTicks, boolean confined, long elapsedTicks) {
        VillagerMoodSettings.Depression depression = settings.depression();
        if (confined) {
            long cap = depression.confinedTicksBeforeDepression() + depression.ticksPerLevel() * depression.maxLevel();
            return Math.min(cap, confinedTicks + elapsedTicks);
        }
        long recovered = Math.round(elapsedTicks * depression.recoveryRate());
        return Math.max(0L, confinedTicks - recovered);
    }

    // --- annoyance -------------------------------------------------------------------------------

    int annoyanceLevel(long annoyanceTicks) {
        VillagerMoodSettings.Spam spam = settings.spam();
        if (annoyanceTicks <= 0L) {
            return 0;
        }
        long levels = (annoyanceTicks + spam.ticksPerLevel() - 1) / spam.ticksPerLevel();
        return (int) Math.min(spam.maxLevel(), levels);
    }

    /** Adds one annoyance level; each level lasts {@code spam.ticks-per-level}. */
    long annoy(long annoyanceTicks) {
        VillagerMoodSettings.Spam spam = settings.spam();
        return Math.min(spam.ticksPerLevel() * spam.maxLevel(), Math.max(0L, annoyanceTicks) + spam.ticksPerLevel());
    }

    long decayAnnoyance(long annoyanceTicks, long elapsedTicks) {
        return Math.max(0L, annoyanceTicks - elapsedTicks);
    }

    // --- prices ----------------------------------------------------------------------------------

    double surchargeFraction(int depressionLevel, int annoyanceLevel) {
        VillagerMoodSettings.Prices prices = settings.prices();
        double fraction = depressionLevel * prices.perDepressionLevel() + annoyanceLevel * prices.perAnnoyanceLevel();
        return Math.min(prices.maxSurcharge(), Math.max(0.0, fraction));
    }

    /** Extra units of the first ingredient: at least one whenever the villager is in a bad mood at all. */
    int surcharge(int baseCost, int depressionLevel, int annoyanceLevel) {
        double fraction = surchargeFraction(depressionLevel, annoyanceLevel);
        if (fraction <= 0.0 || baseCost <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(baseCost * fraction));
    }

    boolean joinsRiot(int depressionLevel, int annoyanceLevel) {
        return depressionLevel + annoyanceLevel >= settings.riot().minMood();
    }
}
