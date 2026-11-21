package dev.linqfy.bigCasares.modules.alexscaves.core.block;

import dev.linqfy.bigCasares.items.CustomItemRegistry;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;

import java.util.List;

/**
 * The vanilla petrified oak slab (unobtainable in survival) is the module's smooth limestone slab: the pack draws it
 * as such and the item places it. Breaking one would drop the vanilla slab item, so the drop becomes the module item
 * (the amount, 1 or 2, is kept). Slabs always drop themselves, so silk touch does not matter; creative fires no drop
 * event, so nothing drops there. Explosions, pistons and other paths that do not fire that event are covered by
 * {@link ItemSpawnEvent}: any vanilla petrified oak slab item entering the world that is not a module item becomes
 * the module item (the vanilla item is creative-only, so a deliberately dropped one is converted too).
 */
public final class PetrifiedSlabDrops implements Listener {
    public static final String ITEM_ID = "ac_smooth_limestone_slab";

    private final CustomItemRegistry items;

    public PetrifiedSlabDrops(CustomItemRegistry items) {
        this.items = items;
    }

    /** One dropped stack, seen through the few things the conversion needs (a fake in tests). */
    interface Drop {
        Material type();

        boolean moduleItem();

        int amount();

        void replaceWithModuleSlab(int amount);
    }

    /** Turns the vanilla petrified oak slab stacks dropped by a petrified oak slab into the module item. */
    static int convert(Material broken, List<? extends Drop> drops) {
        if (broken != Material.PETRIFIED_OAK_SLAB) {
            return 0;
        }
        int converted = 0;
        for (Drop drop : drops) {
            if (drop.type() == Material.PETRIFIED_OAK_SLAB && !drop.moduleItem()) {
                drop.replaceWithModuleSlab(drop.amount());
                converted++;
            }
        }
        return converted;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDrop(BlockDropItemEvent event) {
        if (event.getBlockState().getType() != Material.PETRIFIED_OAK_SLAB) {
            return;
        }
        convert(Material.PETRIFIED_OAK_SLAB, event.getItems().stream().map(this::adapt).toList());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onSpawn(ItemSpawnEvent event) {
        Drop drop = adapt(event.getEntity());
        if (drop.type() == Material.PETRIFIED_OAK_SLAB && !drop.moduleItem()) {
            drop.replaceWithModuleSlab(drop.amount());
        }
    }

    private Drop adapt(Item item) {
        return new Drop() {
            @Override
            public Material type() {
                return item.getItemStack().getType();
            }

            @Override
            public boolean moduleItem() {
                return items.resolveItemId(item.getItemStack()).isPresent();
            }

            @Override
            public int amount() {
                return item.getItemStack().getAmount();
            }

            @Override
            public void replaceWithModuleSlab(int amount) {
                item.setItemStack(items.createItemStack(ITEM_ID, amount));
            }
        };
    }
}
