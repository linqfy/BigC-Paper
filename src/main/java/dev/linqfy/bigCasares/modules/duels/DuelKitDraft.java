package dev.linqfy.bigCasares.modules.duels;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleSupplier;

/**
 * Kit selection when the challenge did not fix a kit: each player first bans one kit, then each picks one of
 * the kits nobody banned. Equal picks win outright; different picks are settled by a coin flip.
 */
final class DuelKitDraft {

    enum Phase {
        BAN, PICK, DONE
    }

    private final List<String> kits;
    private final UUID first;
    private final UUID second;
    private final DoubleSupplier random;
    private final Map<UUID, String> bans = new HashMap<>();
    private final Map<UUID, String> picks = new HashMap<>();
    private Phase phase;
    private String result;

    DuelKitDraft(List<String> kits, UUID first, UUID second, DoubleSupplier random) {
        if (kits.isEmpty()) {
            throw new IllegalArgumentException("No hay kits para elegir.");
        }
        this.kits = List.copyOf(kits);
        this.first = first;
        this.second = second;
        this.random = random;
        if (this.kits.size() == 1) {
            phase = Phase.DONE;
            result = this.kits.getFirst();
        } else {
            phase = Phase.BAN;
        }
    }

    Phase phase() {
        return phase;
    }

    String result() {
        return result;
    }

    List<String> kits() {
        return kits;
    }

    String banOf(UUID player) {
        return bans.get(player);
    }

    String pickOf(UUID player) {
        return picks.get(player);
    }

    boolean isBanned(String kit) {
        return bans.containsValue(kit);
    }

    /** Kits left after the bans; when the bans removed every kit, all of them come back. */
    List<String> available() {
        List<String> left = new ArrayList<>(kits);
        left.removeAll(bans.values());
        return left.isEmpty() ? kits : List.copyOf(left);
    }

    boolean ban(UUID player, String kit) {
        if (phase != Phase.BAN || !participant(player) || bans.containsKey(player) || !kits.contains(kit)) {
            return false;
        }
        bans.put(player, kit);
        if (bans.size() == 2) {
            phase = Phase.PICK;
            if (available().size() == 1) {
                finish(available().getFirst());
            }
        }
        return true;
    }

    boolean pick(UUID player, String kit) {
        if (phase != Phase.PICK || !participant(player) || picks.containsKey(player) || !available().contains(kit)) {
            return false;
        }
        picks.put(player, kit);
        if (picks.size() == 2) {
            String a = picks.get(first);
            String b = picks.get(second);
            finish(a.equals(b) || random.getAsDouble() < 0.5 ? a : b);
        }
        return true;
    }

    /** Fills the missing choices of the current phase at random (that phase timed out). */
    void autoComplete() {
        Phase current = phase;
        if (current == Phase.BAN) {
            for (UUID player : List.of(first, second)) {
                if (!bans.containsKey(player)) {
                    ban(player, randomOf(kits));
                }
            }
        } else if (current == Phase.PICK) {
            for (UUID player : List.of(first, second)) {
                if (!picks.containsKey(player)) {
                    pick(player, randomOf(available()));
                }
            }
        }
    }

    private void finish(String kit) {
        result = kit;
        phase = Phase.DONE;
    }

    private boolean participant(UUID player) {
        return first.equals(player) || second.equals(player);
    }

    private String randomOf(List<String> options) {
        int index = (int) Math.floor(random.getAsDouble() * options.size());
        return options.get(Math.max(0, Math.min(options.size() - 1, index)));
    }
}
