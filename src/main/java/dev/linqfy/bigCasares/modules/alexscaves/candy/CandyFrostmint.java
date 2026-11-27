package dev.linqfy.bigCasares.modules.alexscaves.candy;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The mod's frostmint ({@code FrostmintBlock}, a slab that falls like sand) as a module block: {@code default} is the
 * bottom slab, {@code top} the top slab, {@code double} the full block (all note block states). A bottom or double
 * frostmint with a free block below falls, checked two ticks after it is placed, after what held it up is broken or
 * blown up, and after the block under it fell; a projectile that hits a column brings its bottom block down, and the
 * rest follows. It falls as a vanilla falling block of the module state and is put back where it lands. A frostmint
 * that meets purple soda (the Candy Cavity's water), falling or landing in it, blows up instead
 * ({@code FrostmintExplosion}): power 4, breaking blocks, and 200 more freeze ticks for the living entities near.
 * <p>
 * Slabs also work like slabs: a second one on the top face of a bottom slab (or the bottom face of a top slab) makes
 * a double, and one placed against the upper half of a block is a top slab.
 */
public final class CandyFrostmint implements Listener, Runnable {
    static final String ITEM = "ac_frostmint";
    static final String FALLING_TAG = "ac_frostmint_falling";
    private static final NamespacedKey CANDY = new NamespacedKey(CaveBiomeId.NAMESPACE, CaveBiomeId.CANDY_CAVITY.path());

    private final ModMobService mobs;
    private final BlockData bottom;
    private final BlockData top;
    private final BlockData doubleSlab;
    private final Set<FallingBlock> falling = new HashSet<>();
    /** Per player: whether the slab they are about to place goes in the upper half of its block. */
    private final Map<UUID, Boolean> upperHalf = new HashMap<>();

    public CandyFrostmint(ModMobService mobs, BlockData bottom, BlockData top, BlockData doubleSlab) {
        this.mobs = mobs;
        this.bottom = bottom;
        this.top = top;
        this.doubleSlab = doubleSlab;
    }

    /** The variant of a frostmint block, or null for any other block. */
    String variant(Block block) {
        if (block.getType() != Material.NOTE_BLOCK) {
            return null;
        }
        BlockData data = block.getBlockData();
        if (data.equals(bottom)) {
            return FrostmintRules.BOTTOM;
        }
        if (data.equals(top)) {
            return FrostmintRules.TOP;
        }
        return data.equals(doubleSlab) ? FrostmintRules.DOUBLE : null;
    }

    private BlockData dataOf(String variant) {
        return switch (variant) {
            case FrostmintRules.TOP -> top;
            case FrostmintRules.DOUBLE -> doubleSlab;
            default -> bottom;
        };
    }

    private boolean isFrostmintItem(ItemStack item) {
        return mobs.itemId(item).filter(ITEM::equals).isPresent();
    }

    // --- gravity ---------------------------------------------------------------------------------------------

    private void schedule(Block block, boolean hit) {
        mobs.tasks().later(FrostmintRules.FALL_DELAY_TICKS, () -> check(block, hit));
    }

    /** Schedules the check of a frostmint resting on a block that is going away. */
    private void scheduleAbove(Block block) {
        Block above = block.getRelative(BlockFace.UP);
        if (variant(above) != null) {
            schedule(above, false);
        }
    }

    private void check(Block block, boolean hit) {
        String variant = variant(block);
        if (variant == null || block.getY() <= block.getWorld().getMinHeight()) {
            return;
        }
        boolean free = FrostmintRules.isFree(block.getRelative(BlockFace.DOWN).getType(), Tag.REPLACEABLE::isTagged);
        if (FrostmintRules.falls(variant, free, hit)) {
            fall(block, variant);
        }
    }

    private void fall(Block block, String variant) {
        BlockData data = dataOf(variant);
        Location start = block.getLocation().add(0.5, 0.0, 0.5);
        block.setBlockData(Material.AIR.createBlockData(), false);
        FallingBlock entity = block.getWorld().spawnFallingBlock(start, data);
        // Where vanilla gives up on it (the landing spot is taken, or it falls for too long), it drops as an item:
        // onDrop turns that item into the module's frostmint.
        entity.setDropItem(true);
        entity.addScoreboardTag(FALLING_TAG);
        falling.add(entity);
        // What rested on it has nothing under it now.
        scheduleAbove(block);
    }

    /** Every tick: a falling frostmint that enters purple soda blows up. */
    @Override
    public void run() {
        if (falling.isEmpty()) {
            return;
        }
        for (FallingBlock entity : new ArrayList<>(falling)) {
            if (!entity.isValid()) {
                falling.remove(entity);
                continue;
            }
            if (isSoda(entity.getLocation().getBlock())) {
                falling.remove(entity);
                Location at = entity.getLocation().add(0.0, 0.5, 0.0);
                entity.remove();
                explode(at);
            }
        }
    }

    /** Where the falling frostmint lands it goes back as a block, or blows up when it lands in purple soda. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onLand(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock entity) || !entity.getScoreboardTags().contains(FALLING_TAG)
            || event.getTo() == Material.AIR) {
            return;
        }
        boolean refused = event.isCancelled();
        event.setCancelled(true);
        falling.remove(entity);
        Block target = event.getBlock();
        BlockData data = entity.getBlockData();
        entity.remove();
        if (refused) {
            // Something (a protection) did not let it land: it is dropped instead of lost.
            dropAt(target.getLocation().add(0.5, 0.5, 0.5), data);
            return;
        }
        if (isSoda(target)) {
            explode(target.getLocation().add(0.5, 0.5, 0.5));
            return;
        }
        target.setBlockData(data, false);
        target.getWorld().playSound(target.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_GLASS_PLACE, 1.0F, 1.0F);
        schedule(target, false);
    }

    /** The item vanilla drops for a falling frostmint (a plain note block) becomes the module's frostmint. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDrop(EntityDropItemEvent event) {
        if (!(event.getEntity() instanceof FallingBlock entity) || !entity.getScoreboardTags().contains(FALLING_TAG)) {
            return;
        }
        falling.remove(entity);
        event.setCancelled(true);
        dropAt(event.getItemDrop().getLocation(), entity.getBlockData());
    }

    private void dropAt(Location at, BlockData data) {
        int amount = data.equals(doubleSlab) ? 2 : 1;
        mobs.createItem(ITEM, amount).ifPresent(item -> at.getWorld().dropItemNaturally(at, item));
    }

    /** Mod {@code onProjectileHit}: the lowest block of the column hit is checked, and the column follows it down. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onProjectileHit(ProjectileHitEvent event) {
        Block hit = event.getHitBlock();
        if (hit == null || variant(hit) == null) {
            return;
        }
        World world = hit.getWorld();
        int bottomY = FrostmintRules.columnBottom(hit.getY(), world.getMinHeight(),
            y -> variant(world.getBlockAt(hit.getX(), y, hit.getZ())) != null);
        schedule(world.getBlockAt(hit.getX(), bottomY, hit.getZ()), true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) {
        scheduleAbove(event.getBlock());
    }

    /** Before the block list is final (it only lists what may go): a block that stays is checked and holds. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent event) {
        explodedBlocks(event.blockList());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent event) {
        explodedBlocks(event.blockList());
    }

    private void explodedBlocks(List<Block> blocks) {
        for (Block block : new ArrayList<>(blocks)) {
            scheduleAbove(block);
        }
    }

    // --- placing ---------------------------------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        Block clicked = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || item == null || clicked == null || !isFrostmintItem(item)) {
            return;
        }
        BlockFace face = event.getBlockFace();
        boolean faceUp = face == BlockFace.UP;
        boolean faceDown = face == BlockFace.DOWN;
        String variant = variant(clicked);
        Player player = event.getPlayer();
        if (variant != null && FrostmintRules.merges(variant, faceUp, faceDown)) {
            event.setCancelled(true);
            if (!mayPlace(player, clicked, event)) {
                return;
            }
            clicked.setBlockData(doubleSlab, false);
            clicked.getWorld().playSound(clicked.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_GLASS_PLACE, 1.0F, 1.0F);
            if (player.getGameMode() != GameMode.CREATIVE) {
                item.subtract();
            }
            player.swingHand(event.getHand());
            return;
        }
        Location point = event.getInteractionPoint();
        double height = point == null ? 0.0 : point.getY() - clicked.getY();
        upperHalf.put(player.getUniqueId(), FrostmintRules.upperHalf(faceUp, faceDown, height));
    }

    /** Whether the player may turn the slab into a double: not in adventure or spectator, and protections agree. */
    private static boolean mayPlace(Player player, Block clicked, PlayerInteractEvent use) {
        GameMode mode = player.getGameMode();
        if (mode == GameMode.ADVENTURE || mode == GameMode.SPECTATOR || use.useItemInHand() == Event.Result.DENY) {
            return false;
        }
        // Other listeners of the module may rewrite the block while handling the event: it is put back as it was.
        BlockData before = clicked.getBlockData();
        BlockPlaceEvent place = new BlockPlaceEvent(clicked, clicked.getState(), clicked, use.getItem(), player, true,
            use.getHand());
        player.getServer().getPluginManager().callEvent(place);
        clicked.setBlockData(before, false);
        return !place.isCancelled() && place.canBuild();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlaced(BlockPlaceEvent event) {
        if (!isFrostmintItem(event.getItemInHand())) {
            return;
        }
        Boolean upper = upperHalf.remove(event.getPlayer().getUniqueId());
        Block block = event.getBlockPlaced();
        if (variant(block) == null) {
            return;
        }
        if (Boolean.TRUE.equals(upper) && FrostmintRules.BOTTOM.equals(variant(block))) {
            block.setBlockData(top, false);
        }
        schedule(block, false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        upperHalf.remove(event.getPlayer().getUniqueId());
    }

    // --- purple soda -----------------------------------------------------------------------------------------

    /** Purple soda is the Candy Cavity's water. */
    private static boolean isSoda(Block block) {
        return block.getType() == Material.WATER && CANDY.equals(block.getWorld().getBiome(block.getLocation()).getKey());
    }

    /** Mod {@code FrostmintExplosion}: a blast that breaks blocks, with no fire, and a freeze on whoever is near. */
    private void explode(Location at) {
        World world = at.getWorld();
        world.createExplosion(at, FrostmintRules.EXPLOSION_POWER, false, true);
        double radiusSquared = FrostmintRules.FREEZE_RADIUS * FrostmintRules.FREEZE_RADIUS;
        for (LivingEntity living : world.getNearbyLivingEntities(at, FrostmintRules.FREEZE_RADIUS)) {
            if (living.getLocation().distanceSquared(at) <= radiusSquared) {
                living.setFreezeTicks(Math.max(0, living.getFreezeTicks()) + FrostmintRules.FREEZE_TICKS);
            }
        }
    }
}
