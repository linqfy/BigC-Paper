package dev.linqfy.bigCasares.modules.missions;

import org.bukkit.Material;
import org.bukkit.block.data.type.Bed;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;

public final class MissionListener implements Listener {

    private final MissionModule module;

    public MissionListener(MissionModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        module.greet(event.getPlayer());
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        module.revalidatePassiveMissions(player);
        if (!event.isSneaking()) {
            return;
        }

        if (!(player.getLocation().clone().subtract(0, 1, 0).getBlock().getBlockData() instanceof Bed)) {
            return;
        }

        var bedBlock = player.getLocation().clone().subtract(0, 1, 0).getBlock();
        boolean hasSleepingPlayer = player.getWorld().getPlayers().stream()
            .anyMatch(other -> other.isSleeping() && other.getLocation().getBlock().equals(bedBlock));
        if (hasSleepingPlayer) {
            module.incrementMatchingMissions(player, MissionType.CROUCH_ON_SLEEPING_BED, 1);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            module.revalidatePassiveMissions(player);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
            && event.getItem() != null
            && event.getItem().getType() == Material.HONEYCOMB) {
            module.incrementMatchingMissions(event.getPlayer(), MissionType.WAX_BLOCK_COUNT, 1);
        }
        module.revalidatePassiveMissions(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        module.revalidatePassiveMissions(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inventory = event.getInventory();
        if (!(inventory.getViewers().stream().findFirst().orElse(null) instanceof Player player)) {
            return;
        }

        ItemStack result = event.getResult();
        if (result == null || !result.hasItemMeta() || !result.getItemMeta().hasDisplayName()) {
            return;
        }

        String renamedTo = result.getItemMeta().getDisplayName();
        module.completeRenameMission(player, result.getType(), renamedTo);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }

        ItemStack mainHand = killer.getInventory().getItemInMainHand();
        Material weapon = mainHand == null ? Material.AIR : mainHand.getType();

        if (event.getEntity() instanceof Player) {
            module.completeFinalHitMission(killer, weapon);
        }
        if (event.getEntity() instanceof IronGolem) {
            module.completeMobKillMission(killer, "IRON_GOLEM", weapon);
        }
        module.recordOutboundProgress(
            killer,
            MissionType.KILL_ENTITY_IN_BIOME,
            biomeKey(event.getEntity().getLocation()),
            0.0,
            event.getEntityType().name()
        );
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        Entity clicked = event.getRightClicked();
        if (item.getType() != Material.NAME_TAG || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return;
        }

        if (clicked instanceof Pig) {
            module.completePigNameMission(event.getPlayer(), item.getItemMeta().getDisplayName());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            module.revalidatePassiveMissions(player);
        }
    }

    @EventHandler
    public void onEntityToggleGlide(EntityToggleGlideEvent event) {
        if (event.getEntity() instanceof Player player) {
            module.revalidatePassiveMissions(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || event.getFrom().getBlockX() == event.getTo().getBlockX()
            && event.getFrom().getBlockY() == event.getTo().getBlockY()
            && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        Player player = event.getPlayer();
        String biome = biomeKey(event.getTo());
        module.recordOutboundProgress(player, MissionType.VISIT_BIOME, biome, 0.0, null);
        module.recordOutboundProgress(player, MissionType.VISIT_BIOME_SET, biome, 0.0, null);
        module.recordOutboundProgress(
            player, MissionType.ENTER_ENVIRONMENT, player.getWorld().getEnvironment().name(), 0.0, null);
        org.bukkit.Location spawn = player.getWorld().getSpawnLocation();
        double dx = player.getLocation().getX() - spawn.getX();
        double dz = player.getLocation().getZ() - spawn.getZ();
        module.recordOutboundProgress(
            player, MissionType.REACH_DISTANCE_FROM_SPAWN, null, Math.sqrt(dx * dx + dz * dz), null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLootGenerate(LootGenerateEvent event) {
        if (event.getEntity() instanceof Player player && event.getLootTable() != null) {
            module.recordOutboundProgress(
                player, MissionType.OPEN_LOOT_TABLE, event.getLootTable().getKey().toString(), 0.0, null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            module.recordOutboundProgress(
                event.getPlayer(), MissionType.CATCH_FISH_IN_BIOME,
                biomeKey(event.getPlayer().getLocation()), 0.0, null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        module.flush(event.getPlayer().getUniqueId());
    }

    private String biomeKey(org.bukkit.Location location) {
        return location.getBlock().getBiome().getKey().getKey();
    }
}
