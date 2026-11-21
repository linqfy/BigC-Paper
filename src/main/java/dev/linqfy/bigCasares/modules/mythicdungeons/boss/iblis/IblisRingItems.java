package dev.linqfy.bigCasares.modules.mythicdungeons.boss.iblis;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Solomon's ring: a temporary fight item (a gold nugget with a name and a glint) tagged with the boss instance it
 * belongs to. It does nothing by itself (the fight polls who holds it) and it is taken back when the fight ends, when
 * its holder is seen outside the room, and, for a ring of a fight that no longer exists (its holder logged out
 * mid-fight), the next time that player stands in any Iblis room. Dropped rings are swept from the room.
 *
 * <p>Limitation: encounters cannot listen to events, so a ring put into a chest during the fight stays there as a
 * harmless named nugget until someone carries it into an Iblis room again.</p>
 */
final class IblisRingItems {

    /** Iblis fights alive in this server run; a ring of any other instance is stale. Server thread only. */
    private static final Set<String> LIVE = new HashSet<>();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final NamespacedKey key;
    private final String instance;

    IblisRingItems(JavaPlugin plugin, UUID bossInstanceId) {
        this.key = new NamespacedKey(plugin, "iblis_ring");
        this.instance = bossInstanceId.toString();
    }

    void open() {
        LIVE.add(instance);
    }

    void close() {
        LIVE.remove(instance);
    }

    ItemStack create() {
        ItemStack ring = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = ring.getItemMeta();
        meta.displayName(text("§6§lAnillo de Salomón"));
        meta.lore(List.of(
            text("§7El sello que encierra a los genios."),
            text("§eAgachate §7con el anillo en la mano"),
            text("§7junto a una vasija para armarla."),
            text("§8Desaparece al terminar la pelea.")));
        meta.setEnchantmentGlintOverride(true);
        meta.setMaxStackSize(1);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, instance);
        ring.setItemMeta(meta);
        return ring;
    }

    private static Component text(String legacy) {
        return LEGACY.deserialize(legacy).decoration(TextDecoration.ITALIC, false);
    }

    /** The instance a ring belongs to, or {@code null} when the stack is not a ring. */
    String tagOf(ItemStack stack) {
        if (stack == null || stack.getType() != Material.GOLD_NUGGET || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    boolean ours(ItemStack stack) {
        return instance.equals(tagOf(stack));
    }

    /** A ring of an Iblis fight that is over (or of another run of the server). */
    boolean stale(ItemStack stack) {
        String tag = tagOf(stack);
        return tag != null && !LIVE.contains(tag);
    }

    boolean holding(Player player) {
        PlayerInventory inventory = player.getInventory();
        return ours(inventory.getItemInMainHand()) || ours(inventory.getItemInOffHand());
    }

    boolean has(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (ours(stack)) {
                return true;
            }
        }
        return ours(player.getItemOnCursor());
    }

    /** Gives a ring; {@code false} when the inventory is full. */
    boolean give(Player player) {
        return player.getInventory().addItem(create()).isEmpty();
    }

    /** Takes back this fight's rings. Returns how many. */
    int stripOurs(Player player) {
        return strip(player, this::ours);
    }

    /** Takes back rings of fights that are over. Returns how many. */
    int stripStale(Player player) {
        return strip(player, this::stale);
    }

    private static int strip(Player player, Predicate<ItemStack> which) {
        int removed = 0;
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (contents[slot] != null && which.test(contents[slot])) {
                inventory.setItem(slot, null);
                removed++;
            }
        }
        ItemStack cursor = player.getItemOnCursor();
        if (!cursor.isEmpty() && which.test(cursor)) {
            player.setItemOnCursor(null);
            removed++;
        }
        return removed;
    }

    /** Removes dropped rings (this fight's and stale ones) around {@code center}. */
    int sweepDropped(World world, Location center, double radius) {
        int removed = 0;
        for (Item item : world.getNearbyEntitiesByType(Item.class, center, radius)) {
            ItemStack stack = item.getItemStack();
            if (ours(stack) || stale(stack)) {
                item.remove();
                removed++;
            }
        }
        return removed;
    }
}
