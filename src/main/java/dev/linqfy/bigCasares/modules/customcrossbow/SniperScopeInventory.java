package dev.linqfy.bigCasares.modules.customcrossbow;

import java.util.List;
import java.util.ArrayList;

/** Immutable snapshot used by the network thread; it never accesses Bukkit inventory. */
record SniperScopeInventory<T>(int slot, T proxy) {
    T playerSlot(int index, T actual) {
        return index == slot ? proxy : actual;
    }

    T containerSlot(int container, int index, T actual) {
        return container == 0 && index == containerIndex() ? proxy : actual;
    }

    List<T> containerContents(int container, List<T> actual) {
        if (container != 0 || actual.size() <= containerIndex()) {
            return actual;
        }
        List<T> presented = new ArrayList<>(actual);
        presented.set(containerIndex(), proxy);
        return presented;
    }

    private int containerIndex() {
        return slot == 40 ? 45 : 36 + slot;
    }
}
