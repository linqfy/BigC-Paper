package dev.linqfy.bigCasares.modules.alexscaves.core.magnetism;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

/** Keeps the magnet index in sync with chunks and placed or broken neodymium blocks. */
public final class MagnetismListener implements Listener {
    private final MagnetismService magnetism;

    public MagnetismListener(MagnetismService magnetism) {
        this.magnetism = magnetism;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        magnetism.indexChunk(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        magnetism.unloadChunk(event.getWorld().getUID(), event.getChunk().getX(), event.getChunk().getZ());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlace(BlockPlaceEvent event) {
        magnetism.onBlockPlaced(event.getBlockPlaced());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) {
        magnetism.onBlockRemoved(event.getBlock());
    }
}
