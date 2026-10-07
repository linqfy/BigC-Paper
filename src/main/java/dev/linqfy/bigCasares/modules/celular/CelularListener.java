package dev.linqfy.bigCasares.modules.celular;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * F changes the video because the Java client waits for the server before swapping hands, so the phone does not bob.
 * Java's drop (Q) is predicted client-side and would play the re-equip animation; other keys never reach the server.
 * Bedrock players see the phone through Geyser with "Pobre" on the screen; it does nothing for them.
 */
final class CelularListener implements Listener {

    private final CelularService service;
    private final CelularItems items;
    private final CelularVillages villages;
    private final NamespacedKey recipeKey;
    private final Predicate<UUID> bedrock;

    CelularListener(CelularService service, CelularItems items, CelularVillages villages, NamespacedKey recipeKey,
                    Predicate<UUID> bedrock) {
        this.service = service;
        this.items = items;
        this.villages = villages;
        this.recipeKey = recipeKey;
        this.bedrock = bedrock;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (recipeKey != null) {
            event.getPlayer().discoverRecipe(recipeKey);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ItemStack phone = player.getInventory().getItemInMainHand();
        if (!items.isCelular(phone) || !CelularService.worksFor(isBedrock(player))) {
            return;
        }
        event.setCancelled(true);
        int index = service.navigate(items.video(phone), player.isSneaking());
        items.setVideo(phone, index);
        player.getInventory().setItemInMainHand(phone);
        showVideo(player, index, "");
    }

    @EventHandler(ignoreCancelled = true)
    public void onHold(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItem(event.getNewSlot());
        if (items.isCelular(held) && CelularService.worksFor(isBedrock(player))) {
            showVideo(player, items.video(held), CelularService.HINT);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        HumanEntity crafter = event.getWhoClicked();
        if (isPhoneRecipe(event.getRecipe()) && !CelularService.worksFor(isBedrock(crafter))) {
            crafter.sendMessage(Component.text(CelularService.BEDROCK_CRAFT_MESSAGE, NamedTextColor.YELLOW));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLoot(LootGenerateEvent event) {
        NamespacedKey table = event.getLootTable().getKey();
        if (!CelularService.isVillageChest(table.getNamespace(), table.getKey())) {
            return;
        }
        Optional<GeneratedStructure> village = villages.villageAt(event.getLootContext().getLocation());
        if (service.shouldAddVillagePhone(table.getNamespace(), table.getKey(), villages.alreadyGave(village))) {
            event.getLoot().add(items.create());
            villages.markGave(village);
        }
    }

    private boolean isPhoneRecipe(Recipe recipe) {
        return recipeKey != null && recipe instanceof Keyed keyed && recipeKey.equals(keyed.getKey());
    }

    private boolean isBedrock(HumanEntity player) {
        return bedrock.test(player.getUniqueId());
    }

    private void showVideo(Player player, int index, String hint) {
        player.sendActionBar(Component.text(service.actionBar(index), NamedTextColor.WHITE)
            .append(Component.text(hint, NamedTextColor.GRAY)));
    }
}
