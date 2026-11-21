package dev.linqfy.bigCasares.modules.duels;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/** Journal each Vault operation before calling the provider; unknown outcomes require staff review. */
final class DuelEscrow {
    private final DuelBank bank;
    private final Path journal;
    private final Map<UUID, Entry> entries = new HashMap<>();

    DuelEscrow(DuelBank bank) { this(bank, null); }

    DuelEscrow(DuelBank bank, Path journal) {
        this.bank = bank;
        this.journal = journal;
        if (journal != null && Files.exists(journal)) {
            Properties properties = new Properties();
            try (var input = Files.newInputStream(journal)) {
                properties.load(input);
                for (String key : properties.stringPropertyNames()) {
                    String[] parts = properties.getProperty(key).split(",", -1);
                    if (parts.length != 4) throw new IOException("Invalid escrow entry " + key);
                    entries.put(UUID.fromString(key), new Entry(UUID.fromString(parts[0]), Long.parseLong(parts[1]), State.valueOf(parts[2]), parts[3].isEmpty() ? null : UUID.fromString(parts[3])));
                }
            } catch (IOException | IllegalArgumentException failure) {
                throw new IllegalStateException("Cannot load duel escrow journal " + journal, failure);
            }
        }
    }

    boolean available() { return bank != null && !needsReconciliation(); }
    boolean needsReconciliation() { return entries.values().stream().anyMatch(e -> e.state != State.HELD); }
    boolean canAfford(UUID player, long wager) {
        return wager <= 0 || (available() && !entries.containsKey(player) && bank.balance(player) >= wager);
    }

    boolean collect(UUID first, UUID second, long wager) {
        if (wager <= 0) return true;
        if (!canAfford(first, wager) || !canAfford(second, wager) || wager > Long.MAX_VALUE / 2) return false;
        UUID group = UUID.randomUUID();
        if (!withdraw(first, group, wager)) return false;
        if (!withdraw(second, group, wager)) {
            refund(first, wager);
            return false;
        }
        return true;
    }

    private boolean withdraw(UUID player, UUID group, long amount) {
        entries.put(player, new Entry(group, amount, State.WITHDRAWING, player));
        save();
        if (!bank.withdraw(player, amount)) return false;
        entries.put(player, new Entry(group, amount, State.HELD, null));
        save();
        return true;
    }

    void payWinner(UUID winner, long wager) {
        if (wager <= 0 || bank == null) return;
        Entry winning = entries.get(winner);
        if (winning == null || winning.state != State.HELD || winning.amount != wager) return;
        var group = entries.entrySet().stream().filter(e -> e.getValue().group.equals(winning.group)).toList();
        if (group.size() != 2 || group.stream().anyMatch(e -> e.getValue().state != State.HELD)) return;
        for (var entry : group) entries.put(entry.getKey(), entry.getValue().state(State.PAYING, winner));
        save();
        if (!bank.deposit(winner, Math.multiplyExact(wager, 2))) return;
        group.forEach(entry -> entries.remove(entry.getKey()));
        save();
    }

    void refund(UUID player, long wager) {
        if (wager <= 0 || bank == null) return;
        Entry entry = entries.get(player);
        if (entry == null || entry.state != State.HELD || entry.amount != wager) return;
        entries.put(player, entry.state(State.REFUNDING, player));
        save();
        if (!bank.deposit(player, wager)) return;
        entries.remove(player);
        save();
    }

    void recover() {
        for (var entry : new ArrayList<>(entries.entrySet())) {
            if (entry.getValue().state == State.HELD) refund(entry.getKey(), entry.getValue().amount);
        }
    }

    private void save() {
        if (journal == null) return;
        try {
            Path parent = journal.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            Properties properties = new Properties();
            entries.forEach((id, e) -> properties.setProperty(id.toString(), e.group + "," + e.amount + "," + e.state + "," + (e.recipient == null ? "" : e.recipient)));
            Path temporary = Files.createTempFile(parent, "escrow-", ".tmp");
            try {
                try (var output = Files.newOutputStream(temporary)) { properties.store(output, "Duel escrow"); }
                try (FileChannel file = FileChannel.open(temporary, StandardOpenOption.WRITE)) { file.force(true); }
                Files.move(temporary, journal, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows")) {
                    try (FileChannel directory = FileChannel.open(parent, StandardOpenOption.READ)) { directory.force(true); }
                }
            } finally { Files.deleteIfExists(temporary); }
        } catch (IOException failure) { throw new UncheckedIOException("Cannot persist duel escrow", failure); }
    }

    private enum State { WITHDRAWING, HELD, PAYING, REFUNDING }
    private record Entry(UUID group, long amount, State state, UUID recipient) {
        Entry state(State next, UUID receiver) { return new Entry(group, amount, next, receiver); }
    }
}
