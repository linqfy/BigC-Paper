package dev.linqfy.bigCasares.modules.alexscaves.core.block;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import dev.linqfy.bigCasares.items.CustomItemRegistry;
import io.papermc.paper.event.entity.EntityInsideBlockEvent;
import io.papermc.paper.event.player.PlayerPickBlockEvent;
import org.bukkit.GameMode;
import org.bukkit.ExplosionResult;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Bukkit events of the module blocks (note block, tripwire and chorus plant states). Everything that would
 * change, move or wash away a module state is cancelled only for module states, so vanilla string and chorus
 * behave as usual.
 */
public final class NoteBlockListener implements Listener {
    private final NoteBlockService blocks;
    private final CustomItemRegistry items;
    private final Consumer<Block> removed;

    /**
     * {@code removed} is told about module blocks that vanish without a player break (explosions). Tripwire and
     * chorus plant states only draw module blocks when Paper's {@code disable-tripwire-updates} and
     * {@code disable-chorus-plant-updates} are on (see {@link ModBlockRegistry#shapedBasesActive}): that is what
     * stops neighbours from rewriting them, so nothing here scans or cancels events of the blocks around them.
     */
    public NoteBlockListener(NoteBlockService blocks, CustomItemRegistry items, Consumer<Block> removed) {
        this.blocks = blocks;
        this.items = items;
        this.removed = removed;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getType() == Material.CHORUS_FLOWER) {
            // A flower that grows on or next to a module chorus state would link to it and grow plants from it.
            if (blocks.touchesModuleChorus(block)) {
                event.setCancelled(true);
            }
            return;
        }
        if (block.getType() == Material.TRIPWIRE_HOOK) {
            // A hook that closes a line of string rewrites attached / powered of every string state in it.
            if (block.getBlockData() instanceof Directional hook && blocks.stringRunHasModuleBlock(block, hook.getFacing())) {
                event.setCancelled(true);
            }
            return;
        }
        if (block.getType() != Material.NOTE_BLOCK) {
            return;
        }
        Optional<String> blockId = moduleBlock(event.getItemInHand());
        if (blockId.isPresent()) {
            if (!blocks.canPlace(block, blockId.get())) {
                event.setCancelled(true);
                return;
            }
            BlockFace clicked = event.getBlockAgainst().getFace(block);
            blocks.place(block, blockId.get(), clicked == null ? "UP" : clicked.name());
        } else {
            blocks.normalizeVanilla(block);
        }
    }

    /** The top half of a two-block plant goes in only when no later handler cancelled the placement. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlaceFinal(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getType() == Material.NOTE_BLOCK && moduleBlock(event.getItemInHand()).isPresent()) {
            blocks.placeUpperHalf(block);
        }
    }

    /** Note blocks never update their state from neighbours: that would change module blocks. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        if (type != Material.NOTE_BLOCK) {
            // A module tripwire / chorus plant state (the block itself only; its neighbours are left alone).
            if ((type == Material.TRIPWIRE || type == Material.CHORUS_PLANT) && blocks.isModuleState(block.getBlockData())) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        if (blocks.placedAt(block).isEmpty() && block.getBlockData() instanceof NoteBlock note) {
            // Vanilla note block: keep redstone working by hand (rising edge plays the note).
            boolean powered = block.isBlockIndirectlyPowered();
            if (powered != note.isPowered()) {
                note.setPowered(powered);
                block.setBlockData(note, false);
                if (powered) {
                    blocks.playVanilla(block, note.getNote());
                }
            }
        }
    }

    /**
     * Pistons reshape moved note blocks without a physics event, which would rewrite their instrument; tripwire
     * and chorus plant module states would break into string or chorus fruit, either moved or in the way.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (movesModuleBlock(event.getBlock(), event.getBlocks(), event.getDirection(), true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (movesModuleBlock(event.getBlock(), event.getBlocks(), event.getDirection(), false)) {
            event.setCancelled(true);
        }
    }

    /**
     * {@code direction} is the way the blocks move: the piston's facing when it extends, towards the piston when a
     * sticky one retracts (CraftBukkit passes the opposite of the facing). Only an extending piston pushes its
     * head into the block in front of it; on retract that spot holds the head.
     */
    private boolean movesModuleBlock(Block piston, List<Block> moved, BlockFace direction, boolean extending) {
        if (extending && blocks.isModuleState(piston.getRelative(direction).getBlockData())) {
            return true;
        }
        for (Block block : moved) {
            if (block.getType() == Material.NOTE_BLOCK || blocks.isModuleState(block.getBlockData())
                || blocks.isModuleState(block.getRelative(direction).getBlockData())) {
                return true;
            }
        }
        return false;
    }

    /** Water and lava flow into tripwire (it does not block motion) and would wash a module block away. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onFlow(BlockFromToEvent event) {
        if (blocks.placedAt(event.getToBlock()).isPresent()) {
            event.setCancelled(true);
        }
    }

    /** An entity inside a module tripwire state would power it (another state). */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEntityInside(EntityInsideBlockEvent event) {
        if (event.getBlock().getType() == Material.TRIPWIRE && blocks.placedAt(event.getBlock()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEntityInteract(EntityInteractEvent event) {
        if (event.getBlock().getType() == Material.TRIPWIRE && blocks.placedAt(event.getBlock()).isPresent()) {
            event.setCancelled(true);
        }
    }

    /** A chorus plant that lost its support breaks through a scheduled tick; module states have no support. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDestroy(BlockDestroyEvent event) {
        Material type = event.getBlock().getType();
        if ((type == Material.CHORUS_PLANT || type == Material.TRIPWIRE) && blocks.placedAt(event.getBlock()).isPresent()) {
            event.setCancelled(true);
        }
    }

    /**
     * A growing chorus flower turns into a plant linked to whatever chorus is around it, which can be one of the
     * module's chorus states: that plant also links down the next tick. Paper fires {@link BlockSpreadEvent}
     * (source: the flower) for every growth that turns the flower into a plant; {@code BlockGrowEvent} is only
     * the flower ageing.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onChorusGrow(BlockSpreadEvent event) {
        if (event.getSource().getType() == Material.CHORUS_FLOWER) {
            blocks.guardChorusGrowth(event.getSource());
        }
    }

    /** Pick block on a module block selects (or, in creative, gives) its module item, not string or chorus. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPickBlock(PlayerPickBlockEvent event) {
        Optional<ModBlockRegistry.Placed> placed = blocks.placedAt(event.getBlock());
        if (placed.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        String id = "ac_" + placed.get().blockId();
        PlayerInventory inventory = event.getPlayer().getInventory();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && items.resolveItemId(stack).filter(id::equals).isPresent()) {
                if (slot < 9) {
                    inventory.setHeldItemSlot(slot);
                } else {
                    ItemStack held = inventory.getItemInMainHand();
                    inventory.setItemInMainHand(stack);
                    inventory.setItem(slot, held);
                }
                return;
            }
        }
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) {
            try {
                inventory.setItemInMainHand(items.createItemStack(id, 1));
            } catch (RuntimeException missing) {
                // No catalog item: nothing to pick.
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onNotePlay(NotePlayEvent event) {
        if (blocks.placedAt(event.getBlock()).isPresent()) {
            event.setCancelled(true);
            return;
        }
        event.setInstrument(blocks.soundInstrument(event.getBlock()));
    }

    /**
     * Right-clicking a module block must never tune it: that changes its state, so it would
     * become another module block. Denying the block use still lets players place against it.
     * Vanilla note blocks under a mob head are played by hand, since their pinned harp state
     * makes vanilla tune them silently instead.
     */
    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        if (block.getType() != Material.NOTE_BLOCK) {
            // Stepping on a module tripwire state would power it.
            if (event.getAction() == Action.PHYSICAL && block.getType() == Material.TRIPWIRE
                && blocks.placedAt(block).isPresent()) {
                event.setUseInteractedBlock(Event.Result.DENY);
                event.setCancelled(true);
            }
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (blocks.placedAt(block).isPresent()) {
                event.setUseInteractedBlock(Event.Result.DENY);
                return;
            }
            boolean bypass = event.getPlayer().isSneaking() && holdsSomething(event);
            if (!bypass && event.getHand() == EquipmentSlot.HAND && event.useInteractedBlock() != Event.Result.DENY
                && NoteBlockService.hasMobHeadAbove(block) && block.getBlockData() instanceof NoteBlock note) {
                event.setUseInteractedBlock(Event.Result.DENY);
                blocks.playVanilla(block, note.getNote());
            }
        } else if (event.getAction() == Action.LEFT_CLICK_BLOCK && event.useInteractedBlock() != Event.Result.DENY
            && blocks.placedAt(block).isEmpty() && NoteBlockService.hasMobHeadAbove(block)
            && block.getBlockData() instanceof NoteBlock note) {
            blocks.playVanilla(block, note.getNote());
        }
    }

    /**
     * Axes strip module logs like vanilla ones: {@code <id>} becomes {@code stripped_<id>} with the
     * same orientation. Runs late so protection plugins that deny the item use are honoured.
     */
    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    public void onStrip(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        ItemStack tool = event.getItem();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null || tool == null
            || !tool.getType().name().endsWith("_AXE") || event.useItemInHand() == Event.Result.DENY
            || event.getPlayer().getGameMode() == GameMode.ADVENTURE) {
            return;
        }
        blocks.placedAt(block).flatMap(placed -> blocks.blockData(strippedId(placed.blockId()), placed.variant()))
            .ifPresent(stripped -> {
                block.setBlockData(stripped, false);
                block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), Sound.ITEM_AXE_STRIP, 1.0f, 1.0f);
                if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
                    tool.damage(1, event.getPlayer());
                }
                event.getPlayer().swingHand(event.getHand());
                event.setUseItemInHand(Event.Result.DENY);
            });
    }

    static String strippedId(String blockId) {
        return blockId.startsWith("stripped_") ? "" : "stripped_" + blockId;
    }

    private static boolean holdsSomething(PlayerInteractEvent event) {
        var inventory = event.getPlayer().getInventory();
        return !inventory.getItemInMainHand().isEmpty() || !inventory.getItemInOffHand().isEmpty();
    }

    /**
     * The module item replaces the note block drop. Done on the drop event, which only fires
     * once nobody cancelled the break, so a later cancellation can never duplicate items.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDrop(BlockDropItemEvent event) {
        blocks.placedIn(event.getBlockState().getBlockData()).ifPresent(placed -> {
            boolean silk = event.getPlayer().getInventory().getItemInMainHand().containsEnchantment(Enchantment.SILK_TOUCH);
            List<ItemStack> drops = blocks.drops(placed, silk);
            List<Item> spawned = event.getItems();
            Iterator<Item> iterator = spawned.iterator();
            int index = 0;
            // Every drop of the base (note block, string, chorus fruit) is replaced.
            while (iterator.hasNext()) {
                Item item = iterator.next();
                if (index < drops.size()) {
                    item.setItemStack(drops.get(index++));
                } else {
                    iterator.remove();
                }
            }
            // A chorus plant may drop nothing at all: the rest of the module drops are spawned here.
            for (; index < drops.size(); index++) {
                event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), drops.get(index));
            }
        });
    }

    /** Breaking one half of a two-block plant (curly fern) removes the other half without drops (one drop). */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        blocks.placedAt(block).flatMap(placed -> blocks.otherHalf(block, placed)).ifPresent(other -> {
            other.setType(Material.AIR, false);
            removed.accept(other);
        });
    }

    /** Runs last so the block list is final: protection plugins may still cancel or trim it. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (destroysBlocks(event.getExplosionResult())) {
            explode(event.blockList());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (destroysBlocks(event.getExplosionResult())) {
            explode(event.blockList());
        }
    }

    /** Wind charges list blocks only to trigger them (buttons, doors), not to break them. */
    static boolean destroysBlocks(ExplosionResult result) {
        return result == ExplosionResult.DESTROY || result == ExplosionResult.DESTROY_WITH_DECAY;
    }

    private void explode(List<Block> affected) {
        Iterator<Block> iterator = affected.iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            Optional<ModBlockRegistry.Placed> placed = blocks.placedAt(block);
            if (placed.isPresent()) {
                iterator.remove();
                // The other half goes too, without drops; if it is also in the list it is air by then and skipped.
                blocks.otherHalf(block, placed.get()).ifPresent(other -> {
                    other.setType(Material.AIR, false);
                    removed.accept(other);
                });
                block.setType(Material.AIR, false);
                removed.accept(block);
                for (ItemStack drop : blocks.drops(placed.get(), false)) {
                    block.getWorld().dropItemNaturally(block.getLocation(), drop);
                }
            }
        }
    }

    /** Module terrain is note block (wood) underneath; cave lava must not eat it. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBurn(BlockBurnEvent event) {
        if (blocks.placedAt(event.getBlock()).isPresent()) {
            event.setCancelled(true);
        }
    }

    /** Module blocks are note block items underneath; they are not furnace fuel. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onFuel(FurnaceBurnEvent event) {
        if (moduleBlock(event.getFuel()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        blocks.chunkUnloaded(event.getChunk());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        blocks.visitChunk(event.getChunk(), event.isNewChunk());
        blocks.migrateChunk(event.getChunk(), event.isNewChunk());
    }

    private Optional<String> moduleBlock(ItemStack item) {
        if (item == null || item.getType() != Material.NOTE_BLOCK) {
            return Optional.empty();
        }
        return items.resolveItemId(item)
            .filter(id -> id.startsWith("ac_"))
            .map(id -> id.substring(3))
            .filter(blocks.registry()::contains);
    }
}
