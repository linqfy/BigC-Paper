package dev.linqfy.bigCasares.modules.alexscaves.core.block;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.List;
import java.util.Map;

/**
 * Placing, breaking and cleaning up cave paintings (see {@link CavePaintings}). Mod {@code CavePaintingBlock}: placed
 * facing the player, silk touch drops the painting, anything else drops smooth limestone (the module's limestone
 * drop already does that). No ticking: displays are only touched on events.
 */
public final class CavePaintingListener implements Listener {
    private final ModMobService mobs;
    private final BlockData limestone;
    /** Paintings broken with silk touch, waiting for the block's drop event (same tick). */
    private final Map<Location, String> silkBroken = new HashMap<>();
    /** Paintings loaded before their limestone's chunk: chunk key -> display ids. */
    private final Map<String, List<UUID>> waiting = new HashMap<>();

    public CavePaintingListener(ModMobService mobs, BlockData smoothLimestone) {
        this.mobs = mobs;
        this.limestone = smoothLimestone;
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGH)
    public void onPlace(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        ItemStack held = event.getItem();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || clicked == null || held == null
            || event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        String painting = CavePaintings.paintingOf(mobs.itemId(held).orElse(null));
        if (painting == null) {
            return;
        }
        Player player = event.getPlayer();
        if (clicked.getType().isInteractable() && !player.isSneaking()) {
            return;
        }
        event.setCancelled(true);
        if (player.getGameMode() == GameMode.ADVENTURE) {
            return;
        }
        Block target = clicked.isReplaceable() ? clicked : clicked.getRelative(event.getBlockFace());
        if (!target.isReplaceable() || target.getY() >= target.getWorld().getMaxHeight()
            || target.getY() < target.getWorld().getMinHeight() || occupied(target)) {
            return;
        }
        BlockPlaceEvent check = new BlockPlaceEvent(target, target.getState(), clicked, held, player, true,
            event.getHand());
        if (!check.callEvent()) {
            return;
        }
        Vector look = player.getLocation().getDirection();
        BlockFace facing = FacingDisplays.placedFacing(look.getX(), look.getY(), look.getZ());
        target.setBlockData(limestone, false);
        spawn(target, painting, facing);
        if (player.getGameMode() != GameMode.CREATIVE) {
            held.subtract();
        }
        player.swingHand(event.getHand());
        target.getWorld().playSound(target.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_STONE_PLACE, 1.0f, 0.8f);
    }

    private static boolean occupied(Block target) {
        for (Entity entity : target.getWorld().getNearbyEntities(BoundingBox.of(target))) {
            if (entity instanceof LivingEntity) {
                return true;
            }
        }
        return false;
    }

    private static void spawn(Block host, String painting, BlockFace facing) {
        World world = host.getWorld();
        world.spawn(CavePaintings.anchorLocation(world, host.getX(), host.getY(), host.getZ(), facing),
            ItemDisplay.class, display -> CavePaintings.configure(display, painting, facing, host.getX(), host.getY(),
                host.getZ()));
    }

    /** The painting display of a limestone block, if it has one. */
    private static ItemDisplay paintingOf(Block block) {
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        for (Entity entity : block.getWorld().getNearbyEntities(center, 1.1, 1.1, 1.1)) {
            int[] host = CavePaintings.host(entity);
            if (host != null && host[0] == block.getX() && host[1] == block.getY() && host[2] == block.getZ()) {
                return (ItemDisplay) entity;
            }
        }
        return null;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!limestone.equals(block.getBlockData())) {
            return;
        }
        ItemDisplay display = paintingOf(block);
        if (display == null) {
            return;
        }
        String painting = display.getPersistentDataContainer().get(CavePaintings.PAINTING,
            org.bukkit.persistence.PersistentDataType.STRING);
        display.remove();
        Player player = event.getPlayer();
        if (event.isDropItems() && player.getGameMode() != GameMode.CREATIVE
            && player.getInventory().getItemInMainHand().containsEnchantment(Enchantment.SILK_TOUCH)) {
            Location key = block.getLocation();
            silkBroken.put(key, painting);
            // The drop event comes in the same tick; forget it if it never does.
            mobs.plugin().getServer().getScheduler().runTask(mobs.plugin(), () -> silkBroken.remove(key));
        }
    }

    /** Silk touch on a painting: the painting instead of the limestone (after the module's own drop swap). */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDrop(BlockDropItemEvent event) {
        String painting = silkBroken.remove(event.getBlock().getLocation());
        if (painting == null) {
            return;
        }
        // Only the limestone that really dropped becomes the painting (no drop with the wrong tool, no painting).
        String limestoneItem = "ac_smooth_limestone";
        mobs.createItem(CavePaintings.ITEM_PREFIX + painting, 1).ifPresent(drop -> {
            for (Item item : event.getItems()) {
                if (mobs.itemId(item.getItemStack()).filter(limestoneItem::equals).isPresent()) {
                    item.setItemStack(drop);
                    return;
                }
            }
        });
    }

    /** Runs first: the module's note block listener takes its blocks out of the list when it blows them up. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onBlockExplode(BlockExplodeEvent event) {
        checkLater(event.blockList());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEntityExplode(EntityExplodeEvent event) {
        checkLater(event.blockList());
    }

    /** Next tick, the paintings of the limestone that is gone go too (explosions drop plain limestone, as in the mod). */
    private void checkLater(List<Block> affected) {
        List<Block> limestones = new ArrayList<>();
        for (Block block : affected) {
            if (limestone.equals(block.getBlockData())) {
                limestones.add(block);
            }
        }
        if (limestones.isEmpty()) {
            return;
        }
        mobs.plugin().getServer().getScheduler().runTask(mobs.plugin(), () -> {
            for (Block block : limestones) {
                if (!limestone.equals(block.getBlockData())) {
                    ItemDisplay display = paintingOf(block);
                    if (display != null) {
                        display.remove();
                    }
                }
            }
        });
    }

    /** Endermen, falling blocks, withers...: a limestone changed by an entity loses its painting. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Block block = event.getBlock();
        if (limestone.equals(block.getBlockData()) && !limestone.equals(event.getBlockData())) {
            ItemDisplay display = paintingOf(block);
            if (display != null) {
                display.remove();
            }
        }
    }

    /** Fire, decay and other block-destroy paths Paper reports (not player breaks: those are handled above). */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDestroy(com.destroystokyo.paper.event.block.BlockDestroyEvent event) {
        Block block = event.getBlock();
        if (limestone.equals(block.getBlockData()) && !limestone.equals(event.getNewState())) {
            ItemDisplay display = paintingOf(block);
            if (display != null) {
                display.remove();
            }
        }
    }

    /**
     * A painting whose limestone is gone (removed while nobody watched: commands, other plugins) goes too. When the
     * limestone is in a chunk that is not loaded, the check waits for that chunk.
     */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            int[] host = CavePaintings.host(entity);
            if (host == null) {
                continue;
            }
            if (!entity.getWorld().isChunkLoaded(host[0] >> 4, host[2] >> 4)) {
                waiting.computeIfAbsent(chunkKey(entity.getWorld(), host[0] >> 4, host[2] >> 4),
                    ignored -> new ArrayList<>()).add(entity.getUniqueId());
                continue;
            }
            checkHost(entity, host);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        List<UUID> pending = waiting.remove(chunkKey(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ()));
        if (pending == null) {
            return;
        }
        for (UUID id : pending) {
            Entity entity = event.getWorld().getEntity(id);
            int[] host = entity == null ? null : CavePaintings.host(entity);
            if (host != null) {
                checkHost(entity, host);
            }
        }
    }

    /** Forgets the waiting checks of paintings that unload before their limestone's chunk loads. */
    @EventHandler
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        if (waiting.isEmpty()) {
            return;
        }
        for (Entity entity : event.getEntities()) {
            int[] host = CavePaintings.host(entity);
            if (host != null) {
                List<UUID> pending = waiting.get(chunkKey(entity.getWorld(), host[0] >> 4, host[2] >> 4));
                if (pending != null) {
                    pending.remove(entity.getUniqueId());
                    if (pending.isEmpty()) {
                        waiting.remove(chunkKey(entity.getWorld(), host[0] >> 4, host[2] >> 4));
                    }
                }
            }
        }
    }

    private void checkHost(Entity entity, int[] host) {
        if (!limestone.equals(entity.getWorld().getBlockAt(host[0], host[1], host[2]).getBlockData())) {
            entity.remove();
        }
    }

    static String chunkKey(World world, int chunkX, int chunkZ) {
        return world.getUID() + ":" + chunkX + ":" + chunkZ;
    }
}
