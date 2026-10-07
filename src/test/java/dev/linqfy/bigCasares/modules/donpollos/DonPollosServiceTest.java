package dev.linqfy.bigCasares.modules.donpollos;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DonPollosServiceTest {

    private final DonPollosService service = new DonPollosService();

    @Test
    void tradeRemovesCostThenGivesResult() {
        InMemoryInventory inventory = new InMemoryInventory();
        inventory.give("BREAD", 5);

        DonPollosResult result = service.trade(inventory, new DonPollosTrade(
            new DonPollosItem("BREAD", 3), new DonPollosItem("BREAD", 10)));

        assertTrue(result.success());
        assertEquals(12, inventory.count("BREAD"));
    }

    @Test
    void tradeWithoutFullCostChangesNothing() {
        InMemoryInventory inventory = new InMemoryInventory();
        inventory.give("BREAD", 2);

        DonPollosResult result = service.trade(inventory, new DonPollosTrade(
            new DonPollosItem("BREAD", 3), new DonPollosItem("BREAD", 10)));

        assertFalse(result.success());
        assertEquals(2, inventory.count("BREAD"));
    }

    @Test
    void buyingFriedChickenCostsFiveBreadAndGivesFour() {
        InMemoryInventory inventory = new InMemoryInventory();
        inventory.give("BREAD", 7);

        DonPollosResult result = service.buy(inventory, new DonPollosOffer(DonPollosProduct.POLLO_FRITO, 4, 5));

        assertTrue(result.success());
        assertEquals(2, inventory.count("BREAD"));
        assertEquals(4, inventory.products.get(DonPollosProduct.POLLO_FRITO));
    }

    @Test
    void buyingWithoutEnoughBreadChangesNothing() {
        InMemoryInventory inventory = new InMemoryInventory();
        inventory.give("BREAD", 2);

        DonPollosResult result = service.buy(inventory, new DonPollosOffer(DonPollosProduct.PICANTE, 1, 3));

        assertFalse(result.success());
        assertEquals(2, inventory.count("BREAD"));
        assertTrue(inventory.products.isEmpty());
    }

    private static final class InMemoryInventory implements DonPollosInventoryGateway {
        private final Map<String, Integer> items = new HashMap<>();
        private final Map<DonPollosProduct, Integer> products = new HashMap<>();

        @Override
        public int count(String material) {
            return items.getOrDefault(material, 0);
        }

        @Override
        public boolean remove(String material, int amount) {
            if (count(material) < amount) {
                return false;
            }
            items.merge(material, -amount, Integer::sum);
            return true;
        }

        @Override
        public void give(String material, int amount) {
            items.merge(material, amount, Integer::sum);
        }

        @Override
        public void giveProduct(DonPollosProduct product, int amount) {
            products.merge(product, amount, Integer::sum);
        }
    }
}
