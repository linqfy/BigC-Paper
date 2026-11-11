package dev.linqfy.bigCasares.modules.customcrossbow;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CustomCrossbowChargeListener implements Listener {

    private final BigCasares plugin;
    private final CustomCrossbowData crossbowData;
    private final CustomCrossbowSettings settings;
    private final CustomCrossbowLoadService loadService;
    private final BukkitRuntimeRegistrations registrations;
    private final Map<UUID, BukkitTask> loadingTasks = new HashMap<>();

    public CustomCrossbowChargeListener(
        BigCasares plugin,
        CustomCrossbowData crossbowData,
        CustomCrossbowSettings settings,
        CustomCrossbowLoadService loadService,
        BukkitRuntimeRegistrations registrations
    ) {
        this.plugin = plugin;
        this.crossbowData = crossbowData;
        this.settings = settings;
        this.loadService = loadService;
        this.registrations = registrations;
    }

    public void shutdown() {
        for (BukkitTask task : loadingTasks.values()) {
            task.cancel();
        }
        loadingTasks.clear();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // RIGHT_CLICK_AIR is delivered pre-cancelled, so ignoreCancelled would drop it;
        // honour other plugins through the item-use result instead.
        if (event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND || !isRightClick(event.getAction())) {
            return;
        }

        if (tryStartLoading(event.getPlayer())) {
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
            event.setCancelled(true);
        }
    }

    public boolean tryStartLoading(org.bukkit.entity.Player player) {
        ItemStack crossbow = player.getInventory().getItemInMainHand();
        if (crossbow == null || crossbow.getType() != Material.CROSSBOW || isAlreadyCharged(crossbow)) {
            return false;
        }

        ItemStack offhand = player.getInventory().getItemInOffHand();
        var chargeType = CustomCrossbowRules.resolveCharge(offhand.getType());
        if (chargeType.isEmpty()) {
            return false;
        }
        if (chargeType.get() == CustomCrossbowChargeType.ECHO_SHARD && countEchoChargedCrossbows(player) >= settings.maxEchoChargedCrossbows()) {
            player.sendMessage("§cNo podes tener mas de " + settings.maxEchoChargedCrossbows() + " crossbows cargadas con Echo Shard.");
            return true;
        }

        int fireworkPower = fireworkPower(offhand);
        int chargeCount = chargeCount(crossbow);
        int quickChargeLevel = crossbow.getEnchantmentLevel(Enchantment.QUICK_CHARGE);
        boolean started = loadService.start(
            player.getUniqueId(),
            chargeType.get(),
            quickChargeLevel,
            offhand.getType(),
            offhand.getAmount(),
            fireworkPower,
            chargeCount
        );
        if (!started) {
            return true;
        }

        int loadTicks = CustomCrossbowRules.loadTicks(chargeType.get(), quickChargeLevel);
        player.playSound(player.getLocation(), Sound.ITEM_CROSSBOW_LOADING_START, 0.8f, 1.0f);
        ItemStack crossbowSnapshot = crossbow.clone();
        ItemStack offhandSnapshot = offhand.clone();
        BukkitTask task = registrations.scheduleDelayed(
            "charge-load",
            () -> completeLoading(player.getUniqueId(), loadTicks, crossbowSnapshot, offhandSnapshot),
            loadTicks
        );
        loadingTasks.put(player.getUniqueId(), task);
        return true;
    }

    private void completeLoading(UUID playerId, int elapsedTicks, ItemStack expectedCrossbow, ItemStack expectedOffhand) {
        loadingTasks.remove(playerId);
        org.bukkit.entity.Player player = plugin.getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            loadService.cancel(playerId);
            return;
        }

        ItemStack crossbow = player.getInventory().getItemInMainHand();
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (!sameStackIgnoringAmount(crossbow, expectedCrossbow) || !sameStackIgnoringAmount(offhand, expectedOffhand)) {
            loadService.cancel(playerId);
            return;
        }
        var result = loadService.tick(playerId, offhand.getType(), offhand.getAmount(), elapsedTicks);
        if (result.isEmpty() || crossbow.getType() != Material.CROSSBOW || isAlreadyCharged(crossbow)) {
            loadService.cancel(playerId);
            return;
        }

        CustomCrossbowLoadResult loadResult = result.get();
        crossbowData.writeCharge(crossbow, loadResult.chargeType(), loadResult.fireworkPower(), loadResult.chargeCount());
        player.getInventory().setItemInMainHand(crossbow);
        consumeOffhandItem(player, offhand, loadResult.remainingOffhandAmount());
        player.updateInventory();
        player.playSound(player.getLocation(), Sound.ITEM_CROSSBOW_LOADING_END, 1.0f, 1.0f);
    }

    private boolean sameStackIgnoringAmount(ItemStack current, ItemStack expected) {
        if (current == null || expected == null) {
            return current == expected;
        }
        ItemStack currentCopy = current.clone();
        ItemStack expectedCopy = expected.clone();
        currentCopy.setAmount(1);
        expectedCopy.setAmount(1);
        return currentCopy.isSimilar(expectedCopy);
    }

    private boolean isRightClick(Action action) {
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }

    private boolean isAlreadyCharged(ItemStack crossbow) {
        ItemMeta meta = crossbow.getItemMeta();
        return meta instanceof CrossbowMeta crossbowMeta && crossbowMeta.hasChargedProjectiles();
    }

    private int fireworkPower(ItemStack offhand) {
        ItemMeta meta = offhand.getItemMeta();
        return meta instanceof FireworkMeta fireworkMeta && fireworkMeta.hasPower() ? fireworkMeta.getPower() : 1;
    }

    private int chargeCount(ItemStack crossbow) {
        return crossbow.getEnchantmentLevel(Enchantment.MULTISHOT) > 0 ? 3 : 1;
    }

    private void consumeOffhandItem(org.bukkit.entity.Player player, ItemStack offhand, int remaining) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        if (remaining <= 0) {
            player.getInventory().setItemInOffHand(null);
            return;
        }
        offhand.setAmount(remaining);
        player.getInventory().setItemInOffHand(offhand);
    }

    private int countEchoChargedCrossbows(org.bukkit.entity.Player player) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (crossbowData.isEchoChargedCrossbow(stack)) {
                total += Math.max(1, stack.getAmount());
            }
        }
        if (crossbowData.isEchoChargedCrossbow(player.getInventory().getItemInOffHand())) {
            total++;
        }
        return total;
    }
}
