package dev.linqfy.bigCasares.modules.tideframe.engine;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

/**
 * Vault's economy for the charge and pay actions, reached by reflection (Tideframe compiles without Vault). Only the
 * public {@code net.milkbowl.vault.economy.Economy} interface is used: {@code has}, {@code withdrawPlayer} and
 * {@code depositPlayer}, whose responses report {@code transactionSuccess()}. Without Vault or without a registered
 * economy every call answers false and moves nothing. Looked up lazily; {@link #reset()} after a reload.
 */
final class VaultEconomy {
    private record Bound(Object provider, Method has, Method withdraw, Method deposit) { }
    private final Supplier<Bound> lookup;
    private volatile Bound bound;
    private volatile boolean checked;

    /** The server's economy, through Bukkit's services manager. */
    VaultEconomy() { this(VaultEconomy::fromServices); }
    /** {@code type}: the Economy interface; {@code provider}: an implementation of it (tests pass a stand-in). */
    VaultEconomy(Class<?> type, Object provider) { this(() -> bind(type, provider)); }
    private VaultEconomy(Supplier<Bound> lookup) { this.lookup = lookup; }

    boolean available() { return current() != null; }
    /** Takes {@code amount} only when the balance covers it; true when the economy confirmed the withdrawal. */
    boolean charge(OfflinePlayer player, BigDecimal amount) {
        var economy = current();
        if (economy == null) return false;
        try {
            if (!Boolean.TRUE.equals(economy.has().invoke(economy.provider(), player, amount.doubleValue()))) return false;
            return succeeded(economy.withdraw().invoke(economy.provider(), player, amount.doubleValue()));
        } catch (ReflectiveOperationException | RuntimeException failure) { return false; }
    }
    /** Adds {@code amount}; true when the economy confirmed the deposit. */
    boolean pay(OfflinePlayer player, BigDecimal amount) {
        var economy = current();
        if (economy == null) return false;
        try { return succeeded(economy.deposit().invoke(economy.provider(), player, amount.doubleValue())); }
        catch (ReflectiveOperationException | RuntimeException failure) { return false; }
    }
    /** Forget the economy (after /tideframe reload, or when economy plugins change). */
    void reset() { checked = false; bound = null; }

    private Bound current() {
        if (!checked) { checked = true; bound = lookup.get(); }
        return bound;
    }
    private static boolean succeeded(Object response) throws ReflectiveOperationException {
        if (response == null) return false;
        return Boolean.TRUE.equals(response.getClass().getMethod("transactionSuccess").invoke(response));
    }
    private static Bound bind(Class<?> type, Object provider) {
        if (type == null || provider == null || !type.isInstance(provider)) return null;
        try {
            return new Bound(provider, type.getMethod("has", OfflinePlayer.class, double.class),
                type.getMethod("withdrawPlayer", OfflinePlayer.class, double.class), type.getMethod("depositPlayer", OfflinePlayer.class, double.class));
        } catch (ReflectiveOperationException | LinkageError unavailable) { return null; }
    }
    private static Bound fromServices() {
        try {
            var vault = Bukkit.getPluginManager().getPlugin("Vault");
            if (vault == null || !vault.isEnabled()) return null;
            Class<?> type = Class.forName("net.milkbowl.vault.economy.Economy", true, vault.getClass().getClassLoader());
            var registration = Bukkit.getServicesManager().getRegistration(type);
            return registration == null ? null : bind(type, registration.getProvider());
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) { return null; }
    }
}
