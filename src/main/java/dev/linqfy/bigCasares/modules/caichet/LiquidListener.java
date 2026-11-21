package dev.linqfy.bigCasares.modules.caichet;

import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.inventory.ItemStack;

public final class LiquidListener implements Listener {

    /** Vaciar un balde en la dimension: en vez del flujo normal, el liquido sube (ver {@link LiquidRiser}). */
    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        Block block = event.getBlock();
        if (!block.getWorld().equals(PortalListener.dimension())) {
            return;
        }
        Material bucket = event.getBucket();
        // solo los baldes de agua y lava suben; los de peces, ajolote o nieve quedan como en el juego normal
        if (bucket != Material.WATER_BUCKET && bucket != Material.LAVA_BUCKET) {
            return;
        }
        // agua en un bloque que se moja (cofre, escalera, losa...): se moja normal, no se reemplaza el bloque
        if (block.getBlockData() instanceof Waterlogged || !(block.isEmpty() || block.isReplaceable())) {
            return;
        }
        Material fluid = bucket == Material.LAVA_BUCKET ? Material.LAVA : Material.WATER;
        event.setCancelled(true);
        if (LiquidRiser.full()) {
            // tope de capas moviendose a la vez: el balde no se vacia
            event.getPlayer().sendActionBar(Component.text(
                    "Hay demasiado líquido moviéndose, esperá un momento."));
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.CREATIVE) {
            player.getInventory().setItem(event.getHand(), new ItemStack(Material.BUCKET));
        }
        block.getWorld().playSound(block.getLocation(), LiquidRiser.placeSound(fluid), 1f, 1f);
        LiquidRiser.start(block, fluid);
    }

    /** Sin flujo: el liquido de esta dimension no se mueve solo (la capa de arriba queda donde la dejamos). */
    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        Block from = event.getBlock();
        if (from.isLiquid() && from.getWorld().equals(PortalListener.dimension())) {
            event.setCancelled(true);
        }
    }
}
