package dev.linqfy.bigCasares.modules.shop;

import dev.linqfy.bigCasares.items.CustomItemRegistry;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Buying or selling several lots with one click is one operation: one charge or payment, never part of it. */
class ShopServiceLotsTest {
    private static final java.util.UUID PLAYER_ID = java.util.UUID.randomUUID();
    private static final ShopEntry STONE = new ShopEntry("stone", 10, Material.STONE, null, 32, 64.0, 4.0, null, List.of());

    private final List<ItemStack> given = new ArrayList<>();
    private ItemStack[] storage = new ItemStack[36];
    private final Economy economy = new Economy(1000.0);
    private final ShopService service = new ShopService(new ShopItemResolver(new CustomItemRegistry()), economy);
    private final Player player = player();

    @Test
    void buyingFourLotsChargesOnceAndGivesFourCanonicalStacks() {
        ShopTransactionResult result = service.buy(player, STONE, 4);

        assertTrue(result.success());
        assertEquals(1, economy.charges, "one charge for the whole price");
        assertEquals(1000.0 - 256.0, economy.balance);
        assertEquals(4, given.size());
        given.forEach(stack -> {
            assertEquals(Material.STONE, stack.getType());
            assertEquals(32, stack.getAmount());
        });
        assertEquals("Compraste stone x4 por $256.", result.message());
    }

    @Test
    void notEnoughForAllTheLotsBuysNoneOfThem() {
        economy.balance = 200.0;
        ShopTransactionResult result = service.buy(player, STONE, 4);

        assertFalse(result.success());
        assertEquals(0, economy.charges);
        assertEquals(200.0, economy.balance);
        assertEquals(List.of(), given);
    }

    @Test
    void sellingLotsNeedsThemAllAndPaysOnce() {
        storage[0] = new ItemStack(Material.STONE, 64);
        storage[5] = new ItemStack(Material.STONE, 40);
        ShopTransactionResult tooMany = service.sell(player, STONE, 4);
        assertFalse(tooMany.success(), "104 stones are not four lots of 32");
        assertEquals(1000.0, economy.balance);

        ShopTransactionResult sold = service.sell(player, STONE, 3);
        assertTrue(sold.success());
        assertEquals(1000.0 + 12.0, economy.balance);
        int left = 0;
        for (ItemStack stack : storage) left += stack == null ? 0 : stack.getAmount();
        assertEquals(104 - 96, left);
        assertEquals("Vendiste stone x3 por $12.", sold.message());
    }

    @Test
    void theObserverHearsEachPlayerOperationOnceDoneOrRefused() {
        List<String> heard = new ArrayList<>();
        ShopService observed = new ShopService(new ShopItemResolver(new CustomItemRegistry()), economy, ignored -> 0,
            (who, result) -> heard.add((result.success() ? "ok " : "no ") + result.message()));
        observed.buy(player, STONE, 2);
        observed.sell(player, STONE, 1);
        assertEquals(List.of("ok Compraste stone x2 por $128.", "no No tenes suficientes items para vender."), heard);
        observed.buy(null, STONE);
        assertEquals(2, heard.size(), "a purchase with no player (the inventory preview) is not the player's operation");
    }

    @Test
    void chargesThePricingPriceAndRefusesPastTheDailyLimitWithoutCharging() {
        ShopEntry limited = new ShopEntry("stone", 10, Material.STONE, null, 32, 64.0, 4.0, null, List.of(), 3);
        ShopCatalog catalog = new ShopCatalog(List.of(new ShopCategory("blocks", "B", Material.STONE, 0, List.of(limited))));
        java.time.ZonedDateTime saturday = java.time.ZonedDateTime.parse("2026-11-07T21:00:00Z");
        var happy = new dev.linqfy.bigCasares.platform.HappyHour(true, java.util.Set.of(java.time.DayOfWeek.SATURDAY),
            java.time.LocalTime.of(20, 0), java.time.LocalTime.of(22, 0), 2, 0.25);
        ShopService priced = new ShopService(new ShopItemResolver(new CustomItemRegistry()), economy, ignored -> 0,
            (who, result) -> { }, new ShopPricing(catalog, ShopPricing.Offers.OFF, happy, () -> saturday),
            new ShopDailyLimits(new dev.linqfy.bigCasares.testsupport.MapPlayerStrings(), saturday::toLocalDate, catalog::keyOf));

        assertEquals("Compraste stone x2 por $96.", priced.buy(player, limited, 2).message(), "two lots at 64 less 25%");
        assertEquals(1000.0 - 96.0, economy.balance);
        ShopTransactionResult tooMany = priced.buy(player, limited, 2);
        assertFalse(tooMany.success());
        assertEquals("Hoy podés comprar 1 lote más de este producto.", tooMany.message());
        assertEquals(1000.0 - 96.0, economy.balance, "nothing charged");
        assertTrue(priced.buy(player, limited, 1).success());
        assertEquals("Llegaste al límite diario de este producto. Mañana se renueva.", priced.buy(player, limited, 1).message());
        storage[0] = new ItemStack(Material.STONE, 64);
        assertTrue(priced.sell(player, limited, 2).success(), "sales are never limited");
        assertEquals(1000.0 - 96.0 - 48.0 + 8.0, economy.balance, "and keep their catalog price in the happy hour");
    }

    @Test
    void ownedCountsOnlyMatchingItems() {
        storage[0] = new ItemStack(Material.STONE, 64);
        storage[3] = new ItemStack(Material.DIRT, 64);
        storage[7] = new ItemStack(Material.STONE, 5);
        assertEquals(69, service.owned(player, STONE));
    }

    @Test
    void oneLotKeepsTheOldMessagesAndNoLotIsRefused() {
        assertEquals("Compraste stone por $64.", service.buy(player, STONE).message());
        assertThrows(IllegalArgumentException.class, () -> service.buy(player, STONE, 0));
        assertThrows(IllegalArgumentException.class, () -> service.sell(player, STONE, 65));
    }

    private Player player() {
        PlayerInventory inventory = (PlayerInventory) Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(),
            new Class<?>[]{PlayerInventory.class}, (proxy, method, args) -> switch (method.getName()) {
                case "addItem" -> {
                    for (ItemStack stack : (ItemStack[]) args[0]) given.add(stack.clone());
                    yield new HashMap<Integer, ItemStack>();
                }
                case "getStorageContents" -> storage.clone();
                case "setStorageContents" -> {
                    storage = ((ItemStack[]) args[0]).clone();
                    yield null;
                }
                default -> throw new AssertionError("Unexpected inventory call: " + method.getName());
            });
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getInventory" -> inventory;
                case "getUniqueId" -> PLAYER_ID;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new AssertionError("Unexpected player call: " + method.getName());
            });
    }

    private static final class Economy extends ShopEconomyGateway {
        double balance;
        int charges;

        Economy(double balance) {
            super(null, null);
            this.balance = balance;
        }

        @Override
        public boolean has(Player player, double amount) {
            return balance >= amount;
        }

        @Override
        public boolean withdraw(Player player, double amount) {
            if (balance < amount) return false;
            balance -= amount;
            charges++;
            return true;
        }

        @Override
        public boolean deposit(Player player, double amount) {
            balance += amount;
            return true;
        }

        @Override
        public String format(double amount) {
            return "$" + (int) amount;
        }
    }
}
