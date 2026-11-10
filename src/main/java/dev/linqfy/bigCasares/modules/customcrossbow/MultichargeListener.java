package dev.linqfy.bigCasares.modules.customcrossbow;

import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import io.papermc.paper.event.entity.EntityLoadCrossbowEvent;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;

import java.util.List;

final class MultichargeListener implements Listener {

    private final CrossbowEnchantmentData data;
    private final EchoArrowItem echoArrow;
    private final GoldenTippedAmethystArrowItem amethystArrow;
    private final BukkitRuntimeRegistrations registrations;

    MultichargeListener(CrossbowEnchantmentData data, EchoArrowItem echoArrow,
        GoldenTippedAmethystArrowItem amethystArrow, BukkitRuntimeRegistrations registrations) {
        this.data = data;
        this.echoArrow = echoArrow;
        this.amethystArrow = amethystArrow;
        this.registrations = registrations;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if ((event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
            || event.useItemInHand() == Event.Result.DENY
            || data.enchantment(event.getItem()) != CrossbowEnchantment.MULTICHARGE) {
            return;
        }
        if (event.getHand() != null) {
            ItemStack crossbow = event.getPlayer().getInventory().getItem(event.getHand());
            if (crossbow.containsEnchantment(Enchantment.MULTISHOT)) {
                event.setUseItemInHand(Event.Result.DENY);
                conflictMessage(event.getPlayer());
                return;
            }
            data.prepareNextShot(crossbow);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onConflictingLoad(EntityLoadCrossbowEvent event) {
        if (data.enchantment(event.getCrossbow()) == CrossbowEnchantment.MULTICHARGE
            && event.getCrossbow().containsEnchantment(Enchantment.MULTISHOT)) {
            event.setCancelled(true);
            if (event.getEntity() instanceof Player player) {
                conflictMessage(player);
            }
        }
    }

    private void conflictMessage(Player player) {
        player.sendMessage("§cMulticarga no es compatible con Multidisparo. Quitá Multidisparo primero.");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLoad(EntityLoadCrossbowEvent event) {
        if (!(event.getEntity() instanceof Player player)
            || data.enchantment(event.getCrossbow()) != CrossbowEnchantment.MULTICHARGE) {
            return;
        }
        ItemStack crossbow = event.getCrossbow();
        registrations.scheduleDelayed("multicharge-load", () -> fillMagazine(player, crossbow), 1L);
    }

    private void fillMagazine(Player player, ItemStack crossbow) {
        if (!player.isOnline() || data.rounds(crossbow) > 0
            || data.enchantment(crossbow) != CrossbowEnchantment.MULTICHARGE
            || !(crossbow.getItemMeta() instanceof CrossbowMeta meta)) {
            return;
        }
        List<ItemStack> loaded = meta.getChargedProjectiles();
        if (loaded.size() != 1 || !supported(loaded.getFirst())) {
            return;
        }
        ItemStack ammo = loaded.getFirst();
        int extra = player.getGameMode() == GameMode.CREATIVE ? 4 : reserveExtraAmmo(player, ammo);
        data.loadMagazine(crossbow, ammo, CrossbowEnchantmentRules.magazineSize(extra));
        player.sendActionBar(net.kyori.adventure.text.Component.text("Multicarga: " + data.rounds(crossbow) + "/5"));
    }

    private boolean supported(ItemStack item) {
        return (item.getType() == Material.ARROW || item.getType() == Material.TIPPED_ARROW
            || item.getType() == Material.SPECTRAL_ARROW)
            && !echoArrow.matches(item) && !amethystArrow.matches(item);
    }

    private int reserveExtraAmmo(Player player, ItemStack ammo) {
        int reserved = 0;
        // Storage and the offhand contain ammunition; armor slots never do.
        for (int slot = 0; slot <= 40 && reserved < 4; slot++) {
            if (slot >= 36 && slot != 40) {
                continue;
            }
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack == null || !stack.isSimilar(ammo)) {
                continue;
            }
            int taken = Math.min(4 - reserved, stack.getAmount());
            stack.setAmount(stack.getAmount() - taken);
            player.getInventory().setItem(slot, stack.getAmount() == 0 ? null : stack);
            reserved += taken;
        }
        return reserved;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player) || data.rounds(event.getBow()) <= 0) {
            return;
        }
        data.consumeRound(event.getBow());
        // Paper clears charged projectiles before this event. Restoring the
        // next paid round here keeps both the client model and native use ready.
        data.prepareNextShot(event.getBow());
        player.setCooldown(Material.CROSSBOW, CrossbowEnchantmentRules.SHOT_INTERVAL_TICKS);
        player.sendActionBar(net.kyori.adventure.text.Component.text("Multicarga: " + data.rounds(event.getBow()) + "/5"));
    }
}
