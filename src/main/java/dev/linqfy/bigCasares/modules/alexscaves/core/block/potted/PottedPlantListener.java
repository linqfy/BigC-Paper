package dev.linqfy.bigCasares.modules.alexscaves.core.block.potted;

import dev.linqfy.bigCasares.items.CustomItemRegistry;
import dev.linqfy.bigCasares.modules.alexscaves.core.block.remap.WaterloggedIndex;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;

/**
 * Potted module plants as vanilla flower pots (see {@link PottedPlantTable}). Using a module plant item on an empty
 * pot puts the closest vanilla plant in it and remembers the module plant in the chunk's persistent data; emptying the
 * pot (right-click or breaking it) gives back the module item, never the vanilla plant. The mark is dropped when the
 * pot is broken, exploded or replaced. Interaction runs at HIGHEST and honours cancellation (protection plugins).
 * Nothing here is Java-only: Bedrock players see the vanilla pot through Geyser.
 */
public final class PottedPlantListener implements Listener {
    static final String KEY = "alexscaves_potted";

    private final Plugin plugin;
    private final CustomItemRegistry items;
    private final NamespacedKey key;

    public PottedPlantListener(Plugin plugin, CustomItemRegistry items) {
        this.plugin = plugin;
        this.items = items;
        this.key = new NamespacedKey(plugin, KEY);
    }

    // --- marks -------------------------------------------------------------------------------------------------

    private static int packed(Block block) {
        return WaterloggedIndex.pack(block.getX(), block.getY(), block.getZ());
    }

    private String saved(Block block) {
        return block.getChunk().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    private void store(Chunk chunk, String value) {
        PersistentDataContainer data = chunk.getPersistentDataContainer();
        if (value == null) {
            data.remove(key);
        } else {
            data.set(key, PersistentDataType.STRING, value);
        }
    }

    /** The module plant marked in this pot, only while the block still is the matching vanilla potted plant. */
    private Optional<PottedPlantTable.Substitute> marked(Block block) {
        String plant = PottedPlantStore.plantAt(saved(block), packed(block));
        return PottedPlantTable.forPlant(plant)
            .filter(sub -> block.getType().name().equals(sub.potMaterial()));
    }

    private void unmark(Block block) {
        String before = saved(block);
        String after = PottedPlantStore.without(before, packed(block));
        if (!java.util.Objects.equals(before, after)) {
            store(block.getChunk(), after);
        }
    }

    // --- interaction ------------------------------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        String type = block.getType().name();
        boolean pot = block.getType() == Material.FLOWER_POT;
        if (!pot && !type.startsWith("POTTED_")) {
            return;
        }
        // Safety net: a mark whose block no longer is the matching pot is stale and never gives a module item.
        String saved = saved(block);
        boolean stale = PottedPlantStore.isStale(saved, packed(block), type);
        if (stale) {
            unmark(block);
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack main = inventory.getItemInMainHand();
        ItemStack off = inventory.getItemInOffHand();
        boolean secondaryUse = player.isSneaking() && (!main.getType().isAir() || !off.getType().isAir());
        if (pot) {
            fill(event, block, player, main, off, secondaryUse);
        } else if (!stale && event.getHand() == EquipmentSlot.HAND && marked(block).isPresent()) {
            if (player.getGameMode() == GameMode.ADVENTURE) {
                // Adventure players cannot change blocks, and vanilla must not hand out the vanilla plant either.
                event.setUseItemInHand(Event.Result.DENY);
                event.setUseInteractedBlock(Event.Result.DENY);
            } else if (!secondaryUse) {
                empty(event, block, player, marked(block).get());
            }
        }
    }

    private Optional<PottedPlantTable.Substitute> pottable(ItemStack stack) {
        return stack == null || stack.getType().isAir() ? Optional.empty()
            : items.resolveItemId(stack).flatMap(PottedPlantTable::forItem);
    }

    /**
     * Fills from the hand of this event. The off hand only fills when the main hand holds no pottable plant, so a
     * click never fills twice.
     */
    private void fill(PlayerInteractEvent event, Block block, Player player, ItemStack main, ItemStack off,
                      boolean secondaryUse) {
        if (secondaryUse || player.getGameMode() == GameMode.ADVENTURE) {
            return;
        }
        boolean offHand = event.getHand() == EquipmentSlot.OFF_HAND;
        ItemStack held = offHand ? off : main;
        Optional<PottedPlantTable.Substitute> plant = pottable(held);
        if (plant.isEmpty() || offHand && pottable(main).isPresent()) {
            return;
        }
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        block.setType(Material.valueOf(plant.get().potMaterial()), false);
        store(block.getChunk(), PottedPlantStore.with(saved(block), packed(block), plant.get().plant()));
        if (player.getGameMode() != GameMode.CREATIVE) {
            held.setAmount(held.getAmount() - 1);
        }
        block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_GRASS_PLACE, 1.0f, 1.0f);
    }

    private void empty(PlayerInteractEvent event, Block block, Player player, PottedPlantTable.Substitute plant) {
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        block.setType(Material.FLOWER_POT, false);
        unmark(block);
        if (player.getGameMode() != GameMode.CREATIVE) {
            ItemStack module = items.createItemStack(PottedPlantTable.itemId(plant.plant()), 1);
            for (ItemStack left : player.getInventory().addItem(module).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        }
        block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_GRASS_BREAK, 1.0f, 1.0f);
    }

    // --- breaking, replacing, exploding -----------------------------------------------------------------------

    /** The pot's plant drops as the module item (the pot itself drops as usual). */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDrop(BlockDropItemEvent event) {
        Block block = event.getBlock();
        String plant = PottedPlantStore.plantAt(saved(block), packed(block));
        Optional<PottedPlantTable.Substitute> sub = PottedPlantTable.forPlant(plant)
            .filter(s -> event.getBlockState().getType().name().equals(s.potMaterial()));
        if (sub.isEmpty()) {
            return;
        }
        Material vanilla = Material.valueOf(sub.get().plantMaterial());
        for (Item drop : event.getItems()) {
            if (drop.getItemStack().getType() == vanilla) {
                drop.setItemStack(items.createItemStack(PottedPlantTable.itemId(sub.get().plant()), 1));
                return;
            }
        }
    }

    /** After every break the mark is dropped next tick (the drop handler above still needs it), if the pot is gone. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) {
        scheduleClean(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (!PottedPlantStore.decode(saved(block)).isEmpty() && marked(block).isEmpty()) {
            unmark(block);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().forEach(this::scheduleClean);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().forEach(this::scheduleClean);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityChange(EntityChangeBlockEvent event) {
        scheduleClean(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        event.getBlocks().forEach(this::scheduleClean);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        event.getBlocks().forEach(this::scheduleClean);
    }

    private void scheduleClean(Block block) {
        if (!block.getType().name().startsWith("POTTED_") || PottedPlantStore.plantAt(saved(block), packed(block)) == null) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4) && marked(block).isEmpty()) {
                unmark(block);
            }
        });
    }

    /** Chunk load: marks whose pot was changed by something we missed (commands, other plugins) are dropped. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        String saved = chunk.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (saved == null) {
            return;
        }
        var entries = PottedPlantStore.decode(saved);
        int minY = chunk.getWorld().getMinHeight();
        int maxY = chunk.getWorld().getMaxHeight();
        List.copyOf(entries.entrySet()).forEach(entry -> {
            int packed = entry.getKey();
            int y = WaterloggedIndex.y(packed);
            Optional<PottedPlantTable.Substitute> sub = PottedPlantTable.forPlant(entry.getValue());
            boolean valid = y >= minY && y < maxY && sub.isPresent()
                && chunk.getBlock(WaterloggedIndex.localX(packed), y, WaterloggedIndex.localZ(packed)).getType().name()
                    .equals(sub.get().potMaterial());
            if (!valid) {
                entries.remove(packed);
            }
        });
        String cleaned = PottedPlantStore.encode(entries);
        if (!java.util.Objects.equals(cleaned, saved)) {
            store(chunk, cleaned);
        }
    }
}
