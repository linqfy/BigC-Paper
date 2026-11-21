package dev.linqfy.bigCasares.modules.caichet;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Guarda la armadura que tenia puesta cada jugador al entrar a la dimension (en el propio jugador, asi sobrevive a
 * reinicios). Los dobles que copian a ese jugador se visten con esto.
 */
public final class ArmorSnapshot implements Listener {

    /** Casco, pechera, pantalones, botas. */
    private static final NamespacedKey[] KEYS = {
            new NamespacedKey("caichet", "armor_head"),
            new NamespacedKey("caichet", "armor_chest"),
            new NamespacedKey("caichet", "armor_legs"),
            new NamespacedKey("caichet", "armor_feet")};
    private static final NamespacedKey TAKEN = new NamespacedKey("caichet", "armor_taken");

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (player.getWorld().equals(PortalListener.dimension())) {
            take(player);
        }
    }

    /** Si ya estaba adentro (por ejemplo antes de instalar esto) y nunca se le tomo la armadura, se toma ahora. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.getWorld().equals(PortalListener.dimension())
                && !player.getPersistentDataContainer().has(TAKEN, PersistentDataType.BYTE)) {
            take(player);
        }
    }

    static void take(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        ItemStack[] armor = {
                player.getInventory().getHelmet(),
                player.getInventory().getChestplate(),
                player.getInventory().getLeggings(),
                player.getInventory().getBoots()};
        for (int i = 0; i < KEYS.length; i++) {
            ItemStack item = armor[i];
            if (item == null || item.getType().isAir()) {
                pdc.remove(KEYS[i]);
            } else {
                pdc.set(KEYS[i], PersistentDataType.BYTE_ARRAY, item.serializeAsBytes());
            }
        }
        pdc.set(TAKEN, PersistentDataType.BYTE, (byte) 1);
    }

    /** @return casco, pechera, pantalones y botas (null donde no tenia nada) */
    static ItemStack[] get(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        ItemStack[] armor = new ItemStack[KEYS.length];
        for (int i = 0; i < KEYS.length; i++) {
            byte[] data = pdc.get(KEYS[i], PersistentDataType.BYTE_ARRAY);
            if (data != null) {
                try {
                    armor[i] = ItemStack.deserializeBytes(data);
                } catch (RuntimeException ignored) {
                    // item de una version que ya no se puede leer: sin esa pieza
                }
            }
        }
        return armor;
    }
}
