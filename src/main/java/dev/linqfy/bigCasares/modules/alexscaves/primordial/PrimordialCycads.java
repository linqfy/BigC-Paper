package dev.linqfy.bigCasares.modules.alexscaves.primordial;

import dev.linqfy.bigCasares.modules.alexscaves.core.block.NoteBlockService;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The mod's cycad ({@code CycadBlock}) as module blocks: the stack's tip is the {@code default} variant (the leafy
 * model, {@code top=true}) and every cycad below another one is {@code trunk}. A cycad goes on dirt blocks or on
 * another cycad; breaking one drops the cycads above it, and bone meal grows a tip with a 50% chance while its stack
 * is under four blocks tall. The blocks have no support check of their own (the module's plants never do), so the
 * stack is kept in shape here.
 */
public final class PrimordialCycads implements Listener {
    static final String ITEM = "ac_cycad";

    private final ModMobService mobs;
    private final BlockData tip;
    private final BlockData trunk;

    public PrimordialCycads(ModMobService mobs, BlockData tip, BlockData trunk) {
        this.mobs = mobs;
        this.tip = tip;
        this.trunk = trunk;
    }

    boolean isCycad(Block block) {
        if (!NoteBlockService.isBase(block.getType())) {
            return false;
        }
        BlockData data = block.getBlockData();
        return data.equals(tip) || data.equals(trunk);
    }

    private boolean isTip(Block block) {
        return NoteBlockService.isBase(block.getType()) && block.getBlockData().equals(tip);
    }

    private static boolean mayStandOnBlock(Material type) {
        return Tag.DIRT.isTagged(type) || type == Material.FARMLAND;
    }

    /** Mod {@code mayPlaceOn}: a cycad that cannot stand where it is placed is refused. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onPlace(BlockPlaceEvent event) {
        if (!mobs.itemId(event.getItemInHand()).filter(ITEM::equals).isPresent()) {
            return;
        }
        Block below = event.getBlockPlaced().getRelative(BlockFace.DOWN);
        if (!CycadRules.mayStandOn(mayStandOnBlock(below.getType()), isCycad(below))) {
            event.setCancelled(true);
        }
    }

    /** The placed cycad takes its shape from what is above it, and the one below it stops being the tip. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlaced(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (isCycad(block)) {
            reshape(block);
            reshape(block.getRelative(BlockFace.DOWN));
        }
    }

    /** Sets a cycad to the tip or trunk model that matches the block above it. */
    private void reshape(Block block) {
        if (!isCycad(block)) {
            return;
        }
        BlockData wanted = CycadRules.tip(isCycad(block.getRelative(BlockFace.UP))) ? tip : trunk;
        if (!block.getBlockData().equals(wanted)) {
            block.setBlockData(wanted, false);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        // A cycad that loses the dirt it stands on drops too.
        if (isCycad(block) || isCycad(block.getRelative(BlockFace.UP))) {
            afterRemoval(block);
        }
    }

    /** Before the block list is final (NoteBlockListener empties it of module blocks at MONITOR): the next tick tells. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent event) {
        explode(event.blockList());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent event) {
        explode(event.blockList());
    }

    private void explode(List<Block> affected) {
        List<Block> cycads = new ArrayList<>();
        for (Block block : affected) {
            if (isCycad(block) || isCycad(block.getRelative(BlockFace.UP))) {
                cycads.add(block);
            }
        }
        for (Block block : cycads) {
            afterRemoval(block);
        }
    }

    /** Once the block is gone: the cycads above lose their support and drop, the one below becomes the tip. */
    private void afterRemoval(Block removed) {
        Runnable drop = () -> {
            // Still there (an explosion that spared it), or replaced by something a cycad can stand on.
            if (isCycad(removed) || mayStandOnBlock(removed.getType())) {
                return;
            }
            Block above = removed.getRelative(BlockFace.UP);
            while (isCycad(above)) {
                above.setType(Material.AIR, false);
                mobs.createItem(ITEM, 1).ifPresent(item ->
                    removed.getWorld().dropItemNaturally(removed.getLocation().add(0.5, 0.5, 0.5), item));
                above = above.getRelative(BlockFace.UP);
            }
            reshape(removed.getRelative(BlockFace.DOWN));
        };
        mobs.tasks().later(1L, drop, drop);
    }

    /** Mod {@code BonemealableBlock}: bone meal on a tip under four cycads grows one more on top, half of the time. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBoneMeal(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        ItemStack item = event.getItem();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null || item == null
            || item.getType() != Material.BONE_MEAL || !isCycad(block)) {
            return;
        }
        int height = 0;
        Block down = block;
        while (isCycad(down) && height < 64) {
            height++;
            down = down.getRelative(BlockFace.DOWN);
        }
        if (!CycadRules.canGrow(isTip(block), height)) {
            return;
        }
        Player player = event.getPlayer();
        GameMode mode = player.getGameMode();
        if (mode == GameMode.ADVENTURE || mode == GameMode.SPECTATOR || event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        event.setCancelled(true);
        if (player.getGameMode() != GameMode.CREATIVE) {
            item.subtract();
        }
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, center, 8, 0.3, 0.3, 0.3);
        Block above = block.getRelative(BlockFace.UP);
        if (CycadRules.boneMealWorks(ThreadLocalRandom.current().nextBoolean()) && (above.isEmpty() || above.isReplaceable())
            && above.getY() < block.getWorld().getMaxHeight() && mayGrow(above)) {
            block.setBlockData(trunk, false);
            above.setBlockData(tip, false);
            block.getWorld().playSound(center, Sound.ITEM_BONE_MEAL_USE, 1.0F, 1.0F);
        }
    }

    /** Protections see the growth as a {@link BlockGrowEvent}, as for vanilla crops. */
    private boolean mayGrow(Block above) {
        BlockState grown = above.getState();
        grown.setBlockData(tip);
        BlockGrowEvent grow = new BlockGrowEvent(above, grown);
        mobs.plugin().getServer().getPluginManager().callEvent(grow);
        return !grow.isCancelled();
    }
}
