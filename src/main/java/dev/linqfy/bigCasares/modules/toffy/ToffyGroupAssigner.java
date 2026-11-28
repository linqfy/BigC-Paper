package dev.linqfy.bigCasares.modules.toffy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Automatic placement of players into the groups of a tournament ({@code /toffy torneo grupo <torneo> repartir}).
 * Pure: the levels, the group constraints and the random source come in as arguments.
 */
final class ToffyGroupAssigner {
    private ToffyGroupAssigner() {
    }

    /** A group as the assigner sees it: its capacity, level range and how many members it already has. */
    record Slot(String key, Integer capacity, Double minLevel, Double maxLevel, int size) {
        boolean fits(double level) {
            return (minLevel == null || level >= minLevel) && (maxLevel == null || level <= maxLevel);
        }

        /** Width of the level range; unbounded ranges are the widest. */
        double width() {
            if (minLevel == null || maxLevel == null) {
                return Double.POSITIVE_INFINITY;
            }
            return maxLevel - minLevel;
        }

        /** Whether the group can take one more member when it has {@code members} now. */
        boolean hasRoom(int members) {
            return capacity == null || members < capacity;
        }
    }

    /** Who went where, and who fit no group (stays ungrouped). */
    record Result(Map<UUID, String> assigned, List<UUID> ungrouped) {
        Result {
            assigned = Collections.unmodifiableMap(new LinkedHashMap<>(assigned));
            ungrouped = List.copyOf(ungrouped);
        }
    }

    /**
     * Places every player in one group.
     *
     * <p>{@code parejo}: the players are shuffled, then each one goes to the eligible group with the fewest members, so
     * the sizes stay balanced. {@code nivel}: the players are sorted by level, then each one goes to the eligible group
     * with the fewest members, and among those the group with the narrowest range (best fit).
     *
     * @param levels players to place with their real level, in the order they are considered
     * @param slots  the groups; their {@link Slot#size()} is the number of members they already have
     */
    static Result assign(Map<UUID, Double> levels, List<Slot> slots, boolean parejo, Random random) {
        List<UUID> order = new ArrayList<>(levels.keySet());
        if (parejo) {
            Collections.shuffle(order, random);
        } else {
            order.sort(Comparator.comparingDouble(levels::get));
        }
        int[] sizes = new int[slots.size()];
        for (int index = 0; index < slots.size(); index++) {
            sizes[index] = slots.get(index).size();
        }
        Map<UUID, String> assigned = new LinkedHashMap<>();
        List<UUID> ungrouped = new ArrayList<>();
        for (UUID id : order) {
            double level = levels.get(id);
            int best = -1;
            for (int index = 0; index < slots.size(); index++) {
                Slot slot = slots.get(index);
                if (!slot.fits(level) || !slot.hasRoom(sizes[index])) {
                    continue;
                }
                if (best < 0 || better(slots, sizes, parejo, index, best)) {
                    best = index;
                }
            }
            if (best < 0) {
                ungrouped.add(id);
            } else {
                assigned.put(id, slots.get(best).key());
                sizes[best]++;
            }
        }
        return new Result(assigned, ungrouped);
    }

    /** Whether group {@code candidate} is a better home than {@code current}. Ties keep the earlier group. */
    private static boolean better(List<Slot> slots, int[] sizes, boolean parejo, int candidate, int current) {
        if (sizes[candidate] != sizes[current]) {
            return sizes[candidate] < sizes[current];
        }
        return !parejo && slots.get(candidate).width() < slots.get(current).width();
    }
}
