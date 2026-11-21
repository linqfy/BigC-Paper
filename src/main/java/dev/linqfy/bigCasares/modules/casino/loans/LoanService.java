package dev.linqfy.bigCasares.modules.casino.loans;

import dev.linqfy.bigCasares.modules.casino.CasinoBank;
import dev.linqfy.bigCasares.modules.casino.CasinoFiles;
import dev.linqfy.bigCasares.modules.casino.CasinoSettings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.ToDoubleFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Loans plus the chests each debtor has opened lately, which is what the collector goes after. */
public final class LoanService {

    private static final int REMEMBERED_CHESTS = 24;

    private final CasinoSettings.Loans settings;
    private final CasinoBank bank;
    private final LoanBook book;
    private final File file;
    private final Logger logger;
    private final LongSupplier clock;
    private final Map<UUID, Deque<ChestRef>> chests = new HashMap<>();
    private ToDoubleFunction<UUID> creditRating = ignored -> 1.0;

    public record ChestRef(String world, int x, int y, int z) {
        public Location location() {
            World resolved = Bukkit.getWorld(world);
            return resolved == null ? null : new Location(resolved, x, y, z);
        }
    }

    public LoanService(CasinoSettings.Loans settings, CasinoBank bank, File file, Logger logger, LongSupplier clock) {
        this.settings = settings;
        this.bank = bank;
        this.book = new LoanBook(settings.lateFeePerHour(), settings.maxMultiplier());
        this.file = file;
        this.logger = logger;
        this.clock = clock;
        load();
    }

    public CasinoSettings.Loans settings() {
        return settings;
    }

    /** Multiplier on the interest of every offer for a player; 1 is the standard rate. */
    public void creditRating(ToDoubleFunction<UUID> rating) {
        creditRating = rating == null ? ignored -> 1.0 : rating;
    }

    public double creditRating(UUID player) {
        double rating = creditRating.applyAsDouble(player);
        return Double.isFinite(rating) && rating >= 0 ? rating : 1.0;
    }

    /** The offer as Don Casares quotes it to this player. */
    public LoanOffer offerFor(UUID player, LoanOffer offer) {
        return offer.withInterestMultiplier(creditRating(player));
    }

    public Optional<Loan> loan(UUID player) {
        return book.loan(player);
    }

    public List<Loan> loans() {
        return List.copyOf(book.loans());
    }

    public long balance(UUID player) {
        return book.balance(player, clock.getAsLong());
    }

    public boolean overdue(UUID player) {
        return book.overdue(player, clock.getAsLong());
    }

    /** Pays out the principal. Fails when the player already owes money. */
    public Loan take(UUID player, LoanOffer offer) {
        Loan loan = book.take(player, offer, clock.getAsLong());
        if (!bank.deposit(player, offer.principal())) {
            book.pay(player, Long.MAX_VALUE, clock.getAsLong());
            throw new IllegalStateException("El banco no pudo acreditar el préstamo");
        }
        save();
        return loan;
    }

    /** Takes up to {@code amount} from the player's balance. Returns what was paid. */
    public long repayFromBalance(UUID player, long amount) {
        long due = Math.min(amount, balance(player));
        due = Math.min(due, bank.balance(player));
        if (due <= 0 || !bank.withdraw(player, due)) {
            return 0;
        }
        long used = book.pay(player, due, clock.getAsLong());
        save();
        return used;
    }

    /** Credits value seized by the collector. */
    public long credit(UUID player, long amount) {
        long used = book.pay(player, amount, clock.getAsLong());
        save();
        return used;
    }

    public long surcharge(UUID player) {
        long amount = book.surcharge(player, settings.punchSurcharge());
        save();
        return amount;
    }

    public void rememberChest(UUID player, Location location) {
        Deque<ChestRef> recent = chests.computeIfAbsent(player, ignored -> new ArrayDeque<>());
        ChestRef ref = new ChestRef(location.getWorld().getName(), location.getBlockX(), location.getBlockY(),
            location.getBlockZ());
        recent.remove(ref);
        recent.addFirst(ref);
        while (recent.size() > REMEMBERED_CHESTS) {
            recent.removeLast();
        }
    }

    public List<ChestRef> chests(UUID player) {
        return List.copyOf(chests.getOrDefault(player, new ArrayDeque<>()));
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Loan loan : book.loans()) {
            String path = "loans." + loan.debtor();
            yaml.set(path + ".principal", loan.principal());
            yaml.set(path + ".owed", loan.owed());
            yaml.set(path + ".paid", loan.paid());
            yaml.set(path + ".surcharges", loan.surcharges());
            yaml.set(path + ".taken-at", loan.takenAtMillis());
            yaml.set(path + ".due-at", loan.dueAtMillis());
        }
        for (Map.Entry<UUID, Deque<ChestRef>> entry : chests.entrySet()) {
            List<String> refs = new ArrayList<>();
            for (ChestRef ref : entry.getValue()) {
                refs.add(ref.world() + ";" + ref.x() + ";" + ref.y() + ";" + ref.z());
            }
            yaml.set("chests." + entry.getKey(), refs);
        }
        try {
            CasinoFiles.atomicSave(yaml, file);
        } catch (IOException failure) {
            logger.log(Level.SEVERE, "[Casino] Could not save loans", failure);
        }
    }

    private void load() {
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection loans = yaml.getConfigurationSection("loans");
        if (loans != null) {
            for (String id : loans.getKeys(false)) {
                ConfigurationSection entry = loans.getConfigurationSection(id);
                try {
                    book.restore(new Loan(UUID.fromString(id), entry.getLong("principal"), entry.getLong("owed"),
                        entry.getLong("paid"), entry.getLong("surcharges"), entry.getLong("taken-at"),
                        entry.getLong("due-at")));
                } catch (RuntimeException invalid) {
                    logger.warning("[Casino] Ignoring invalid loan " + id);
                }
            }
        }
        ConfigurationSection chestSection = yaml.getConfigurationSection("chests");
        if (chestSection != null) {
            for (String id : chestSection.getKeys(false)) {
                Deque<ChestRef> refs = new ArrayDeque<>();
                for (String raw : chestSection.getStringList(id)) {
                    String[] parts = raw.split(";");
                    if (parts.length == 4) {
                        try {
                            refs.addLast(new ChestRef(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                                Integer.parseInt(parts[3])));
                        } catch (NumberFormatException ignored) {
                            // skip a corrupted entry
                        }
                    }
                }
                try {
                    chests.put(UUID.fromString(id), refs);
                } catch (IllegalArgumentException ignored) {
                    // skip a corrupted entry
                }
            }
        }
    }
}
