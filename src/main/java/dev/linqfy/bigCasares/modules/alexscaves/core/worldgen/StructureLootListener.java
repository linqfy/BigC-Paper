package dev.linqfy.bigCasares.modules.alexscaves.core.worldgen;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.ListIterator;

/**
 * Chests of the mod's ruins use the module's datapack loot tables, where mod items are paper marked
 * {@code bigcasares:ac_loot} (see {@code tools/alexscaves/convert_structures.py}); this swaps them for the real items
 * when the loot is rolled, keeping the rolled amount.
 */
public final class StructureLootListener implements Listener {
    private static final NamespacedKey LOOT = new NamespacedKey("bigcasares", "ac_loot");
    private final ModMobService mobs;

    public StructureLootListener(ModMobService mobs) {
        this.mobs = mobs;
    }

    @EventHandler(ignoreCancelled = true)
    public void onLoot(LootGenerateEvent event) {
        if (!"alexscaves".equals(event.getLootTable().getKey().getNamespace())) {
            return;
        }
        List<ItemStack> loot = event.getLoot();
        ListIterator<ItemStack> iterator = loot.listIterator();
        while (iterator.hasNext()) {
            ItemStack stack = iterator.next();
            ItemMeta meta = stack == null ? null : stack.getItemMeta();
            String id = meta == null ? null : meta.getPersistentDataContainer().get(LOOT, PersistentDataType.STRING);
            if (id == null) {
                continue;
            }
            int amount = stack.getAmount();
            mobs.createItem(id, amount).ifPresentOrElse(iterator::set, iterator::remove);
        }
        // getLoot() is the event's own list, edited in place: setLoot(loot) would clear it first.
    }
}
