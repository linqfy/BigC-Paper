package dev.linqfy.bigCasares.modules.villagermood;

import io.papermc.paper.event.player.PlayerTradeEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.MerchantInventory;

final class VillagerMoodListener implements Listener {

    private final VillagerMoodRuntime runtime;

    VillagerMoodListener(VillagerMoodRuntime runtime) {
        this.runtime = runtime;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTradeOpen(InventoryOpenEvent event) {
        if (event.getInventory() instanceof MerchantInventory inventory && event.getPlayer() instanceof Player player) {
            runtime.onTradeOpen(player, inventory);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTrade(PlayerTradeEvent event) {
        if (!runtime.isOrdinary(event.getVillager())) {
            return;
        }
        if (runtime.infiniteTrading()) {
            event.setIncreaseTradeUses(false);
        }
        runtime.onTrade(event.getPlayer(), event.getVillager());
    }

    /** Riot golems are summoned for free: they must not feed iron farms. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onGolemDeath(EntityDeathEvent event) {
        if (runtime.isRiotGolem(event.getEntity())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        runtime.forget(event.getPlayer().getUniqueId());
    }
}
