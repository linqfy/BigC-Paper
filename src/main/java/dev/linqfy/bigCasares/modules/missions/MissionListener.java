package dev.linqfy.bigCasares.modules.missions;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.HorseJumpEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

public final class MissionListener implements Listener {

    private static final int ANVIL_RESULT_SLOT = 2;

    private final MissionModule module;
    private final NamespacedKey customEntityKey;

    public MissionListener(MissionModule module, Plugin plugin) {
        this.module = module;
        this.customEntityKey = new NamespacedKey(plugin, "custom_entity");
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        module.revalidatePassiveMissions(player);
        if (!event.isSneaking()) {
            return;
        }

        Block bedBlock = player.getLocation().clone().subtract(0, 1, 0).getBlock();
        if (!(bedBlock.getBlockData() instanceof Bed bed)) {
            return;
        }

        // A sleeping player sits on one half of the bed; standing on either half counts.
        Block otherHalf = bedBlock.getRelative(bed.getPart() == Bed.Part.HEAD
            ? bed.getFacing().getOppositeFace()
            : bed.getFacing());
        boolean hasSleepingPlayer = player.getWorld().getPlayers().stream()
            .filter(other -> other != player && other.isSleeping())
            .map(other -> other.getLocation().getBlock())
            .anyMatch(block -> block.equals(bedBlock) || block.equals(otherHalf));
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnvilTake(InventoryClickEvent event) {
        // Count the rename when the result is actually taken, not while it is only previewed.
        if (!(event.getInventory() instanceof AnvilInventory)
            || event.getRawSlot() != ANVIL_RESULT_SLOT
            || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType().isAir() || !result.hasItemMeta() || !result.getItemMeta().hasDisplayName()) {
            return;
        }
        if (event.getView() instanceof AnvilView anvil
            && player.getGameMode() != GameMode.CREATIVE
            && player.getLevel() < anvil.getRepairCost()) {
            return;
        }

        module.completeRenameMission(player, result.getType(), result.getItemMeta().getDisplayName());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
            && event.useInteractedBlock() != Event.Result.DENY
            && event.getItem() != null
            && event.getItem().getType() == Material.HONEYCOMB
            && isUnwaxedCopper(event.getClickedBlock())) {
            module.incrementMatchingMissions(event.getPlayer(), MissionType.WAX_BLOCK_COUNT, 1);
        }
        module.revalidatePassiveMissions(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Material placed = event.getBlockPlaced().getType();
        module.incrementWhere(player, MissionType.PLACE_BLOCK_COUNT,
            definition -> placed.name().equalsIgnoreCase(String.valueOf(definition.params().get("material"))), 1);
        module.revalidatePassiveMissions(player);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Entity victim = event.getEntity();
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }

        Material weapon = killer.getInventory().getItemInMainHand().getType();

        if (victim instanceof Player victimPlayer) {
            module.completeFinalHitMission(killer, weapon);
            if (victimPlayer != killer && module.areTeammates(killer, victimPlayer)) {
                module.recordSameTarget(killer, MissionType.KILL_SAME_TEAMMATE, victimPlayer.getUniqueId().toString());
            }
        } else {
            module.completeMobKillMission(killer, victim.getType().name(), weapon);
        }
        module.recordTargetEvent(killer, MissionType.KILL_TARGET, targetNames(victim));
        module.recordOutboundProgress(
            killer,
            MissionType.KILL_ENTITY_IN_BIOME,
            biomeKey(victim.getLocation()),
            0.0,
            event.getEntityType().name()
        );
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItem(event.getHand());
        if (item == null || item.getType() != Material.NAME_TAG || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return;
        }
        module.completeEntityNameMission(event.getPlayer(), event.getRightClicked(), item.getItemMeta().getDisplayName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            module.recordTargetEvent(player, MissionType.HIT_TARGET, targetNames(event.getEntity()));
            module.revalidatePassiveMissions(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemBreak(PlayerItemBreakEvent event) {
        ItemStack broken = event.getBrokenItem();
        if (!MissionTargets.isHelmet(broken.getType()) || !broken.containsEnchantment(Enchantment.BINDING_CURSE)) {
            return;
        }
        module.incrementWhere(event.getPlayer(), MissionType.BREAK_CURSED_HELMET, definition -> {
            Object material = definition.params().get("material");
            return material == null || broken.getType().name().equalsIgnoreCase(String.valueOf(material));
        }, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHorseJump(HorseJumpEvent event) {
        String horseType = event.getEntity().getType().name();
        for (Entity passenger : event.getEntity().getPassengers()) {
            if (passenger instanceof Player rider) {
                module.incrementWhere(rider, MissionType.HORSE_JUMP_COUNT, definition ->
                    horseType.equalsIgnoreCase(String.valueOf(definition.params().getOrDefault("entity", "HORSE"))), 1);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String target = MissionTargets.whisperTarget(event.getMessage());
        if (target == null) {
            return;
        }
        Player receiver = event.getPlayer().getServer().getPlayerExact(target);
        if (receiver == null || receiver == event.getPlayer()) {
            return;
        }
        module.recordSameTarget(event.getPlayer(), MissionType.WHISPER_SAME_PLAYER, receiver.getUniqueId().toString());
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

    /** Names a mission {@code target} param can refer to: custom entity id, player name or custom name. */
    private List<String> targetNames(Entity entity) {
        List<String> names = new ArrayList<>(3);
        String customId = entity.getPersistentDataContainer().get(customEntityKey, PersistentDataType.STRING);
        if (customId != null) {
            names.add(customId);
        }
        if (entity instanceof Player player) {
            names.add(player.getName());
        }
        String customName = entity.getCustomName();
        if (customName != null) {
            names.add(ChatColor.stripColor(customName));
        }
        return names;
    }

    private static boolean isUnwaxedCopper(Block block) {
        if (block == null) {
            return false;
        }
        String name = block.getType().name();
        return name.contains("COPPER") && !name.startsWith("WAXED_") && !name.contains("ORE")
            && block.getType() != Material.RAW_COPPER_BLOCK;
    }

    private String biomeKey(org.bukkit.Location location) {
        return location.getBlock().getBiome().getKey().getKey();
    }
}
