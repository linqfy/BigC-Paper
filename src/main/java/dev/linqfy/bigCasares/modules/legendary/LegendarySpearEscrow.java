package dev.linqfy.bigCasares.modules.legendary;

import dev.linqfy.bigCasares.BigCasares;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.IOException;
import java.util.Base64;
import java.util.UUID;

/** A reserved inventory slot is a receipt, never a second usable weapon. */
final class LegendarySpearEscrow {
    private final LegendaryStateStore store;
    private final NamespacedKey receipt = new NamespacedKey("bigcasares", "legendary_receipt");
    private final NamespacedKey instance = new NamespacedKey("bigcasares", "legendary_instance");

    LegendarySpearEscrow(LegendaryStateStore store) {
        this.store = store;
    }

    boolean pending(UUID player) {
        return store.get(key(player)) != null;
    }

    boolean receipt(ItemStack item) {
        return item != null && item.hasItemMeta()
            && item.getItemMeta().getPersistentDataContainer().has(receipt, PersistentDataType.STRING);
    }

    boolean locked(Player player, ItemStack item) {
        if (receipt(item)) return true;
        String raw = store.get(key(player.getUniqueId()));
        if (raw == null || item == null || !item.hasItemMeta()) return false;
        String id = item.getItemMeta().getPersistentDataContainer().get(instance, PersistentDataType.STRING);
        if (id == null) return false;
        ItemStack original = ItemStack.deserializeBytes(Base64.getDecoder().decode(raw));
        return id.equals(original.getItemMeta().getPersistentDataContainer().get(instance, PersistentDataType.STRING));
    }

    ItemStack launch(Player player, int slot) throws IOException {
        if (pending(player.getUniqueId())) {
            throw new IllegalStateException("La lanza anterior todavía está regresando.");
        }
        ItemStack original = player.getInventory().getItem(slot);
        if (original == null || original.getAmount() != 1 || original.getType() != Material.NETHERITE_SPEAR
            || !original.hasItemMeta() || !"thunder_spear".equals(original.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey("bigcasares", "item_id"), PersistentDataType.STRING))) {
            throw new IllegalStateException("La lanza debe ocupar un único espacio.");
        }
        original = original.clone();
        String transaction = UUID.randomUUID().toString();
        var meta = original.getItemMeta();
        meta.getPersistentDataContainer().set(instance, PersistentDataType.STRING, transaction);
        original.setItemMeta(meta);
        // Persist an identifiable original before writing escrow. A crash at this
        // boundary leaves an ordinary owned spear, rather than an untracked copy.
        player.getInventory().setItem(slot, original);
        LegendaryPlayerSaveVerifier.save(player, instance, transaction);
        store.put(key(player.getUniqueId()), Base64.getEncoder().encodeToString(original.serializeAsBytes()));
        ItemStack token = new ItemStack(Material.BARRIER);
        meta = token.getItemMeta();
        meta.setDisplayName("§bThunder Spear — en vuelo");
        meta.setLore(java.util.List.of("§7Espacio reservado para su devolución."));
        meta.getPersistentDataContainer().set(receipt, PersistentDataType.STRING, transaction);
        token.setItemMeta(meta);
        player.getInventory().setItem(slot, token);
        LegendaryPlayerSaveVerifier.save(player, receipt, transaction);
        return original;
    }

    boolean restore(Player player) throws IOException {
        String raw = store.get(key(player.getUniqueId()));
        if (raw == null) {
            return true;
        }
        ItemStack original = ItemStack.deserializeBytes(Base64.getDecoder().decode(raw));
        String transaction = original.getItemMeta().getPersistentDataContainer().get(instance, PersistentDataType.STRING);
        int reserved = -1;
        boolean alreadyOwned = false;
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item == null || !item.hasItemMeta()) {
                continue;
            }
            var pdc = item.getItemMeta().getPersistentDataContainer();
            if (transaction.equals(pdc.get(instance, PersistentDataType.STRING))) {
                alreadyOwned = true;
            }
            if (transaction.equals(pdc.get(receipt, PersistentDataType.STRING))) {
                reserved = slot;
            }
        }
        int empty = player.getInventory().firstEmpty();
        var decision = LegendaryReturnPolicy.decide(alreadyOwned, reserved >= 0, empty >= 0);
        if (decision == LegendaryReturnPolicy.Decision.ACKNOWLEDGE_EXISTING) {
            if (reserved >= 0) {
                player.getInventory().setItem(reserved, null);
            }
        } else if (decision == LegendaryReturnPolicy.Decision.REPLACE_RECEIPT) {
            player.getInventory().setItem(reserved, original);
        } else {
            if (decision == LegendaryReturnPolicy.Decision.KEEP_PENDING) {
                player.sendActionBar(net.kyori.adventure.text.Component.text("Libera un espacio para recuperar Thunder Spear."));
                return false;
            }
            player.getInventory().setItem(empty, original);
        }
        // A restart before acknowledgment finds the delivered instance and
        // acknowledges it, instead of creating another weapon.
        LegendaryPlayerSaveVerifier.save(player, instance, transaction);
        store.put(key(player.getUniqueId()), null);
        return true;
    }

    private static String key(UUID player) {
        return "spear." + player;
    }
}
