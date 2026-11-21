package dev.linqfy.bigCasares.modules.alexscaves.integration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pure rules of a boss fight table: the damage each player did to one boss, kept in the boss's own persistent data
 * (as text) so it survives restarts and unloads and disappears with the boss. No server classes here.
 */
public final class BossFightTracker {
    static final int MAX_PLAYERS = 32;
    static final int PLACES = 3;
    /** The bosses finish themselves off with a 1.0E6 hit (their death animation ends): that is not player damage. */
    static final double SELF_KILL_DAMAGE = 100_000.0;

    /** A placement with the damage that earned it. */
    public record Standing(UUID playerId, double damage, int placement) {
    }

    private BossFightTracker() {
    }

    /** Adds damage to the table (in place); ignored when not a positive finite number or the table is full. */
    public static void record(Map<UUID, Double> table, UUID playerId, double damage) {
        if (!(damage > 0.0) || !Double.isFinite(damage) || damage >= SELF_KILL_DAMAGE) {
            return;
        }
        if (table.size() >= MAX_PLAYERS && !table.containsKey(playerId)) {
            return;
        }
        table.merge(playerId, damage, Double::sum);
    }

    /**
     * Whether a hit took the boss into its death animation. The bosses cancel the lethal blow, set their health to 1
     * and play the animation, so the hit is cancelled, was lethal and left the boss at 1 or less from above 1. A hit
     * cancelled by another plugin leaves the health untouched and is not one.
     */
    public static boolean enteredDying(boolean cancelled, double healthBefore, double healthAfter, double finalDamage) {
        return cancelled && healthBefore > 1.0 && finalDamage >= healthBefore && healthAfter <= 1.0;
    }

    /** Top three with at least {@code minDamage}: most damage first, the earlier first hit wins ties. */
    public static List<Standing> standings(Map<UUID, Double> table, double minDamage) {
        List<Map.Entry<UUID, Double>> entries = new ArrayList<>(table.entrySet());
        entries.removeIf(entry -> entry.getValue() < minDamage);
        // Stable sort over insertion order: the first to hit wins ties.
        entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        List<Standing> standings = new ArrayList<>();
        for (int i = 0; i < Math.min(PLACES, entries.size()); i++) {
            standings.add(new Standing(entries.get(i).getKey(), entries.get(i).getValue(), i + 1));
        }
        return List.copyOf(standings);
    }

    /** {@code uuid=damage;uuid=damage}, in insertion order. */
    public static String encode(Map<UUID, Double> table) {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<UUID, Double> entry : table.entrySet()) {
            if (!text.isEmpty()) {
                text.append(';');
            }
            text.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return text.toString();
    }

    /** Reads {@link #encode}; broken or oversized entries are skipped. */
    public static Map<UUID, Double> decode(String text) {
        Map<UUID, Double> table = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return table;
        }
        for (String part : text.split(";")) {
            int equals = part.indexOf('=');
            if (equals <= 0 || table.size() >= MAX_PLAYERS) {
                continue;
            }
            try {
                double damage = Double.parseDouble(part.substring(equals + 1));
                if (damage > 0.0 && Double.isFinite(damage)) {
                    table.put(UUID.fromString(part.substring(0, equals)), damage);
                }
            } catch (IllegalArgumentException broken) {
                // skip it
            }
        }
        return table;
    }
}
