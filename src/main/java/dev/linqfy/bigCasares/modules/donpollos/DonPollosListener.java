package dev.linqfy.bigCasares.modules.donpollos;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;

final class DonPollosListener implements Listener {

    private final DonPollosModule module;
    private final DonPollosEntityFactory factory;

    DonPollosListener(DonPollosModule module, DonPollosEntityFactory factory) {
        this.module = module;
        this.factory = factory;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEntityEvent event) {
        factory.variant(event.getRightClicked()).ifPresent(variant -> {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                module.handleInteraction(event.getPlayer(), (Villager) event.getRightClicked(), variant);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.KILL || cause == EntityDamageEvent.DamageCause.VOID) {
            return;
        }
        if (factory.isDonPollo(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTransform(EntityTransformEvent event) {
        if (factory.isDonPollo(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCareerChange(VillagerCareerChangeEvent event) {
        if (factory.isDonPollo(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAcquireTrade(VillagerAcquireTradeEvent event) {
        if (factory.isDonPollo(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPickup(EntityPickupItemEvent event) {
        if (factory.isDonPollo(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreed(EntityBreedEvent event) {
        if (factory.isDonPollo(event.getMother()) || factory.isDonPollo(event.getFather())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            factory.variant(entity).ifPresent(variant -> module.track((Villager) entity, variant));
        }
    }

    @EventHandler
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (factory.isDonPollo(entity)) {
                module.untrack(entity.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onRemoveFromWorld(EntityRemoveFromWorldEvent event) {
        if (factory.isDonPollo(event.getEntity())) {
            module.untrack(event.getEntity().getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        module.playerQuit(event.getPlayer());
    }
}
