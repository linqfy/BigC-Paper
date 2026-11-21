package dev.linqfy.bigCasares.modules.mythicdungeons.boss.pachamama;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Picks the colour and the cells of one painted-floor round (no server types).
 *
 * <p>The colour is random among blue, brown and yellow, never the same twice in a row so the rounds feel different.
 * Blue paints a random subset of the tiles; brown and yellow paint one colour of the chessboard (random parity) with
 * a few tiles left out. Either way at least {@link #MIN_SAFE_SHARE} of the usable tiles stay safe and at least one
 * tile is painted.</p>
 */
public final class PachamamaRoundPlanner {

    /** Never paint more than this share of the usable tiles: there is always somewhere to stand. */
    public static final double MAX_PAINTED_SHARE = 0.5;
    public static final double MIN_SAFE_SHARE = 1.0 - MAX_PAINTED_SHARE;

    /** One planned round. */
    public record Round(PachamamaColour colour, Set<PachamamaTile> painted) {

        public Round {
            Objects.requireNonNull(colour, "colour");
            painted = Collections.unmodifiableSet(new LinkedHashSet<>(painted));
        }

        public boolean paints(PachamamaTile tile) {
            return painted.contains(tile);
        }
    }

    private PachamamaRoundPlanner() {
    }

    /** A random colour different from {@code previous} ({@code null} for the first round). */
    public static PachamamaColour colour(PachamamaColour previous, RandomGenerator random) {
        List<PachamamaColour> options = new ArrayList<>(List.of(PachamamaColour.values()));
        options.remove(previous);
        return options.get(random.nextInt(options.size()));
    }

    /**
     * @param usable   the tiles that may be painted (with a floor, away from the altar and the pedestal)
     * @param previous the colour of the last round, or {@code null}
     */
    public static Round plan(List<PachamamaTile> usable, boolean phaseTwo, PachamamaColour previous,
                             RandomGenerator random) {
        PachamamaColour colour = colour(previous, random);
        return new Round(colour, cells(colour, usable, phaseTwo, random));
    }

    /** The painted tiles for a colour, in the order of {@code usable}. */
    public static Set<PachamamaTile> cells(PachamamaColour colour, List<PachamamaTile> usable, boolean phaseTwo,
                                           RandomGenerator random) {
        if (usable.isEmpty()) {
            return Set.of();
        }
        int cap = Math.max(1, (int) Math.floor(usable.size() * MAX_PAINTED_SHARE));
        List<PachamamaTile> chosen = new ArrayList<>();
        if (colour.chessboard()) {
            int parity = random.nextInt(2);
            double keep = PachamamaTuning.boardKeep(phaseTwo);
            for (PachamamaTile tile : usable) {
                if (tile.parity() == parity && random.nextDouble() < keep) {
                    chosen.add(tile);
                }
            }
            if (chosen.isEmpty()) {
                usable.stream().filter(tile -> tile.parity() == parity).findFirst().ifPresent(chosen::add);
            }
        } else {
            List<PachamamaTile> shuffled = new ArrayList<>(usable);
            for (int index = shuffled.size() - 1; index > 0; index--) {
                Collections.swap(shuffled, index, random.nextInt(index + 1));
            }
            int count = Math.max(1, (int) Math.round(usable.size() * PachamamaTuning.blueShare(phaseTwo)));
            chosen.addAll(shuffled.subList(0, Math.min(count, shuffled.size())));
        }
        if (chosen.isEmpty()) {
            chosen.add(usable.get(random.nextInt(usable.size())));
        }
        while (chosen.size() > cap) {
            chosen.remove(random.nextInt(chosen.size()));
        }
        Set<PachamamaTile> ordered = new LinkedHashSet<>();
        for (PachamamaTile tile : usable) {
            if (chosen.contains(tile)) {
                ordered.add(tile);
            }
        }
        return ordered;
    }
}
