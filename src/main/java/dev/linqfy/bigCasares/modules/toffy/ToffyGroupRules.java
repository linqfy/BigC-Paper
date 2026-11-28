package dev.linqfy.bigCasares.modules.toffy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Pure rules of the groups of a tournament: why a player may not join a group, which groups a player may choose, and
 * where a grouped player is teleported. No Bukkit and no state.
 */
final class ToffyGroupRules {
    /** Why a group refuses a player. */
    enum Refusal {
        /** Players cannot choose the group themselves (it is not open). */
        CLOSED,
        /** The capacity is reached. */
        FULL,
        /** The level of the player is outside the range of the group. */
        LEVEL_OUT_OF_RANGE,
        /** The group has a level range but the level of the player cannot be read (offline). */
        LEVEL_UNKNOWN
    }

    private ToffyGroupRules() {
    }

    /**
     * Why the group refuses the player, if it does. A player already in the group is never refused here.
     *
     * @param byPlayer  true when the player chooses the group with {@code /anotar} (needs the group to be open); false
     *                  when staff or the plugin places the player
     * @param level     real level of the player, or null when it cannot be read
     */
    static Optional<Refusal> refusal(ToffyGroup group, UUID playerId, Double level, boolean byPlayer) {
        if (group.members().contains(playerId)) {
            return Optional.empty();
        }
        if (byPlayer && !group.open()) {
            return Optional.of(Refusal.CLOSED);
        }
        if (group.isFull()) {
            return Optional.of(Refusal.FULL);
        }
        if (group.hasLevelRange()) {
            if (level == null) {
                return Optional.of(Refusal.LEVEL_UNKNOWN);
            }
            if (!group.allowsLevel(level)) {
                return Optional.of(Refusal.LEVEL_OUT_OF_RANGE);
            }
        }
        return Optional.empty();
    }

    /** Open groups of the tournament, in creation order: the ones players may choose. */
    static List<ToffyGroup> openGroups(ToffyTournament tournament) {
        return tournament.groups().stream().filter(ToffyGroup::open).toList();
    }

    /** Whether {@code /anotar <torneo>} must list the open groups instead of signing the player up. */
    static boolean mustChooseGroup(ToffyTournament tournament) {
        return tournament.groupMandatory() && !openGroups(tournament).isEmpty();
    }

    /** Comma separated display names, for the group lists of the messages. */
    static String names(List<ToffyGroup> groups) {
        return groups.stream().map(ToffyGroup::displayName).collect(Collectors.joining(", "));
    }

    /**
     * Destinations of a grouped player. An explicit destination (coordinates typed in the command) wins for everyone.
     * Otherwise the group places are used when the group has any, and the fallback (the places of the tournament, or
     * the sender's location) when it has none.
     */
    static <T> List<T> destinations(boolean explicit, List<T> groupPlaces, List<T> fallback) {
        if (explicit) {
            return fallback;
        }
        return groupPlaces.isEmpty() ? fallback : groupPlaces;
    }

    /** Short text of a level range, "sin rango", "40-120", "*-120" or "40-*". */
    static String rangeText(Double min, Double max) {
        if (min == null && max == null) {
            return "sin rango";
        }
        return boundText(min) + "-" + boundText(max);
    }

    private static String boundText(Double value) {
        if (value == null) {
            return "*";
        }
        return value == Math.rint(value) ? String.valueOf(value.longValue()) : String.valueOf(value);
    }
}
