package dev.linqfy.bigCasares.modules.alexscaves.core.worldgen;

import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A world's populator list is a plain list that Paper's generation threads
 * iterate off the main thread, so it must not be mutated while chunks
 * generate. The module adds one slot per world, once per server run, and
 * enables or disables generation by swapping the slot's delegate.
 */
public final class PopulatorSlot extends BlockPopulator {
    private static final Map<UUID, PopulatorSlot> SLOTS = new ConcurrentHashMap<>();

    private final AtomicReference<BlockPopulator> delegate = new AtomicReference<>();

    private PopulatorSlot() {
    }

    /** Returns the world's slot, adding it to the world the first time only. */
    public static PopulatorSlot of(World world) {
        return SLOTS.computeIfAbsent(world.getUID(), ignored -> {
            PopulatorSlot slot = new PopulatorSlot();
            world.getPopulators().add(slot);
            return slot;
        });
    }

    public void set(BlockPopulator populator) {
        delegate.set(populator);
    }

    public void clear(BlockPopulator expected) {
        delegate.compareAndSet(expected, null);
    }

    @Override
    public void populate(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, LimitedRegion region) {
        BlockPopulator current = delegate.get();
        if (current != null) {
            current.populate(worldInfo, random, chunkX, chunkZ, region);
        }
    }
}
