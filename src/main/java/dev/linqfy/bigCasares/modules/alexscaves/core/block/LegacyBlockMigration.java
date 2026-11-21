package dev.linqfy.bigCasares.modules.alexscaves.core.block;

import java.util.Map;

/**
 * Moves the legacy note block states of blocks that changed base to their new state, one 16x16x16
 * section at a time. Pure logic over a grid of states, so the same code runs on chunks (block data)
 * and in tests (strings). Running it twice changes nothing the second time.
 *
 * @param <T> a block state
 */
public final class LegacyBlockMigration<T> {
    /** Read and write access to one section; coordinates are 0-15. */
    public interface Section<T> {
        /** Cheap test that the state may be a legacy one (e.g. its material is a note block). */
        boolean candidate(int x, int y, int z);

        T get(int x, int y, int z);

        void set(int x, int y, int z, T state);
    }

    private final Map<T, T> legacyToCurrent;

    public LegacyBlockMigration(Map<T, T> legacyToCurrent) {
        this.legacyToCurrent = Map.copyOf(legacyToCurrent);
    }

    public boolean isEmpty() {
        return legacyToCurrent.isEmpty();
    }

    public Map<T, T> mapping() {
        return legacyToCurrent;
    }

    /** Migrates one section; returns how many blocks changed. */
    public int migrate(Section<T> section) {
        int changed = 0;
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (!section.candidate(x, y, z)) {
                        continue;
                    }
                    T current = legacyToCurrent.get(section.get(x, y, z));
                    if (current != null) {
                        section.set(x, y, z, current);
                        changed++;
                    }
                }
            }
        }
        return changed;
    }
}
