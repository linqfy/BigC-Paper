package dev.linqfy.bigCasares.modules.legendary;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LegendaryCraftingPlanTest {
    @Test void removedRecipesRejectBeforeMovingOrConsumingIngredients() {
        for (var weapon : List.of(LegendaryWeapon.VOID_KATANA, LegendaryWeapon.HYPERION)) {
            var storage = new ArrayList<Item>(java.util.Collections.nCopies(36, null));
            var matrix = matrix(weapon, 64);
            assertThrows(IllegalArgumentException.class, () -> LegendaryCraftingPlan.create(weapon, storage, matrix, ITEMS));
            assertEquals(0, total(storage));
            assertEquals(576, total(matrix));
        }
    }
    record Item(String material, int amount, boolean vanilla) { }
    private static final LegendaryCraftingPlan.Items<Item> ITEMS = new LegendaryCraftingPlan.Items<>() {
        public String material(Item i) { return i.material; }
        public int amount(Item i) { return i.amount; }
        public int maximum(Item i) { return 64; }
        public boolean eligible(Item i) { return i.vanilla; }
        public boolean similar(Item a, Item b) { return a.material.equals(b.material) && a.vanilla == b.vanilla; }
        public Item amount(Item i, int count) { return new Item(i.material, count, i.vanilla); }
    };
    private static List<Item> matrix(LegendaryWeapon weapon, int amount) {
        var matrix = new ArrayList<Item>();
        weapon.ingredients.forEach((material, count) -> {
            for (int n = 0; n < count; n++) matrix.add(new Item(material, amount, true));
        });
        return matrix;
    }
    private static int total(List<Item> inventory) { return inventory.stream().filter(java.util.Objects::nonNull).mapToInt(Item::amount).sum(); }

    @Test void allEightOriginalRecipesConsumeExactlyNineMaterialsAndNeverCreateWeapon() {
        for (var weapon : LegendaryWeapon.available()) {
            var storage = new ArrayList<Item>(java.util.Collections.nCopies(36, null));
            var plan = LegendaryCraftingPlan.create(weapon, storage, matrix(weapon, 1), ITEMS);
            assertEquals(9, total(plan.before())); assertEquals(0, total(plan.after()));
            assertEquals(0, total(storage));
        }
    }
    @Test void shiftClickConsumesOnlyOneRecipeAndReturnsUnconsumedStacksWithoutDropping() {
        var storage = new ArrayList<Item>(java.util.Collections.nCopies(36, null));
        storage.set(0, new Item("STONE", 64, true));
        var plan = LegendaryCraftingPlan.create(LegendaryWeapon.THUNDER_SPEAR, storage, matrix(LegendaryWeapon.THUNDER_SPEAR, 64), ITEMS);
        assertEquals(64 + 576, total(plan.before())); assertEquals(64 + 567, total(plan.after()));
        assertEquals(64, total(storage));
    }
    @Test void wrongMaterialsCustomItemsAndPlayerTwoByTwoGridCannotStartCraft() {
        var empty = new ArrayList<Item>(java.util.Collections.nCopies(36, null));
        var matrix = matrix(LegendaryWeapon.THUNDER_SPEAR, 1);
        matrix.set(0, new Item(matrix.getFirst().material, 1, false));
        assertThrows(IllegalArgumentException.class, () -> LegendaryCraftingPlan.create(LegendaryWeapon.THUNDER_SPEAR, empty, matrix, ITEMS));
        assertThrows(IllegalArgumentException.class, () -> LegendaryCraftingPlan.create(LegendaryWeapon.THUNDER_SPEAR, empty, matrix.subList(0, 4), ITEMS));
        matrix.set(0, new Item("TRIDENT", 1, true));
        assertThrows(IllegalArgumentException.class, () -> LegendaryCraftingPlan.create(LegendaryWeapon.THUNDER_SPEAR, empty, matrix, ITEMS));
    }
    @Test void fullStorageRejectsBeforeChangingOrConsumingAnyIngredient() {
        var storage = new ArrayList<>(java.util.Collections.nCopies(36, new Item("STONE", 64, true)));
        var matrix = matrix(LegendaryWeapon.THUNDER_SPEAR, 1);
        assertThrows(IllegalArgumentException.class, () -> LegendaryCraftingPlan.create(LegendaryWeapon.THUNDER_SPEAR, storage, matrix, ITEMS));
        assertEquals(36 * 64, total(storage)); assertEquals(9, total(matrix));
    }
}
