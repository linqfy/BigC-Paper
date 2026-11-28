package dev.linqfy.bigCasares.modules.shop;

import dev.linqfy.bigCasares.items.CustomItemRegistry;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopTradeSafetyTest {
    private static final ShopEntry STONE = new ShopEntry("stone", 0, Material.STONE, null, 32, 64.0, 4.0, "Piedra", List.of());
    private static final ShopEntry STICK = new ShopEntry("stick", 0, Material.STICK, null, 1, 1.0, 1.0, "Palo", List.of());

    /** The whole flow on one test account: every click is one debit and one delivery, or nothing at all. */
    @Test
    void eachPurchaseIsExactlyOneDebitAndOneDelivery() {
        TestShopPlayer account = new TestShopPlayer();
        Bank bank = new Bank(100.0);
        ShopService service = new ShopService(new ShopItemResolver(new CustomItemRegistry()), bank);

        assertTrue(service.trade(account.player, STONE, false).success());
        assertEquals(36.0, bank.balance, 1e-9);
        assertEquals(32, account.count(Material.STONE));
        assertEquals(1, bank.withdrawals);

        // A second click on the redrawn page is a second purchase, but there is money for none.
        var refused = service.trade(account.player, STONE, false);
        assertFalse(refused.success());
        assertEquals("Saldo insuficiente.", refused.message());
        assertEquals(36.0, bank.balance, 1e-9);
        assertEquals(32, account.count(Material.STONE));
        assertEquals(1, bank.withdrawals);
    }

    @Test
    void aFullInventoryKeepsTheExistingDropPolicyAndChargesOnce() {
        TestShopPlayer account = new TestShopPlayer();
        for (int i = 0; i < account.storage.length; i++) account.storage[i] = new ItemStack(Material.DIRT, 64);
        Bank bank = new Bank(100.0);
        ShopService service = new ShopService(new ShopItemResolver(new CustomItemRegistry()), bank);

        assertTrue(service.trade(account.player, STONE, false).success());
        assertEquals(36.0, bank.balance, 1e-9);
        assertEquals(1, account.dropped.size());
        assertEquals(Material.STONE, account.dropped.getFirst().getType());
        assertEquals(32, account.dropped.getFirst().getAmount());
    }

    @Test
    void aFailedDeliveryGivesTheMoneyBack() {
        TestShopPlayer account = new TestShopPlayer();
        account.failDelivery = true;
        Bank bank = new Bank(100.0);
        ShopService service = new ShopService(new ShopItemResolver(new CustomItemRegistry()), bank);

        var result = service.trade(account.player, STONE, false);
        assertFalse(result.success());
        assertEquals("No se pudo entregar la compra; se devolvió el dinero.", result.message());
        assertEquals(100.0, bank.balance, 1e-9);
        assertEquals(0, account.count(Material.STONE));
    }

    /** The inventory changed and then failed: it is put back exactly before the refund, so nothing stays free. */
    @Test
    void aDeliveryThatFailsAfterChangingTheInventoryIsUndoneBeforeTheRefund() {
        TestShopPlayer account = new TestShopPlayer();
        account.storage[0] = new ItemStack(Material.STONE, 10);
        account.failAfterAdding = true;
        Bank bank = new Bank(100.0);
        ShopService service = new ShopService(new ShopItemResolver(new CustomItemRegistry()), bank);

        assertFalse(service.trade(account.player, STONE, false).success());
        assertEquals(100.0, bank.balance, 1e-9);
        assertEquals(10, account.count(Material.STONE), "only the stone the player already had");
        assertTrue(account.dropped.isEmpty());
    }

    /** The leftover could not be dropped and nothing reached the world: the inventory is unchanged, so the refund is safe. */
    @Test
    void aFailedDropBeforeAnythingReachedTheWorldRefundsWithTheInventoryUnchanged() {
        TestShopPlayer account = new TestShopPlayer();
        for (int i = 0; i < account.storage.length - 1; i++) account.storage[i] = new ItemStack(Material.DIRT, 64);
        account.dropsBeforeFailure = 0;
        Bank bank = new Bank(100.0);
        ShopService service = new ShopService(new ShopItemResolver(new CustomItemRegistry()),  bank);
        var big = new ShopEntry("stone", 0, Material.STONE, null, 96, 64.0, 4.0, null, List.of());

        // 64 stone fit in the free slot before the 32 left over fail to drop; those 64 are taken back.
        assertFalse(service.trade(account.player, big, false).success());
        assertEquals(100.0, bank.balance, 1e-9);
        assertEquals(0, account.count(Material.STONE));
        assertTrue(account.dropped.isEmpty());
    }

    @Test
    void aWithdrawalErrorDeliversNothingAndIsNotRetried() {
        TestShopPlayer account = new TestShopPlayer();
        Bank bank = new Bank(100.0);
        bank.withdrawError = true;
        ShopService service = new ShopService(new ShopItemResolver(new CustomItemRegistry()), bank);

        assertFalse(service.trade(account.player, STONE, false).success());
        assertEquals(1, bank.withdrawCalls);
        assertEquals(0, account.count(Material.STONE));
        assertTrue(account.dropped.isEmpty());
    }

    @Test
    void invalidPricesAndAmountsNeverReachTheEconomy() {
        TestShopPlayer account = new TestShopPlayer();
        Bank bank = new Bank(100.0);
        bank.strict = true;
        ShopService service = new ShopService(new ShopItemResolver(new CustomItemRegistry()), bank);
        for (double price : new double[] {0, -64, Double.NaN, Double.POSITIVE_INFINITY}) {
            var entry = new ShopEntry("stone", 0, Material.STONE, null, 32, price, price, null, List.of());
            assertFalse(service.trade(account.player, entry, false).success());
            assertFalse(service.trade(account.player, entry, true).success());
            assertFalse(service.buy(entry).success());
        }
        var none = new ShopEntry("stone", 0, Material.STONE, null, 0, 64, 4, null, List.of());
        assertFalse(service.trade(account.player, none, false).success());
        assertEquals(100.0, bank.balance, 1e-9);
    }

    /** A stack tagged with a custom id (even one no longer registered) is never sold as the vanilla item. */
    @Test
    void sellingTakesOnlyStacksWithTheExactIdentity() {
        TestShopPlayer account = new TestShopPlayer();
        ItemStack ghost = new ItemStack(Material.STICK, 5);
        ItemStack plain = new ItemStack(Material.STICK, 1);
        account.storage[0] = ghost;
        account.storage[1] = plain;
        ShopItemResolver resolver = new ShopItemResolver(new CustomItemRegistry(),
            stack -> stack.getAmount() == 5 ? Optional.of("retired_wand") : Optional.empty());
        Bank bank = new Bank(0.0);
        ShopService service = new ShopService(resolver, bank);

        assertTrue(service.trade(account.player, STICK, true).success());
        assertEquals(1.0, bank.balance, 1e-9);
        assertEquals(5, account.storage[0].getAmount(), "the tagged stack stays");
        assertNull(account.storage[1], "the plain stick was sold");

        var second = service.trade(account.player, STICK, true);
        assertFalse(second.success(), "no plain stick left: the tagged stack does not count");
        assertEquals(1.0, bank.balance, 1e-9);
        assertEquals(5, account.storage[0].getAmount());
    }

    @Test
    void aRefusedSaleDepositPutsBackTheExactItems() {
        TestShopPlayer account = new TestShopPlayer();
        account.storage[3] = new ItemStack(Material.STICK, 7);
        Bank bank = new Bank(0.0);
        bank.refuseDeposits = true;
        ShopService service = new ShopService(vanillaResolver(), bank);

        var result = service.trade(account.player, STICK, true);
        assertEquals("No se pudo pagar la venta.", result.message());
        assertEquals(1, bank.depositCalls, "the sale reached the economy");
        assertEquals(0.0, bank.balance, 1e-9);
        assertEquals(7, account.storage[3].getAmount());
        assertEquals(Material.STICK, account.storage[3].getType());
    }

    /** The provider paid and then threw: the items must not come back, or the sale could be repeated for free. */
    @Test
    void aSaleDepositThatPaysAndThenFailsKeepsTheItemsTaken() {
        TestShopPlayer account = new TestShopPlayer();
        account.storage[0] = new ItemStack(Material.STONE, 32);
        Bank bank = new Bank(0.0);
        bank.creditThenError = true;
        ShopService service = new ShopService(vanillaResolver(), bank);

        var result = service.trade(account.player, STONE, true);
        assertFalse(result.success());
        assertEquals("No se confirmó el pago de la venta; avisale a un administrador.", result.message());
        assertEquals(4.0, bank.balance, 1e-9);
        assertEquals(0, account.count(Material.STONE), "paid once, so the stone stays sold");

        bank.creditThenError = false;
        assertFalse(service.trade(account.player, STONE, true).success(), "nothing left to sell a second time");
        assertEquals(4.0, bank.balance, 1e-9);
    }

    /** Delivery failed and the refund was refused or failed: the player is not told the money came back. */
    @Test
    void aRefundThatDoesNotGoThroughIsNotReportedAsDone() {
        for (boolean error : new boolean[] {false, true}) {
            TestShopPlayer account = new TestShopPlayer();
            account.failDelivery = true;
            Bank bank = new Bank(100.0);
            bank.refuseDeposits = !error;
            bank.depositError = error;
            ShopService service = new ShopService(new ShopItemResolver(new CustomItemRegistry()), bank);

            var result = service.trade(account.player, STONE, false);
            assertFalse(result.success());
            assertEquals("No se pudo entregar la compra ni devolver el dinero; avisale a un administrador.", result.message());
            assertEquals(36.0, bank.balance, 1e-9);
            assertEquals(0, account.count(Material.STONE));
        }
    }

    @Test
    void aRetiredCustomProductIsNeitherChargedNorPaid() {
        TestShopPlayer account = new TestShopPlayer();
        Bank bank = new Bank(1000.0);
        bank.strict = true;
        var tracker = new ShopEntry("tracker", 0, null, "tracker_compass", 1, 500, 90, "Rastreadora", List.of());
        ShopItemResolver resolver = new ShopItemResolver(new CustomItemRegistry(), stack -> Optional.of("tracker_compass"));
        account.storage[0] = new ItemStack(Material.COMPASS, 1);
        ShopService service = new ShopService(resolver, bank);

        assertFalse(service.trade(account.player, tracker, false).success());
        assertFalse(service.trade(account.player, tracker, true).success());
        assertEquals(1000.0, bank.balance, 1e-9);
        assertEquals(1, account.count(Material.COMPASS));
    }

    /** Without a server the registry cannot read item metadata; these stacks are plain vanilla items. */
    private static ShopItemResolver vanillaResolver() {
        return new ShopItemResolver(new CustomItemRegistry(), stack -> Optional.empty());
    }

    /** An economy double that records each call and can refuse or fail like a Vault provider. */
    private static final class Bank extends ShopEconomyGateway {
        double balance;
        int withdrawals;
        int withdrawCalls;
        boolean withdrawError;
        boolean refuseDeposits;
        boolean depositError;
        boolean creditThenError;
        boolean strict;
        int depositCalls;

        Bank(double balance) {
            super(null, null);
            this.balance = balance;
        }

        private void guard() {
            if (strict) throw new AssertionError("The economy must not be reached");
        }

        @Override public boolean has(double amount) { guard(); return balance >= amount; }
        @Override public boolean withdraw(double amount) { guard(); balance -= amount; return true; }
        @Override public boolean deposit(double amount) { guard(); balance += amount; return true; }
        @Override public boolean has(Player player, double amount) { guard(); return balance >= amount; }

        @Override
        public boolean withdraw(Player player, double amount) {
            guard();
            withdrawCalls++;
            if (withdrawError) throw new IllegalStateException("provider error");
            if (balance < amount) return false;
            balance -= amount;
            withdrawals++;
            return true;
        }

        @Override
        public boolean deposit(Player player, double amount) {
            guard();
            depositCalls++;
            if (depositError) throw new IllegalStateException("provider error");
            if (refuseDeposits) return false;
            balance += amount;
            if (creditThenError) throw new IllegalStateException("provider error after paying");
            return true;
        }

        @Override public String format(double amount) { return "$" + amount; }
        @Override public double balance(Player player) { return balance; }
    }
}
