package dev.linqfy.bigCasares.modules.missions;

import dev.linqfy.bigCasares.platform.PlayerStrings;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * What each player has done with their missions, kept with the player ({@link PlayerStrings}) so it survives restarts:
 * how much they claimed this week, and their streak of days with every daily mission claimed. Anything unreadable
 * counts as nothing.
 */
final class MissionRecords {
    static final String WEEK_KEY = "missions_week_v1";
    static final String STREAK_KEY = "missions_streak_v1";

    private final PlayerStrings store;

    MissionRecords(PlayerStrings store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /** Money claimed in the week ending {@code weekEnds}; 0 for another week (the total starts again). */
    double weekTotal(Player player, Instant weekEnds) {
        String[] parts = store.get(player, WEEK_KEY).orElse("").split("\\|");
        try {
            return parts.length == 2 && Long.parseLong(parts[0]) == weekEnds.toEpochMilli() ? Math.max(0, Double.parseDouble(parts[1])) : 0;
        } catch (NumberFormatException unreadable) {
            return 0;
        }
    }

    void addClaimed(Player player, Instant weekEnds, double amount) {
        if (amount <= 0) {
            return;
        }
        store.set(player, WEEK_KEY, weekEnds.toEpochMilli() + "|" + (weekTotal(player, weekEnds) + amount));
    }

    /** Days in a row, up to {@code today}, with every daily mission claimed; 0 once a day was missed. */
    int streak(Player player, LocalDate today) {
        Streak streak = read(player);
        return streak != null && !streak.last().isBefore(today.minusDays(1)) && !streak.last().isAfter(today) ? streak.days() : 0;
    }

    /** Every daily mission of {@code today} is claimed: the streak goes on (or starts again). Once per day. */
    void dailiesDone(Player player, LocalDate today) {
        Streak streak = read(player);
        if (streak != null && streak.last().equals(today)) {
            return;
        }
        int days = streak != null && streak.last().equals(today.minusDays(1)) ? streak.days() + 1 : 1;
        store.set(player, STREAK_KEY, today + "|" + days);
    }

    private record Streak(LocalDate last, int days) {
    }

    private Streak read(Player player) {
        String[] parts = store.get(player, STREAK_KEY).orElse("").split("\\|");
        try {
            return parts.length == 2 ? new Streak(LocalDate.parse(parts[0]), Math.max(1, Integer.parseInt(parts[1]))) : null;
        } catch (RuntimeException unreadable) {
            return null;
        }
    }
}
