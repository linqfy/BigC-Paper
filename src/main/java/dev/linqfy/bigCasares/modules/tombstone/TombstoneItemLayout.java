package dev.linqfy.bigCasares.modules.tombstone;

import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;

final class TombstoneItemLayout {
    static final int STORAGE_SLOTS = 36;
    static final int ARMOR_START = 36;
    static final int OFFHAND_SLOT = 40;
    static final int CURSOR_SLOT = 41;
    static final int SNAPSHOT_SIZE = 42;

    private TombstoneItemLayout() {
    }

    static List<ItemStack> snapshot(
        ItemStack[] storage,
        ItemStack[] armor,
        ItemStack offhand,
        ItemStack cursor
    ) {
        List<ItemStack> items = new ArrayList<>(Collections.nCopies(SNAPSHOT_SIZE, null));
        for (int slot = 0; slot < Math.min(STORAGE_SLOTS, storage.length); slot++) {
            items.set(slot, cloneOrNull(storage[slot]));
        }
        for (int slot = 0; slot < Math.min(4, armor.length); slot++) {
            items.set(ARMOR_START + slot, cloneOrNull(armor[slot]));
        }
        items.set(OFFHAND_SLOT, cloneOrNull(offhand));
        items.set(CURSOR_SLOT, cloneOrNull(cursor));
        return Collections.unmodifiableList(items);
    }

    /**
     * Keeps from the slot layout only what the death really drops, consuming each drop once, and appends the drops
     * that match no slot (added or changed by other listeners). Items removed from the drops on purpose (curse of
     * vanishing, items kept by other modules) are therefore not resurrected, and no drop is stored twice. The cursor
     * is not part of the vanilla drops, so it is always kept, but still consumes a matching drop if one exists.
     */
    static List<ItemStack> claimDrops(
        List<ItemStack> layout,
        Collection<ItemStack> drops,
        BiPredicate<ItemStack, ItemStack> similar
    ) {
        List<ItemStack> pool = presentClones(drops);
        List<ItemStack> claimed = new ArrayList<>(layout.size() + pool.size());
        for (int slot = 0; slot < layout.size(); slot++) {
            ItemStack item = cloneOrNull(layout.get(slot));
            if (item == null) {
                claimed.add(null);
                continue;
            }
            int taken = consume(pool, item, item.getAmount(), similar);
            if (slot == CURSOR_SLOT) taken = item.getAmount();
            claimed.add(withAmount(item, taken));
        }
        claimed.addAll(pool);
        return Collections.unmodifiableList(claimed);
    }

    /** Removes from the slot layout the items another listener asked to keep in the inventory. */
    static List<ItemStack> withoutKept(
        List<ItemStack> layout,
        Collection<ItemStack> kept,
        BiPredicate<ItemStack, ItemStack> similar
    ) {
        List<ItemStack> pool = presentClones(kept);
        List<ItemStack> remaining = new ArrayList<>(layout.size());
        for (ItemStack original : layout) {
            ItemStack item = cloneOrNull(original);
            if (item == null) {
                remaining.add(null);
                continue;
            }
            int removed = consume(pool, item, item.getAmount(), similar);
            remaining.add(withAmount(item, item.getAmount() - removed));
        }
        return Collections.unmodifiableList(remaining);
    }

    /**
     * What stays in each player slot once the stored items are taken out. Only the snapshot slots are considered;
     * appended drops never came from the inventory.
     */
    static List<ItemStack> remainder(List<ItemStack> layout, List<ItemStack> stored) {
        List<ItemStack> remaining = new ArrayList<>(layout.size());
        for (int slot = 0; slot < layout.size(); slot++) {
            ItemStack original = cloneOrNull(layout.get(slot));
            ItemStack taken = slot < stored.size() ? stored.get(slot) : null;
            if (original == null || !isPresent(taken)) {
                remaining.add(original);
                continue;
            }
            remaining.add(withAmount(original, original.getAmount() - taken.getAmount()));
        }
        return Collections.unmodifiableList(remaining);
    }

    static boolean hasItems(List<ItemStack> items) {
        return items.stream().anyMatch(TombstoneItemLayout::isPresent);
    }

    static ItemStack cloneOrNull(ItemStack item) {
        return isPresent(item) ? item.clone() : null;
    }

    private static int consume(
        List<ItemStack> pool,
        ItemStack wanted,
        int amount,
        BiPredicate<ItemStack, ItemStack> similar
    ) {
        int taken = 0;
        for (int index = 0; index < pool.size() && taken < amount; ) {
            ItemStack candidate = pool.get(index);
            if (!similar.test(candidate, wanted)) {
                index++;
                continue;
            }
            int portion = Math.min(amount - taken, candidate.getAmount());
            taken += portion;
            if (portion >= candidate.getAmount()) {
                pool.remove(index);
            } else {
                candidate.setAmount(candidate.getAmount() - portion);
                index++;
            }
        }
        return taken;
    }

    private static List<ItemStack> presentClones(Collection<ItemStack> items) {
        List<ItemStack> clones = new ArrayList<>();
        if (items == null) return clones;
        for (ItemStack item : items) {
            ItemStack copy = cloneOrNull(item);
            if (copy != null) clones.add(copy);
        }
        return clones;
    }

    private static ItemStack withAmount(ItemStack item, int amount) {
        if (amount <= 0) return null;
        ItemStack copy = item.clone();
        copy.setAmount(amount);
        return copy;
    }

    private static boolean isPresent(ItemStack item) {
        return item != null && item.getType() != Material.AIR && item.getAmount() > 0;
    }
}
