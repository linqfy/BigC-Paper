package dev.linqfy.bigCasares.modules.alexscaves.primordial;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * In the mod, tree stars, pine nuts, fiddleheads, cycads and flytraps are plant
 * blocks of the biome. Here the biome's foliage is vanilla (see D5), so breaking
 * that foliage inside the Primordial Caves drops them instead.
 */
public final class PrimordialDrops implements Listener {
    static final NamespacedKey BIOME = new NamespacedKey("alexscaves", "primordial_caves");

    /** Vanilla block broken in the biome -> (module item, chance). */
    static final Map<Material, List<Drop>> DROPS = Map.of(
        Material.JUNGLE_LEAVES, List.of(new Drop("ac_tree_star", 0.15)),
        Material.SPRUCE_LEAVES, List.of(new Drop("ac_pine_nuts", 0.2)),
        Material.FERN, List.of(new Drop("ac_fiddlehead", 0.15)),
        Material.LARGE_FERN, List.of(new Drop("ac_fiddlehead", 0.2), new Drop("ac_cycad", 0.08)),
        Material.SHORT_GRASS, List.of(new Drop("ac_flytrap", 0.03)));

    private final ModMobService mobs;

    public PrimordialDrops(ModMobService mobs) {
        this.mobs = mobs;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        List<Drop> drops = DROPS.get(block.getType());
        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        // Shears and silk touch give the block back: a module drop on top would be an endless farm.
        if (drops == null || event.getPlayer().getGameMode() == GameMode.CREATIVE || tool.getType() == Material.SHEARS
            || tool.containsEnchantment(Enchantment.SILK_TOUCH)
            || !BIOME.equals(block.getWorld().getBiome(block.getLocation()).getKey())) {
            return;
        }
        for (Drop drop : drops) {
            if (ThreadLocalRandom.current().nextDouble() < drop.chance()) {
                mobs.createItem(drop.item(), 1)
                    .ifPresent(item -> block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), item));
            }
        }
    }

    record Drop(String item, double chance) {
    }
}
