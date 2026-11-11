package dev.linqfy.bigCasares.modules.tombstone;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class TombstoneItemLayoutTest {

    @Test
    void preservesStorageGapsThenArmorOffhandAndCursorSlots() {
        ItemStack[] storage = new ItemStack[36];
        storage[0] = new ItemStack(Material.STONE);
        storage[8] = new ItemStack(Material.DIRT);
        ItemStack[] armor = new ItemStack[4];
        armor[2] = new ItemStack(Material.IRON_CHESTPLATE);
        ItemStack offhand = new ItemStack(Material.SHIELD);
        ItemStack cursor = new ItemStack(Material.APPLE);

        List<ItemStack> items = TombstoneItemLayout.snapshot(storage, armor, offhand, cursor);

        assertEquals(42, items.size());
        assertEquals(Material.STONE, items.get(0).getType());
        assertNull(items.get(1));
        assertEquals(Material.DIRT, items.get(8).getType());
        assertEquals(Material.IRON_CHESTPLATE, items.get(38).getType());
        assertEquals(Material.SHIELD, items.get(40).getType());
        assertEquals(Material.APPLE, items.get(41).getType());
    }

    private static final BiPredicate<ItemStack, ItemStack> SAME_TYPE = (a, b) -> a.getType() == b.getType();

    @Test
    void claimDropsSkipsItemsThatDoNotDropSoTheyAreNotResurrected() {
        List<ItemStack> layout = layout();
        layout.set(0, new ItemStack(Material.STONE, 10));
        layout.set(1, new ItemStack(Material.DIAMOND_SWORD));  // e.g. curse of vanishing, removed from the drops
        layout.set(38, new ItemStack(Material.IRON_CHESTPLATE));

        List<ItemStack> claimed = TombstoneItemLayout.claimDrops(layout,
            List.of(new ItemStack(Material.IRON_CHESTPLATE), new ItemStack(Material.STONE, 10)), SAME_TYPE);

        assertEquals(42, claimed.size());
        assertEquals(10, claimed.get(0).getAmount());
        assertNull(claimed.get(1));
        assertEquals(Material.IRON_CHESTPLATE, claimed.get(38).getType());
    }

    @Test
    void claimDropsConsumesEachDropOnceAcrossIdenticalSlots() {
        List<ItemStack> layout = layout();
        layout.set(0, new ItemStack(Material.STONE, 64));
        layout.set(1, new ItemStack(Material.STONE, 64));

        List<ItemStack> claimed = TombstoneItemLayout.claimDrops(layout,
            List.of(new ItemStack(Material.STONE, 64), new ItemStack(Material.STONE, 30)), SAME_TYPE);

        assertEquals(64, claimed.get(0).getAmount());
        assertEquals(30, claimed.get(1).getAmount());
        assertEquals(94, total(claimed));
    }

    @Test
    void claimDropsKeepsDropsWithoutASlotAndTheCursorOnce() {
        List<ItemStack> layout = layout();
        layout.set(TombstoneItemLayout.CURSOR_SLOT, new ItemStack(Material.APPLE, 3));

        List<ItemStack> claimed = TombstoneItemLayout.claimDrops(layout,
            List.of(new ItemStack(Material.APPLE, 3), new ItemStack(Material.PLAYER_HEAD)), SAME_TYPE);

        assertEquals(43, claimed.size());
        assertEquals(3, claimed.get(TombstoneItemLayout.CURSOR_SLOT).getAmount());
        assertEquals(Material.PLAYER_HEAD, claimed.get(42).getType());
        assertEquals(4, total(claimed));
    }

    @Test
    void claimDropsDoesNotModifyTheEventDrops() {
        List<ItemStack> layout = layout();
        layout.set(0, new ItemStack(Material.STONE, 5));
        ItemStack drop = new ItemStack(Material.STONE, 8);

        TombstoneItemLayout.claimDrops(layout, List.of(drop), SAME_TYPE);

        assertEquals(8, drop.getAmount());
    }

    @Test
    void withoutKeptRemovesOnlyTheKeptAmount() {
        List<ItemStack> layout = layout();
        layout.set(0, new ItemStack(Material.STONE, 10));
        layout.set(5, new ItemStack(Material.TOTEM_OF_UNDYING));

        List<ItemStack> remaining = TombstoneItemLayout.withoutKept(layout,
            List.of(new ItemStack(Material.STONE, 4), new ItemStack(Material.TOTEM_OF_UNDYING)), SAME_TYPE);

        assertEquals(6, remaining.get(0).getAmount());
        assertNull(remaining.get(5));
    }

    @Test
    void remainderLeavesOnlyWhatWasNotStored() {
        List<ItemStack> layout = layout();
        layout.set(0, new ItemStack(Material.STONE, 10));
        layout.set(1, new ItemStack(Material.DIAMOND_SWORD));
        layout.set(40, new ItemStack(Material.SHIELD));
        List<ItemStack> stored = new ArrayList<>(Collections.nCopies(42, null));
        stored.set(0, new ItemStack(Material.STONE, 7));
        stored.set(40, new ItemStack(Material.SHIELD));

        List<ItemStack> remaining = TombstoneItemLayout.remainder(layout, stored);

        assertEquals(3, remaining.get(0).getAmount());
        assertEquals(Material.DIAMOND_SWORD, remaining.get(1).getType());
        assertNull(remaining.get(40));
        assertFalse(TombstoneItemLayout.hasItems(remaining.subList(2, 40)));
    }

    private static List<ItemStack> layout() {
        return new ArrayList<>(Collections.nCopies(TombstoneItemLayout.SNAPSHOT_SIZE, null));
    }

    private static int total(List<ItemStack> items) {
        return items.stream().filter(java.util.Objects::nonNull).mapToInt(ItemStack::getAmount).sum();
    }
}
