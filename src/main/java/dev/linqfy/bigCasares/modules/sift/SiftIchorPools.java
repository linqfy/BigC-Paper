package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.content.ContentItemFactory;
import dev.linqfy.bigCasares.dimension.DimensionPlatform;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Ichor poured outside the Sift. The mod's Ichor is its own fluid everywhere; the
 * port's Ichor is the Sift's water, so an Ichor bucket emptied in another world
 * places water. The source block is remembered in its chunk: touching it gives the
 * Sift's Ichor effects and a bucket takes Ichor back out.
 *
 * <p>A substitute, not a port: only the source itself is Ichor (water flowing from
 * it is plain water), and it looks like water and gives no light.</p>
 */
final class SiftIchorPools implements Listener {

    private final JavaPlugin plugin;
    private final Supplier<Optional<DimensionPlatform>> platform;
    private final ContentItemFactory items;
    private final SiftSettings.Ichor ichor;
    private final SiftChunkMarks sources = new SiftChunkMarks(new NamespacedKey(SiftBlocks.NS, "ichor_sources"));

    SiftIchorPools(JavaPlugin plugin, Supplier<Optional<DimensionPlatform>> platform, ContentItemFactory items,
                   SiftSettings.Ichor ichor) {
        this.plugin = plugin;
        this.platform = platform;
        this.items = items;
        this.ichor = ichor;
    }

    void start(BukkitRuntimeRegistrations registrations) {
        registrations.registerListener("sift-ichor-pools", this);
        registrations.scheduleRepeating("sift-ichor-pools", this::applyEffects, 20L, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEmpty(PlayerBucketEmptyEvent event) {
        Block target = event.getBlock();
        boolean ichorBucket = ContentItemFactory.idOf(event.getPlayer().getInventory().getItem(event.getHand()))
            .filter(id -> id.asString().equals(SiftIchorItems.BUCKET)).isPresent();
        if (inSift(target.getWorld())) {
            return;
        }
        sources.mark(target, ichorBucket);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFill(PlayerBucketFillEvent event) {
        Block source = event.getBlockClicked();
        if (inSift(source.getWorld()) || !sources.isMarked(source)) {
            return;
        }
        sources.mark(source, false);
        if (event.getItemStack() != null && event.getItemStack().getType() == Material.WATER_BUCKET) {
            items.create(SiftIchorItems.BUCKET, 1).ifPresent(event::setItemStack);
        }
    }

    private void applyEffects() {
        if (!ichor.regeneration()) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (inSift(player.getWorld()) || !touchesPool(player)) {
                continue;
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 70, 0, true, true, true));
            if (ichor.slowness()) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 0, true, false, false));
            }
        }
    }

    private boolean touchesPool(Player player) {
        return isPool(player.getLocation().getBlock()) || isPool(player.getEyeLocation().getBlock());
    }

    private boolean isPool(Block block) {
        boolean water = block.getType() == Material.WATER
            || block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged();
        if (!sources.isMarked(block)) {
            return false;
        }
        if (!water) {
            // The source is gone (picked up by other means, dried, replaced).
            sources.mark(block, false);
        }
        return water;
    }

    private boolean inSift(World world) {
        return platform.get().flatMap(dimensions -> dimensions.dimensionOf(world)).filter(SiftDimension.KEY::equals).isPresent();
    }
}
