package dev.linqfy.bigCasares.modules.netheritequest;

import org.bukkit.GameMode;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.Set;

final class NetheriteQuestListener implements Listener {

    private static final Set<Material> NETHERITE_ARMOR = Set.of(
        Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE,
        Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS);

    private final NetheriteQuestRuntime runtime;
    private final boolean blockSmithingArmor;
    private final double maxStepBlocks;

    NetheriteQuestListener(NetheriteQuestRuntime runtime, boolean blockSmithingArmor, double maxStepBlocks) {
        this.runtime = runtime;
        this.blockSmithingArmor = blockSmithingArmor;
        this.maxStepBlocks = maxStepBlocks;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null && !(event.getEntity() instanceof Player)) {
            runtime.recordKill(killer, event.getEntity().getType().getKey().getKey());
        }
    }

    /** Blocks walked count the same in every dimension: the Nether gives no 8x shortcut. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!runtime.walking(player.getUniqueId())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || from.getWorld() != to.getWorld() || player.isInsideVehicle() || player.isGliding()
            || player.isFlying() || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        double blocks = WalkDistance.horizontal(from.getX(), from.getZ(), to.getX(), to.getZ(), maxStepBlocks);
        if (blocks > 0.0) {
            runtime.recordWalk(player, blocks);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        runtime.deliverPending(event.getPlayer());
    }

    /** Netherite armor only comes from the quests; netherite tools still upgrade normally. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSmithing(PrepareSmithingEvent event) {
        if (!blockSmithingArmor) {
            return;
        }
        ItemStack result = event.getResult();
        Recipe recipe = event.getInventory().getRecipe();
        if (result != null && NETHERITE_ARMOR.contains(result.getType())
            && recipe instanceof Keyed keyed && keyed.getKey().getNamespace().equals("minecraft")) {
            event.setResult(null);
        }
    }
}
