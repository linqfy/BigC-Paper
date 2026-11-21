package dev.linqfy.bigCasares.modules.alexscaves.core.block.remap;

import java.util.Arrays;
import java.util.Map;

/**
 * Global block state id (the id on the wire, {@code Block.BLOCK_STATE_REGISTRY}) to the id Java clients receive.
 * Applied once per id, never chained: a module note block becomes a leaves state X, and a player's real leaves in X
 * become X at distance 7, in the same pass. Immutable and lock-free, read from Netty threads.
 */
public final class StateIdRemap {
    private static final int NONE = -1;
    /** Ids are a few tens of thousands; a guard against a broken resolution building a huge table. */
    static final int MAX_ID = 1 << 20;

    private final int[] table;
    private final int size;

    private StateIdRemap(int[] table, int size) {
        this.table = table;
        this.size = size;
    }

    public static StateIdRemap of(Map<Integer, Integer> mapping) {
        int max = -1;
        for (Map.Entry<Integer, Integer> entry : mapping.entrySet()) {
            int from = entry.getKey();
            int to = entry.getValue();
            if (from < 0 || to < 0 || from >= MAX_ID || to >= MAX_ID) {
                throw new IllegalArgumentException("id de estado fuera de rango: " + from + " -> " + to);
            }
            if (from == to) {
                throw new IllegalArgumentException("estado mapeado a sí mismo: " + from);
            }
            max = Math.max(max, from);
        }
        int[] table = new int[max + 1];
        Arrays.fill(table, NONE);
        mapping.forEach((from, to) -> table[from] = to);
        return new StateIdRemap(table, mapping.size());
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public boolean remaps(int id) {
        return id >= 0 && id < table.length && table[id] != NONE;
    }

    /** The id to send instead of {@code id} ({@code id} itself when it is not remapped). */
    public int apply(int id) {
        if (id < 0 || id >= table.length) {
            return id;
        }
        int mapped = table[id];
        return mapped == NONE ? id : mapped;
    }

    /**
     * Rewrites a section palette (or any id list) in place.
     *
     * @return whether an entry changed
     */
    public boolean rewrite(int[] ids) {
        boolean changed = false;
        for (int i = 0; i < ids.length; i++) {
            int id = ids[i];
            if (id >= 0 && id < table.length && table[id] != NONE) {
                ids[i] = table[id];
                changed = true;
            }
        }
        return changed;
    }
}
