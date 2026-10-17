package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.content.ContentItemFactory;
import dev.linqfy.bigCasares.dimension.DimensionPlatform;
import dev.linqfy.bigCasares.modules.sift.feature.SiftFeatureRunner;
import dev.linqfy.bigCasares.modules.sift.feature.SiftFeatures;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Sapling;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Overgrown Willow saplings ({@code OvergrownWillowSaplingBlock} and the vines'
 * loot table in the mod). The sapling is a mangrove propagule on the client; its
 * position is remembered in the chunk, and when vanilla would grow a mangrove
 * there the mod's willow grows instead (one in seven random ticks, or 45 % of
 * bone meal uses, as in the mod). Without room or tree ground the sapling stays.
 * Willow vines broken without shears or silk touch with Fortune III drop a sapling.
 *
 * <p>Differences: the propagule is planted at its last growth stage so the first
 * growth step grows the tree, as the mod's single-stage sapling does, but vanilla
 * decides when that step happens (mangrove propagules ignore light, the mod needs
 * light 9). Mushroom-block soils all count as ground for planting. A sapling that
 * is washed away, pushed or blown up drops a mangrove propagule.</p>
 */
final class SiftWillowSaplings implements Listener {

    static final String SAPLING = SiftBlocks.NS + ":overgrown_willow_sapling";

    private final JavaPlugin plugin;
    private final Supplier<Optional<DimensionPlatform>> platform;
    private final ContentItemFactory items;
    private final SiftFeatures features;
    private final SiftChunkMarks saplings = new SiftChunkMarks(new NamespacedKey(SiftBlocks.NS, "willow_saplings"));

    SiftWillowSaplings(JavaPlugin plugin, Supplier<Optional<DimensionPlatform>> platform, ContentItemFactory items,
                       SiftFeatures features) {
        this.plugin = plugin;
        this.platform = platform;
        this.items = items;
        this.features = features;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getType() != Material.MANGROVE_PROPAGULE
            || ContentItemFactory.idOf(event.getItemInHand()).filter(id -> id.asString().equals(SAPLING)).isEmpty()) {
            return;
        }
        BlockData data = block.getBlockData();
        if (data instanceof Sapling sapling) {
            sapling.setStage(sapling.getMaximumStage());
            block.setBlockData(sapling, false);
        }
        track(block, true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGrow(StructureGrowEvent event) {
        Block block = event.getLocation().getBlock();
        if (!isTracked(block)) {
            return;
        }
        event.setCancelled(true);
        plugin.getServer().getScheduler().runTask(plugin, () -> grow(block));
    }

    /** The mod's {@code growTree}: remove the sapling, grow, put it back when the tree does not fit. */
    private void grow(Block block) {
        if (block.getType() != Material.MANGROVE_PROPAGULE || !isTracked(block)) {
            track(block, false);
            return;
        }
        BlockData sapling = block.getBlockData();
        block.setType(Material.AIR, false);
        Random random = new Random(ThreadLocalRandom.current().nextLong());
        boolean grown = features.growWillow(SiftFeatureRunner.region(plugin.getServer(), block.getWorld(), items::resolve),
            random, block.getX(), block.getY(), block.getZ());
        if (grown) {
            track(block, false);
        } else {
            block.setBlockData(sapling, false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSaplingDrop(BlockDropItemEvent event) {
        if (event.getBlockState().getType() != Material.MANGROVE_PROPAGULE || !isTracked(event.getBlock())) {
            return;
        }
        track(event.getBlock(), false);
        for (Item drop : event.getItems()) {
            ItemStack stack = drop.getItemStack();
            if (stack.getType() == Material.MANGROVE_PROPAGULE && ContentItemFactory.idOf(stack).isEmpty()) {
                items.create(SAPLING, stack.getAmount()).ifPresent(drop::setItemStack);
            }
        }
    }

    /** Willow vines (vines in the Sift) broken with Fortune III, without shears or silk touch. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVineBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        if (block.getType() != Material.VINE || !event.isDropItems() || event.getPlayer().getGameMode() == GameMode.CREATIVE
            || tool.getType() == Material.SHEARS || tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0
            || tool.getEnchantmentLevel(Enchantment.FORTUNE) < 3 || !inSift(block)) {
            return;
        }
        items.create(SAPLING, 1).ifPresent(stack -> block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), stack));
    }

    private boolean isTracked(Block block) {
        return saplings.isMarked(block);
    }

    private void track(Block block, boolean planted) {
        saplings.mark(block, planted);
    }

    private boolean inSift(Block block) {
        return platform.get().flatMap(dimensions -> dimensions.dimensionOf(block.getWorld()))
            .filter(SiftDimension.KEY::equals).isPresent();
    }
}
