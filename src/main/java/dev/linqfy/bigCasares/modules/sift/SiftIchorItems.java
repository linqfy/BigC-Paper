package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.content.ContentItemFactory;
import dev.linqfy.bigCasares.dimension.DimensionPlatform;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameEvent;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Filling Ichor (the mod's {@code IchorBucketItem} and {@code IchorBottleItem}).
 * Ichor is water inside the Sift, so a bucket or glass bottle filled from a water
 * source there holds Ichor. The Ichor bucket is a water bucket underneath and
 * empties as water: Ichor in the Sift, plain water elsewhere.
 */
final class SiftIchorItems implements Listener {

    static final String BUCKET = SiftBlocks.NS + ":ichor_bucket";
    static final String BOTTLE = SiftBlocks.NS + ":ichor_bottle";
    private static final String BOTTLE_FILL_SOUND = SiftBlocks.NS + ":item.ichor_bottle.fill";

    private final Supplier<Optional<DimensionPlatform>> platform;
    private final ContentItemFactory items;

    SiftIchorItems(Supplier<Optional<DimensionPlatform>> platform, ContentItemFactory items) {
        this.platform = platform;
        this.items = items;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        ItemStack filled = event.getItemStack();
        if (filled != null && filled.getType() == Material.WATER_BUCKET && inSift(event.getBlockClicked().getWorld())) {
            items.create(BUCKET, 1).ifPresent(event::setItemStack);
        }
    }

    /** A glass bottle aimed at a water source in the Sift fills with Ichor instead of water. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBottleFill(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack held = event.getItem();
        EquipmentSlot hand = event.getHand();
        if ((event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
            || held == null || held.getType() != Material.GLASS_BOTTLE || hand == null
            || event.useItemInHand() == Event.Result.DENY || !inSift(player.getWorld())) {
            return;
        }
        Block source = waterSource(player);
        if (source == null) {
            return;
        }
        Optional<ItemStack> bottle = items.create(BOTTLE, 1);
        if (bottle.isEmpty()) {
            return;
        }
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        if (held.getAmount() == 1) {
            player.getInventory().setItem(hand, bottle.get());
        } else {
            held.setAmount(held.getAmount() - 1);
            player.getInventory().addItem(bottle.get()).values()
                .forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
        }
        World world = source.getWorld();
        world.playSound(source.getLocation().add(0.5, 0.5, 0.5), BOTTLE_FILL_SOUND, SoundCategory.BLOCKS, 1.0F, 1.0F);
        world.sendGameEvent(player, GameEvent.FLUID_PICKUP, source.getLocation().toVector());
    }

    /** The water source the player looks at within reach, as vanilla bottles find it. */
    private static Block waterSource(Player player) {
        AttributeInstance reach = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
        RayTraceResult hit = player.rayTraceBlocks(reach == null ? 4.5 : reach.getValue(), FluidCollisionMode.SOURCE_ONLY);
        Block block = hit == null ? null : hit.getHitBlock();
        if (block == null) {
            return null;
        }
        boolean water = block.getType() == Material.WATER
            || block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged();
        return water ? block : null;
    }

    private boolean inSift(World world) {
        return platform.get().flatMap(dimensions -> dimensions.dimensionOf(world)).filter(SiftDimension.KEY::equals).isPresent();
    }
}
