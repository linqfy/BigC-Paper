package dev.linqfy.bigCasares.modules.staffevents;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Who carries the TNT each round of TNT Tag. */
public final class TntTagRules {

    private TntTagRules() {
    }

    /** One tagged player per {@code playersPerTagger} alive players, at least one, never everybody. */
    public static int taggerCount(int alive, int playersPerTagger) {
        if (alive < 2) {
            return 0;
        }
        int count = (int) Math.ceil(alive / (double) Math.max(1, playersPerTagger));
        return Math.max(1, Math.min(alive - 1, count));
    }

    public static Set<UUID> chooseTaggers(Collection<UUID> alive, int playersPerTagger, Random random) {
        List<UUID> shuffled = new ArrayList<>(alive);
        Collections.shuffle(shuffled, random);
        return Set.copyOf(shuffled.subList(0, taggerCount(shuffled.size(), playersPerTagger)));
    }
}
