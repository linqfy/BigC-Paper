package dev.linqfy.bigCasares.modules.shop;

import dev.linqfy.bigCasares.items.CustomItem;
import dev.linqfy.bigCasares.items.CustomItemRegistry;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopServiceTest {

    @Test
    void buysVanillaEntryWhenBalanceIsEnough() {
        ShopItemResolver resolver = new ShopItemResolver(new CustomItemRegistry());
        FakeEconomyGateway economy = new FakeEconomyGateway(100.0);
        ShopService service = new ShopService(resolver, economy);
        ShopEntry entry = new ShopEntry("stone", 10, Material.STONE, null, 32, 64.0, 4.0, null, List.of());

        ShopTransactionResult result = service.buy(entry);

        assertTrue(result.success());
        assertEquals(36.0, economy.balance());
        assertEquals(Material.STONE, result.stack().getType());
        assertEquals(32, result.stack().getAmount());
    }

    @Test
    void failsBuyWhenBalanceIsTooLow() {
        ShopItemResolver resolver = new ShopItemResolver(new CustomItemRegistry());
        FakeEconomyGateway economy = new FakeEconomyGateway(10.0);
        ShopService service = new ShopService(resolver, economy);
        ShopEntry entry = new ShopEntry("stone", 10, Material.STONE, null, 32, 64.0, 4.0, null, List.of());

        ShopTransactionResult result = service.buy(entry);

        assertFalse(result.success());
        assertEquals("Saldo insuficiente.", result.message());
        assertEquals(10.0, economy.balance());
    }

    @Test
    void sellsCustomEntryOnlyWhenMatchingRegistryItemExists() {
        CustomItemRegistry registry = new CustomItemRegistry();
        registry.register(new TestCustomItem());

        ItemStack custom = new TestCustomItem().createItemStack(1);
        ShopItemResolver resolver = taggedOnly(registry, custom);
        FakeEconomyGateway economy = new FakeEconomyGateway(0.0);
        ShopService service = new ShopService(resolver, economy);
        ShopEntry entry = new ShopEntry("copper_apple", 12, null, "copper_apple", 1, 1500.0, 50.0, null, List.of());

        ShopTransactionResult success = service.sell(List.of(custom), entry);
        ShopTransactionResult failure = service.sell(List.of(new ItemStack(Material.APPLE, 1)), entry);

        assertTrue(success.success());
        assertEquals(50.0, economy.balance());
        assertFalse(failure.success());
        assertEquals("No tenes suficientes items para vender.", failure.message());
    }

    @Test
    void customEntryRejectsVanillaStackOfSameBaseMaterial() {
        CustomItemRegistry registry = new CustomItemRegistry();
        registry.register(new TestCustomItem());
        ItemStack custom = new TestCustomItem().createItemStack(1);
        FakeEconomyGateway economy = new FakeEconomyGateway(0.0);
        ShopService service = new ShopService(taggedOnly(registry, custom), economy);
        ShopEntry entry = new ShopEntry("copper_apple", 12, null, "copper_apple", 1, 1500.0, 50.0, null, List.of());

        ShopTransactionResult result = service.sell(List.of(new ItemStack(Material.STICK, 64)), entry);

        assertFalse(result.success());
        assertEquals(0.0, economy.balance());
    }

    @Test
    void vanillaEntryRejectsCustomItemOfSameMaterial() {
        CustomItemRegistry registry = new CustomItemRegistry();
        ItemStack custom = new TestCustomItem().createItemStack(1);
        FakeEconomyGateway economy = new FakeEconomyGateway(0.0);
        ShopService service = new ShopService(taggedOnly(registry, custom), economy);
        ShopEntry entry = new ShopEntry("stick", 1, Material.STICK, null, 1, 1.0, 1.0, null, List.of());

        assertFalse(service.sell(List.of(custom), entry).success());
        assertTrue(service.sell(List.of(new ItemStack(Material.STICK, 1)), entry).success());
    }

    /** Identifies only {@code custom} as the copper apple, like a PDC-tagged stack in game. */
    private static ShopItemResolver taggedOnly(CustomItemRegistry registry, ItemStack custom) {
        return new ShopItemResolver(registry, stack -> stack == custom
            ? java.util.Optional.of("copper_apple") : java.util.Optional.empty());
    }

    @Test
    void buyCanReportOverflowAfterGrant() {
        ShopItemResolver resolver = new ShopItemResolver(new CustomItemRegistry());
        FakeEconomyGateway economy = new FakeEconomyGateway(100.0);
        AtomicInteger overflowRemoved = new AtomicInteger(2);
        ShopService service = new ShopService(resolver, economy, player -> overflowRemoved.get());
        ShopEntry entry = new ShopEntry("stone", 10, Material.STONE, null, 2, 64.0, 4.0, null, List.of());

        ShopTransactionResult result = service.buy(null, entry);

        assertTrue(result.success());
        assertEquals(2, result.overflowRemoved());
    }

    private static final class FakeEconomyGateway extends ShopEconomyGateway {

        private double balance;

        private FakeEconomyGateway(double balance) {
            super(null, null);
            this.balance = balance;
        }

        @Override
        public boolean has(double amount) {
            return balance >= amount;
        }

        @Override
        public boolean withdraw(double amount) {
            if (!has(amount)) {
                return false;
            }
            balance -= amount;
            return true;
        }

        @Override
        public boolean deposit(double amount) {
            balance += amount;
            return true;
        }

        double balance() {
            return balance;
        }
    }

    private static final class TestCustomItem implements CustomItem {

        @Override
        public String getId() {
            return "copper_apple";
        }

        @Override
        public int getCustomModelData() {
            return 5001;
        }

        @Override
        public NamespacedKey getItemKey() {
            return new NamespacedKey("bigcasares", "copper_apple");
        }

        @Override
        public ItemStack createItemStack(int amount) {
            return new ItemStack(Material.STICK, amount);
        }

        @Override
        public void onConsume(org.bukkit.entity.Player player, ItemStack consumedItem) {
        }
    }
}
