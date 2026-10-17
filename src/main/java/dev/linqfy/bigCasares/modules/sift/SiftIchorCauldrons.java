package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.content.ContentItemFactory;
import dev.linqfy.bigCasares.dimension.DimensionPlatform;
import org.bukkit.GameEvent;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Ichor cauldrons (the mod's {@code IchorCauldronBlock} and {@code IchorCauldronInteractions}).
 * A cauldron filled from an Ichor bucket or bottle is a water cauldron on the client
 * and is remembered in its chunk; in the Sift every water cauldron holds Ichor. Glass
 * bottles and buckets take Ichor out, Ichor bottles pour it back in, with the mod's
 * sounds.
 *
 * <p>Differences: apart from these interactions an Ichor cauldron is a water cauldron
 * (it looks like one and washes dyed leather and banners); a cauldron emptied or
 * changed by other means forgets its Ichor once it is empty.</p>
 */
final class SiftIchorCauldrons implements Listener {

    private static final int FULL = 3;

    private final Supplier<Optional<DimensionPlatform>> platform;
    private final ContentItemFactory items;
    private final SiftChunkMarks ichor = new SiftChunkMarks(new NamespacedKey(SiftBlocks.NS, "ichor_cauldrons"));

    SiftIchorCauldrons(Supplier<Optional<DimensionPlatform>> platform, ContentItemFactory items) {
        this.platform = platform;
        this.items = items;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        ItemStack held = event.getItem();
        EquipmentSlot hand = event.getHand();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null || held == null || hand == null
            || event.useInteractedBlock() == Event.Result.DENY || !Tag.CAULDRONS.isTagged(block.getType())) {
            return;
        }
        String content = ContentItemFactory.idOf(held).map(id -> id.asString()).orElse("");
        if (content.equals(SiftIchorItems.BUCKET) && block.getType() != Material.CAULDRON) {
            // As in the mod only empty cauldrons take an Ichor bucket (vanilla would treat it as a water bucket
            // here); the bucket empties into the world instead.
            event.setUseInteractedBlock(Event.Result.DENY);
            return;
        }
        if (block.getType() != Material.CAULDRON && block.getType() != Material.WATER_CAULDRON) {
            return;
        }
        int level = level(block);
        boolean holdsIchor = block.getType() == Material.CAULDRON || isIchor(block);
        Player player = event.getPlayer();
        if (content.equals(SiftIchorItems.BUCKET)) {
            handled(event);
            setLevel(block, FULL);
            exchange(player, hand, held, new ItemStack(Material.BUCKET));
            effects(player, block, "item.ichor_bucket.empty", GameEvent.FLUID_PLACE);
        } else if (content.equals(SiftIchorItems.BOTTLE) && holdsIchor && level < FULL) {
            handled(event);
            setLevel(block, level + 1);
            exchange(player, hand, held, new ItemStack(Material.GLASS_BOTTLE));
            effects(player, block, "item.ichor_bottle.empty", GameEvent.FLUID_PLACE);
        } else if (held.getType() == Material.GLASS_BOTTLE && content.isEmpty() && isIchor(block)) {
            Optional<ItemStack> bottle = items.create(SiftIchorItems.BOTTLE, 1);
            if (bottle.isPresent()) {
                handled(event);
                setLevel(block, level - 1);
                exchange(player, hand, held, bottle.get());
                effects(player, block, "item.ichor_bottle.fill", GameEvent.FLUID_PICKUP);
            }
        } else if (held.getType() == Material.BUCKET && content.isEmpty() && isIchor(block) && level == FULL) {
            Optional<ItemStack> bucket = items.create(SiftIchorItems.BUCKET, 1);
            if (bucket.isPresent()) {
                handled(event);
                setLevel(block, 0);
                exchange(player, hand, held, bucket.get());
                effects(player, block, "item.ichor_bucket.fill", GameEvent.FLUID_PICKUP);
            }
        }
    }

    /** A cauldron that empties forgets its Ichor. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLevelChange(CauldronLevelChangeEvent event) {
        if (event.getNewState().getType() == Material.CAULDRON) {
            ichor.mark(event.getBlock(), false);
        }
    }

    private boolean isIchor(Block block) {
        return block.getType() == Material.WATER_CAULDRON && (ichor.isMarked(block) || inSift(block));
    }

    private static int level(Block block) {
        return block.getBlockData() instanceof Levelled levelled ? levelled.getLevel() : 0;
    }

    /** Empty below level 1; a filled cauldron is an Ichor cauldron. */
    private void setLevel(Block block, int level) {
        if (level <= 0) {
            block.setType(Material.CAULDRON);
            ichor.mark(block, false);
            return;
        }
        Levelled data = (Levelled) Material.WATER_CAULDRON.createBlockData();
        data.setLevel(level);
        block.setBlockData(data);
        ichor.mark(block, true);
    }

    private static void handled(PlayerInteractEvent event) {
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
    }

    /** Vanilla's {@code ItemUtils.createFilledResult}: creative players keep the item and get the result once. */
    private static void exchange(Player player, EquipmentSlot hand, ItemStack held, ItemStack result) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            if (!player.getInventory().containsAtLeast(result, 1)) {
                player.getInventory().addItem(result);
            }
            return;
        }
        if (held.getAmount() == 1) {
            player.getInventory().setItem(hand, result);
            return;
        }
        held.setAmount(held.getAmount() - 1);
        player.getInventory().addItem(result).values().forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
    }

    private static void effects(Player player, Block block, String sound, GameEvent gameEvent) {
        block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), SiftBlocks.NS + ":" + sound, SoundCategory.BLOCKS,
            1.0F, 1.0F);
        block.getWorld().sendGameEvent(player, gameEvent, block.getLocation().toVector());
    }

    private boolean inSift(Block block) {
        return platform.get().flatMap(dimensions -> dimensions.dimensionOf(block.getWorld()))
            .filter(SiftDimension.KEY::equals).isPresent();
    }
}
