package dev.linqfy.bigCasares.modules.tideframe.engine;

import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** The Vault bridge of the charge and pay actions, against a stand-in with Vault's method shapes; and the proxy message. */
class VaultEconomyTest {
    /** Same methods as Vault's Economy interface (only the ones Tideframe calls). */
    public interface FakeEconomy {
        boolean has(OfflinePlayer player, double amount);
        Response withdrawPlayer(OfflinePlayer player, double amount);
        Response depositPlayer(OfflinePlayer player, double amount);
    }
    /** Same shape as Vault's EconomyResponse. */
    public static final class Response {
        private final boolean ok;
        Response(boolean ok) { this.ok = ok; }
        public boolean transactionSuccess() { return ok; }
    }
    static final class Bank implements FakeEconomy {
        final Map<UUID, Double> balances = new HashMap<>(); boolean refuseDeposits;
        public boolean has(OfflinePlayer player, double amount) { return balances.getOrDefault(player.getUniqueId(), 0d) >= amount; }
        public Response withdrawPlayer(OfflinePlayer player, double amount) { balances.merge(player.getUniqueId(), -amount, Double::sum); return new Response(true); }
        public Response depositPlayer(OfflinePlayer player, double amount) {
            if (refuseDeposits) return new Response(false);
            balances.merge(player.getUniqueId(), amount, Double::sum); return new Response(true);
        }
    }
    private static OfflinePlayer player(UUID id) {
        return (OfflinePlayer) Proxy.newProxyInstance(OfflinePlayer.class.getClassLoader(), new Class<?>[]{OfflinePlayer.class},
            (proxy, method, args) -> method.getName().equals("getUniqueId") ? id : null);
    }

    @Test void chargesOnlyWhatTheBalanceCoversAndPays() {
        var bank = new Bank(); var id = UUID.randomUUID(); bank.balances.put(id, 50d);
        var economy = new VaultEconomy(FakeEconomy.class, bank);
        assertTrue(economy.available());
        assertTrue(economy.charge(player(id), new BigDecimal("30")));
        assertFalse(economy.charge(player(id), new BigDecimal("30")), "20 left: not enough, nothing taken");
        assertEquals(20d, bank.balances.get(id));
        assertTrue(economy.pay(player(id), new BigDecimal("5.5")));
        assertEquals(25.5d, bank.balances.get(id));
        bank.refuseDeposits = true;
        assertFalse(economy.pay(player(id), BigDecimal.ONE), "a refused transaction is reported");
    }

    @Test void withoutAnEconomyNothingMoves() {
        var none = new VaultEconomy(FakeEconomy.class, null);
        assertFalse(none.available());
        assertFalse(none.charge(player(UUID.randomUUID()), BigDecimal.ONE));
        assertFalse(none.pay(player(UUID.randomUUID()), BigDecimal.ONE));
        assertFalse(new VaultEconomy(FakeEconomy.class, "not an economy").available(), "a provider of the wrong type is ignored");
    }

    @Test void theProxyMessageIsConnectAndTheServerName() throws Exception {
        var in = new DataInputStream(new ByteArrayInputStream(ProxyConnect.connect("survival-1")));
        assertEquals("Connect", in.readUTF());
        assertEquals("survival-1", in.readUTF());
        assertEquals(0, in.available());
        assertEquals("BungeeCord", ProxyConnect.CHANNEL);
    }
}
