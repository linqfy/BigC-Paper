package dev.linqfy.bigCasares.modules.sift;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The decisions behind the Sift's moving lights: which block cell a light source shines from, when its light block
 * moves and when it must go. The mod's Singer and carried Soul Block light the surroundings through a client mod
 * (LambDynamicLights); the port stands in for it with {@code minecraft:light} blocks that follow the entity.
 *
 * <p>The rules, which {@link SiftDynamicLights} applies to a world:</p>
 * <ul>
 *   <li>A light block is only ever put in a cell that is free (air) or that already holds this source's own light,
 *       and is only ever removed if it is still a light block, so no other block is replaced or deleted.</li>
 *   <li>A source tries its candidate cells in order and shines from the first usable one (its own cell counts as usable
 *       while it still holds the light); a cell another source's light occupies is not free.</li>
 *   <li>The new light is placed before the old one is removed, so a moving source is never dark in between, and
 *       an unchanged source costs two reads and no writes.</li>
 *   <li>A source that has no usable cell, is released (despawned, unloaded, carrying nothing, out of range) or whose
 *       chunk unloads has its light removed; {@link #releaseAll()} removes them all.</li>
 * </ul>
 *
 * <p>The ledger knows nothing about Bukkit; the world is behind {@link Gateway}.</p>
 */
final class SiftLightLedger {

    /** A block cell of a world. */
    record Cell(UUID world, int x, int y, int z) {

        int chunkX() {
            return x >> 4;
        }

        int chunkZ() {
            return z >> 4;
        }
    }

    /** The world as the ledger needs it. */
    interface Gateway {

        /** The cell is in a loaded chunk, inside the height limits and holds air. */
        boolean isFree(Cell cell);

        /** The cell holds a light block of this level. */
        boolean isLight(Cell cell, int level);

        /** Puts a light block in the cell (which {@link #isFree} said was free or held our light); false if it could not. */
        boolean place(Cell cell, int level);

        /** Removes the light block of the cell, only if it still is one. */
        void remove(Cell cell);
    }

    private record Placed(Cell cell, int level) {
    }

    private final Gateway gateway;
    private final Map<UUID, Placed> placed = new HashMap<>();

    SiftLightLedger(Gateway gateway) {
        this.gateway = gateway;
    }

    /**
     * The cells a point's light can shine from, best first: the block at the point, then the one below, above and two
     * below (a head in a ceiling still lights from the body's cell).
     */
    static List<Cell> candidates(UUID world, double x, double y, double z) {
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);
        return List.of(new Cell(world, bx, by, bz), new Cell(world, bx, by - 1, bz), new Cell(world, bx, by + 1, bz),
            new Cell(world, bx, by - 2, bz));
    }

    /**
     * The sources that deserve a light: the nearest ones to a viewer, within range, at most {@code max}.
     *
     * @param distancesSquared source id to its squared distance from the nearest viewer
     */
    static Set<UUID> nearest(Map<UUID, Double> distancesSquared, double rangeSquared, int max) {
        List<Map.Entry<UUID, Double>> within = new ArrayList<>();
        for (Map.Entry<UUID, Double> entry : distancesSquared.entrySet()) {
            if (entry.getValue() <= rangeSquared) {
                within.add(entry);
            }
        }
        within.sort(Map.Entry.<UUID, Double>comparingByValue().thenComparing(Map.Entry.comparingByKey()));
        Set<UUID> chosen = new LinkedHashSet<>();
        for (int i = 0; i < within.size() && i < max; i++) {
            chosen.add(within.get(i).getKey());
        }
        return chosen;
    }

    /** Moves the source's light to the first usable candidate, or removes it when none is. */
    void update(UUID source, List<Cell> candidates, int level) {
        Placed current = placed.get(source);
        Cell target = null;
        for (Cell candidate : candidates) {
            boolean own = current != null && current.cell().equals(candidate);
            if (own ? gateway.isLight(candidate, current.level()) || gateway.isFree(candidate) : gateway.isFree(candidate)) {
                target = candidate;
                break;
            }
        }
        if (target == null) {
            release(source);
            return;
        }
        if (current != null && current.cell().equals(target)) {
            if (current.level() == level && gateway.isLight(target, level)) {
                return;
            }
            if (gateway.place(target, level)) {
                placed.put(source, new Placed(target, level));
            } else {
                placed.remove(source);
                gateway.remove(target);
            }
            return;
        }
        if (!gateway.place(target, level)) {
            release(source);
            return;
        }
        placed.put(source, new Placed(target, level));
        if (current != null) {
            gateway.remove(current.cell());
        }
    }

    /** Removes the source's light, if it has one. */
    void release(UUID source) {
        Placed old = placed.remove(source);
        if (old != null) {
            gateway.remove(old.cell());
        }
    }

    /** Removes the lights of every source not in {@code keep}. */
    void releaseExcept(Set<UUID> keep) {
        for (UUID source : List.copyOf(placed.keySet())) {
            if (!keep.contains(source)) {
                release(source);
            }
        }
    }

    /** Removes the lights placed in a chunk that is about to unload. */
    void releaseChunk(UUID world, int chunkX, int chunkZ) {
        for (Map.Entry<UUID, Placed> entry : List.copyOf(placed.entrySet())) {
            Cell cell = entry.getValue().cell();
            if (cell.world().equals(world) && cell.chunkX() == chunkX && cell.chunkZ() == chunkZ) {
                release(entry.getKey());
            }
        }
    }

    /** Removes every light. */
    void releaseAll() {
        for (UUID source : List.copyOf(placed.keySet())) {
            release(source);
        }
    }

    int size() {
        return placed.size();
    }

    boolean hasLight(UUID source) {
        return placed.containsKey(source);
    }

    /** The cell the source shines from, or null. */
    Cell cellOf(UUID source) {
        Placed light = placed.get(source);
        return light == null ? null : light.cell();
    }
}
