package dev.linqfy.bigCasares.modules.donpollos;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

/**
 * El Balde de KFC dado vuelta: el premio fijo de ganarle al Don Pollo Boss. Se pone en la cabeza
 * (modelo donpollos:balde_kfc_casco) y mientras lo tenes puesto te da Velocidad II y Agilidad acuatica II.
 */
final class DonPollosKfcBucket implements org.bukkit.event.Listener {

    static final String ITEM_MODEL = "balde_kfc_casco";
    /** Agilidad acuatica II: cada nivel suma 1/3 de eficiencia de movimiento en el agua. */
    static final double DEPTH_STRIDER_II = 2.0 / 3.0;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final JavaPlugin plugin;
    private final NamespacedKey key;
    private BukkitTask task;

    DonPollosKfcBucket(JavaPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "balde_kfc_casco");
    }

    ItemStack create() {
        ItemStack bucket = new ItemStack(Material.PAPER);
        ItemMeta meta = bucket.getItemMeta();
        meta.displayName(LEGACY.deserialize("§c§lBalde de KFC dado vuelta"));
        meta.lore(List.of(
            LEGACY.deserialize("§7El premio por ganarle al Don Pollo Boss."),
            LEGACY.deserialize("§7Puesto en la cabeza:"),
            LEGACY.deserialize("§b Velocidad II"),
            LEGACY.deserialize("§b Agilidad acuatica II")));
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        meta.setUnbreakable(true);
        meta.addAttributeModifier(Attribute.WATER_MOVEMENT_EFFICIENCY, new AttributeModifier(
            new NamespacedKey(plugin, "balde_agilidad_acuatica"), DEPTH_STRIDER_II,
            AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HEAD));
        Enchantment depthStrider = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("depth_strider"));
        if (depthStrider != null) {
            meta.addEnchant(depthStrider, 2, true);
        }
        bucket.setItemMeta(meta);
        bucket.setData(DataComponentTypes.ITEM_MODEL, Key.key("donpollos", ITEM_MODEL));
        bucket.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(EquipmentSlot.HEAD).build());
        bucket.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
        return bucket;
    }

    boolean isBucket(ItemStack item) {
        return item != null && item.hasItemMeta()
            && item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** Cada segundo: Velocidad II a quien tenga el balde puesto (dura un poco mas, asi no parpadea). */
    void start() {
        try {
            create();
        } catch (RuntimeException broken) {
            plugin.getLogger().warning("No se pudo armar el Balde de KFC: " + broken);
        }
        task =Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (isBucket(player.getInventory().getHelmet())) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 50, 1, true, false, true));
                }
            }
        }, 20L, 20L);
    }

    /**
     * Click derecho con el balde en la mano: te lo pone en la cabeza (si ya tenias algo, lo cambia). En Bedrock un
     * papel no entra en el lugar del casco del inventario, asi que esta es la forma de ponerselo.
     */
    @org.bukkit.event.EventHandler
    public void onUse(org.bukkit.event.player.PlayerInteractEvent event) {
        org.bukkit.event.block.Action action = event.getAction();
        if (event.getHand() != EquipmentSlot.HAND || (action != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
            && action != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!isBucket(hand)) {
            return;
        }
        event.setCancelled(true);
        ItemStack helmet = player.getInventory().getHelmet();
        player.getInventory().setHelmet(hand);
        player.getInventory().setItemInMainHand(helmet);
        player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_ARMOR_EQUIP_LEATHER, 1f, 0.8f);
    }

    void stop() {
        if (task != null) {
            task.cancel();
        }
    }
}
