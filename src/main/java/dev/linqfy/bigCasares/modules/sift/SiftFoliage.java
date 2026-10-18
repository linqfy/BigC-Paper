package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.blocks.CustomBlockDefinition;
import dev.linqfy.bigCasares.blocks.CustomBlockRegistry;
import dev.linqfy.bigCasares.blocks.CustomBlockService;
import dev.linqfy.bigCasares.dimension.NamespacedId;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Overgrown Willow Foliage decaying like leaves. The mod's block is an oak-leaves copy
 * ({@code OvergrownWillowFoliageBlock extends TintedParticleLeavesBlock}), so it inherits vanilla's rule: foliage
 * that is not persistent and has no log within {@value LeafDecay#MAX_REACH} steps (through leaves) breaks on a random
 * tick and drops what its loot table says, which for this block is the foliage itself
 * ({@code loot_table/blocks/overgrown_willow_foliage.json}, the block's own item).
 *
 * <p>Foliage a player places is persistent, as vanilla's {@code getStateForPlacement} makes leaves; foliage that world
 * generation or a growing sapling places is not. The custom block has no state to hold the flag, so the positions of
 * placed foliage are marks in the chunk's persistent data ({@link SiftChunkMarks}), dropped when the player breaks the
 * block and, for a position that no longer holds foliage, when the chunk loads.</p>
 *
 * <p>Differences: the sampler of {@link SiftRandomTicks} ticks the foliage instead of the server's random ticks, and the
 * search of {@link LeafDecay} replaces the distance property (the result is the same distance when the tick happens,
 * not kept between ticks). A search that would read an unloaded chunk keeps the foliage. A foliage block removed
 * by a piston, fluid or fire keeps its mark until its chunk is next loaded, so foliage grown at that very position in
 * the meantime counts as placed. Foliage put in by commands or
 * world-editing tools raises no placement event, so it is not marked and decays like generated foliage. Leaf particles and the decay of vanilla leaves in the Sift are the server's.</p>
 */
final class SiftFoliage implements Listener {

    static final String FOLIAGE = "overgrown_willow_foliage";
    /** The mod's {@code the_sift:overgrown_willow_logs} tag, with the port's axis variants. */
    static final Set<String> LOGS = Set.of("overgrown_willow_log", "overgrown_willow_log_x", "overgrown_willow_log_z",
        "stripped_overgrown_willow_log", "stripped_overgrown_willow_log_x", "stripped_overgrown_willow_log_z",
        "overgrown_willow_wood", "stripped_overgrown_willow_wood");

    /** One stack of a drop: a content or vanilla item id and its count. */
    record Stack(String item, int count) {
    }

    private final JavaPlugin plugin;
    private final CustomBlockRegistry blocks;
    private final BiFunction<String, Integer, Optional<ItemStack>> items;
    private final SiftChunkMarks placed = new SiftChunkMarks(new NamespacedKey(SiftBlocks.NS, "placed_foliage"));
    private final Random random = new Random();

    SiftFoliage(JavaPlugin plugin, CustomBlockRegistry blocks, BiFunction<String, Integer, Optional<ItemStack>> items) {
        this.plugin = plugin;
        this.blocks = blocks;
        this.items = items;
    }

    /** What a Sift block means to the leaf search. */
    static LeafDecay.Kind kindOf(String siftPath) {
        if (LOGS.contains(siftPath)) {
            return LeafDecay.Kind.LOG;
        }
        return siftPath.equals(FOLIAGE) ? LeafDecay.Kind.LEAVES : LeafDecay.Kind.OTHER;
    }

    /**
     * What breaking the block without a tool drops: the definition's drops, or the block itself when it lists none,
     * as the block service does. Counts are rolled in the drop's range.
     */
    static List<Stack> drops(CustomBlockDefinition definition, Random random) {
        if (definition.drops().isEmpty()) {
            return List.of(new Stack(definition.id().asString(), 1));
        }
        List<Stack> stacks = new ArrayList<>();
        for (CustomBlockDefinition.Drop drop : definition.drops()) {
            int count = drop.min() + random.nextInt(drop.max() - drop.min() + 1);
            if (count > 0) {
                stacks.add(new Stack(drop.item(), count));
            }
        }
        return stacks;
    }

    // --- placed foliage never decays ---------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (isFoliage(event.getBlockPlaced())) {
            placed.mark(event.getBlockPlaced(), true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (placed.isMarked(event.getBlock())) {
            placed.mark(event.getBlock(), false);
        }
    }

    /** Marks of foliage that is gone (removed some way that raises no event) are forgotten when the chunk loads. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        long[] marks = placed.positions(chunk);
        if (marks.length == 0) {
            return;
        }
        long[] kept = Arrays.stream(marks).filter(packed -> {
            SiftPos at = SiftPos.of(packed);
            return isFoliage(chunk.getWorld().getBlockAt(at.x(), at.y(), at.z()));
        }).toArray();
        if (kept.length != marks.length) {
            placed.clear(chunk);
            for (long packed : kept) {
                SiftPos at = SiftPos.of(packed);
                placed.mark(chunk.getWorld().getBlockAt(at.x(), at.y(), at.z()), true);
            }
        }
    }

    // --- the random tick ---------------------------------------------------------------------------------

    /**
     * The decay of {@code LeavesBlock.randomTick}: foliage nobody placed that has lost its logs breaks and drops.
     * Other blocks are ignored.
     */
    void randomTick(Block block) {
        if (!isFoliage(block) || placed.isMarked(block)) {
            return;
        }
        World world = block.getWorld();
        Reader reader = new Reader(world);
        if (!LeafDecay.decays(reader, block.getX(), block.getY(), block.getZ()) || reader.incomplete) {
            return;
        }
        LeavesDecayEvent decay = new LeavesDecayEvent(block);
        plugin.getServer().getPluginManager().callEvent(decay);
        if (decay.isCancelled() || !isFoliage(block)) {
            return;
        }
        Optional<CustomBlockDefinition> definition = definitionAt(block);
        List<Stack> stacks = definition.map(found -> drops(found, random)).orElse(List.of());
        block.setType(Material.AIR);
        Location at = block.getLocation().add(0.5, 0.5, 0.5);
        for (Stack stack : stacks) {
            items.apply(stack.item(), stack.count()).ifPresent(item -> world.dropItemNaturally(at, item));
        }
    }

    private boolean isFoliage(Block block) {
        return block.getType() == Material.MUSHROOM_STEM
            && definitionAt(block).filter(definition -> definition.id().path().equals(FOLIAGE)).isPresent();
    }

    private Optional<CustomBlockDefinition> definitionAt(Block block) {
        return CustomBlockService.stateOf(block.getBlockData()).flatMap(blocks::byState)
            .filter(definition -> definition.id().namespace().equals(SiftBlocks.NS));
    }

    /** The world as the search reads it; never loads a chunk, and notes when it had to guess at one. */
    private final class Reader implements LeafDecay.Grid {

        private final World world;
        private boolean incomplete;

        Reader(World world) {
            this.world = world;
        }

        @Override
        public LeafDecay.Kind kindAt(int x, int y, int z) {
            if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
                return LeafDecay.Kind.OTHER;
            }
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                incomplete = true;
                return LeafDecay.Kind.OTHER;
            }
            Material type = world.getType(x, y, z);
            if (type == Material.MUSHROOM_STEM) {
                return CustomBlockService.stateOf(world.getBlockData(x, y, z)).flatMap(blocks::byState)
                    .map(CustomBlockDefinition::id).filter(id -> id.namespace().equals(SiftBlocks.NS))
                    .map(NamespacedId::path).map(SiftFoliage::kindOf).orElse(LeafDecay.Kind.OTHER);
            }
            if (Tag.LOGS.isTagged(type)) {
                return LeafDecay.Kind.LOG;
            }
            return Tag.LEAVES.isTagged(type) ? LeafDecay.Kind.LEAVES : LeafDecay.Kind.OTHER;
        }
    }
}
