package dev.linqfy.bigCasares.modules.nexus;

import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.entity.Player;

public final class NexusContainerListener implements Listener {
    private final NexusModule module;
    public NexusContainerListener(NexusModule module) { this.module = module; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player) || event.getInventory().getLocation() == null) return;
        Block block = event.getInventory().getLocation().getBlock();
        if (!module.canOpenContainer(player.getUniqueId(), block)) {
            event.setCancelled(true);
            player.sendMessage("§c[!] Este cofre está protegido por el Nexus de otro equipo.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!module.isProtectedContainer(event.getBlock())) return;
        if (!module.canUseContainer(event.getPlayer().getUniqueId(), event.getBlock())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c[!] Este cofre está protegido por el Nexus de otro equipo.");
        } else module.unregisterContainer(event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) { if (module.isProtectedContainer(event.getBlock())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) { event.blockList().removeIf(module::isProtectedContainer); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) { event.blockList().removeIf(module::isProtectedContainer); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(module::isProtectedContainer)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(module::isProtectedContainer)) event.setCancelled(true);
    }
}
