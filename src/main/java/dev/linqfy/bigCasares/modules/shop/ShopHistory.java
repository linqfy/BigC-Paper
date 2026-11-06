package dev.linqfy.bigCasares.modules.shop;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Each player's last purchases and sales that went through, newest first, from any menu. In memory, and kept with the
 * player too ({@link #encode}/{@link #restore}) so it survives restarts; the economy plugin stays the record of the
 * money itself.
 */
public final class ShopHistory {
    static final int KEPT = 10;

    record Entry(Instant at, String message) {
    }

    private final Map<UUID, Deque<Entry>> entries = new ConcurrentHashMap<>();
    private final Supplier<Instant> clock;

    public ShopHistory() {
        this(Instant::now);
    }

    ShopHistory(Supplier<Instant> clock) {
        this.clock = clock;
    }

    /** A successful operation's message ("Compraste ... por ..."). */
    void record(UUID playerId, String message) {
        Deque<Entry> kept = entries.computeIfAbsent(playerId, ignored -> new ArrayDeque<>());
        synchronized (kept) {
            kept.addFirst(new Entry(clock.get(), message));
            while (kept.size() > KEPT) kept.removeLast();
        }
    }

    /** The key it is kept under with the player (PlayerStrings), so it survives restarts. */
    static final String KEY = "shop_history_v1";

    /** The player's entries as text to keep with them: one "epoch millis TAB message" line each, newest first. */
    String encode(UUID playerId) {
        Deque<Entry> kept = entries.get(playerId);
        if (kept == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        synchronized (kept) {
            for (Entry entry : kept) {
                if (!out.isEmpty()) out.append('\n');
                out.append(entry.at().toEpochMilli()).append('\t').append(entry.message().replace('\n', ' ').replace('\t', ' '));
            }
        }
        return out.toString();
    }

    /**
     * Loads what was kept with the player (a join after a restart). Only when nothing is in memory for them yet, so a
     * newer session's operations are never replaced; unreadable lines are skipped.
     */
    void restore(UUID playerId, String encoded) {
        if (encoded == null || encoded.isBlank() || entries.containsKey(playerId)) {
            return;
        }
        Deque<Entry> kept = new ArrayDeque<>();
        for (String line : encoded.split("\n")) {
            int tab = line.indexOf('\t');
            if (tab <= 0 || kept.size() == KEPT) continue;
            try {
                kept.addLast(new Entry(Instant.ofEpochMilli(Long.parseLong(line.substring(0, tab))), line.substring(tab + 1)));
            } catch (NumberFormatException unreadable) {
                // skipped
            }
        }
        if (!kept.isEmpty()) {
            entries.putIfAbsent(playerId, kept);
        }
    }

    /** The player's last operation that went through, if any (any thread). */
    public java.util.Optional<String> latest(UUID playerId) {
        Deque<Entry> kept = entries.get(playerId);
        if (kept == null) {
            return java.util.Optional.empty();
        }
        synchronized (kept) {
            return kept.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(kept.peekFirst().message());
        }
    }

    /** Chat lines for the player: newest first, each with how long ago. */
    public List<String> lines(UUID playerId) {
        Deque<Entry> kept = entries.get(playerId);
        List<String> lines = new ArrayList<>();
        lines.add("§6Tus últimas operaciones en la tienda");
        if (kept == null || kept.isEmpty()) {
            lines.add("§7Todavía no compraste ni vendiste nada.");
            return lines;
        }
        Instant now = clock.get();
        synchronized (kept) {
            for (Entry entry : kept) {
                lines.add("§8" + ago(Duration.between(entry.at(), now)) + " §f" + entry.message());
            }
        }
        return lines;
    }

    static String ago(Duration elapsed) {
        long seconds = Math.max(0, elapsed.getSeconds());
        if (seconds < 60) return "recién";
        if (seconds < 3600) return "hace " + seconds / 60 + " min";
        if (seconds < 86_400) return "hace " + seconds / 3600 + " h";
        return "hace " + seconds / 86_400 + " d";
    }
}
