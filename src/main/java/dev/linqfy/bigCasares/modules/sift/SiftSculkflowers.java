package dev.linqfy.bigCasares.modules.sift;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import dev.linqfy.bigCasares.blocks.CustomBlockDefinition;
import dev.linqfy.bigCasares.blocks.CustomBlockRegistry;
import dev.linqfy.bigCasares.blocks.CustomBlockService;
import dev.linqfy.bigCasares.content.ContentItemFactory;
import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.modules.sift.SculkflowerCrop.Floor;
import dev.linqfy.bigCasares.modules.sift.SculkflowerCrop.Soil;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Farmland;
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
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * The Sculkflower, ported from the mod's {@code SculkflowerCropBlock}, {@code SculkflowerBlock} and
 * Sculkflower Seeds: seeds planted on sculk, healthy sculk, dry healthy sculk or farmland grow in two stages
 * and turn into the Sculkflower, which drops itself and is a dye for light blue.
 *
 * <p><b>Stand-ins.</b> An unmodified client cannot show a new block, so the crop is a vanilla
 * {@code torchflower_crop} (ages 0 and 1, the same two stages and the same step to a flower) and the
 * Sculkflower a {@code warped_fungus}; the seeds are torchflower seeds and the flower a warped fungus
 * (the mod's compost chances, 30 and 65, are theirs). Vanilla places them, so the client predicts the
 * placement and the light, space and collision rules are vanilla's; the datapack ({@code SiftWorldgen}) lets
 * crops and fungi stand on sculk and on red mushroom blocks (the healthy sculk soils), which keeps them
 * from popping off. That makes them able to stand on them anywhere, not only for Sculkflowers.</p>
 *
 * <p><b>Recognising them.</b> A torchflower crop or warped fungus of the Sculkflower looks like any
 * other one, so the positions of the placed ones are saved in the persistent data of the chunk that holds
 * them ({@link SculkflowerPlants}). A position whose block is no longer one of the two stand-ins is
 * forgotten the next time it is looked at. Everything below happens only for those positions: placing
 * with Sculkflower items is vetoed off the four floors the mod allows, vanilla's own growth (random
 * tick and bone meal) is cancelled for them, and breaking drops the mod's loot tables instead of the
 * stand-in's.</p>
 *
 * <p><b>Growth</b> is the mod's {@code CropBlock.randomTick} ({@link SculkflowerCrop}), driven by a task
 * every {@value #GROWTH_PERIOD} ticks that gives each crop in an entity-ticking chunk the chance a random tick
 * had of reaching it in that time (from the world's random tick speed), then applies the mod's light
 * rule, growth speed (healthy sculk counts as moist farmland) and chance. A bone meal adds one age.
 * Growth goes through {@code BlockGrowEvent}.</p>
 *
 * <p>Differences:</p>
 * <ul>
 *   <li>Crops and fungi stand on sculk and red mushroom blocks everywhere (datapack tag), and the Sculkflower
 *       items also place their stand-ins on vanilla's other floors only to be vetoed (the client may show
 *       the placement for a moment). The Sculkflower is not placed on the floors of vanilla's cactus flower
 *       (cactus and such), and cannot be put in a flower pot.</li>
 *   <li>The look is the stand-ins', not the mod's textures. Chickens breed with Sculkflower Seeds and striders
 *       follow the flower, as with torchflower seeds and warped fungus.</li>
 *   <li>Growth ticks are sampled every {@value #GROWTH_PERIOD} ticks, not drawn each tick. A destruction that
 *       does not raise {@code BlockDestroyEvent} or an explosion event (a piston, flowing water) gives the
 *       stand-in's drops, not the mod's.</li>
 *   <li>Dispensed bone meal does nothing to the crop (the vanilla growth is cancelled, the bone meal is lost).</li>
 *   <li>The advancement {@code planting_the_dark} is awarded when a player right-clicks the top of sculk with the
 *       seeds and the space above can be replaced, as in the mod, whether or not the seeds can be planted.</li>
 * </ul>
 */
final class SiftSculkflowers implements Listener {

    static final NamespacedId SEEDS = NamespacedId.of(SiftBlocks.NS, "sculkflower_seeds");
    static final NamespacedId FLOWER = NamespacedId.of(SiftBlocks.NS, "sculkflower");
    static final String CROP_BLOCK = SiftBlocks.NS + ":sculkflower_crop";
    static final String FLOWER_BLOCK = SiftBlocks.NS + ":sculkflower";
    static final int GROWTH_PERIOD = 20;

    private record ChunkKey(UUID world, int x, int z) {
    }

    private final JavaPlugin plugin;
    private final BiFunction<String, Integer, Optional<ItemStack>> items;
    private final CustomBlockRegistry blocks;
    private final SiftFixedLoot cropLoot;
    private final SiftFixedLoot flowerLoot;
    private final NamespacedKey plantsKey;
    private final Map<ChunkKey, Set<Long>> plants = new HashMap<>();
    private final Random random = new Random();
    private boolean applyingGrowth;

    SiftSculkflowers(JavaPlugin plugin, BiFunction<String, Integer, Optional<ItemStack>> items, CustomBlockRegistry blocks,
                     SiftData data) {
        this.plugin = plugin;
        this.items = items;
        this.blocks = blocks;
        this.cropLoot = SiftFixedLoot.parse(data.json("loot_table/blocks/sculkflower_crop.json"));
        this.flowerLoot = SiftFixedLoot.parse(data.json("loot_table/blocks/sculkflower.json"));
        this.plantsKey = new NamespacedKey(plugin, "sift_sculkflowers");
    }

    void activate(BukkitRuntimeRegistrations registrations) {
        registrations.registerListener("sift-sculkflowers", this);
        registrations.ownCleanup("sift-sculkflower-registry", plants::clear);
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                load(chunk);
            }
        }
        registrations.scheduleRepeating("sculkflower-growth", this::growthPass, GROWTH_PERIOD, GROWTH_PERIOD);
    }

    // --- the saved positions ---------------------------------------------------------------

    private static ChunkKey keyOf(Chunk chunk) {
        return new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
    }

    private static ChunkKey keyOf(Block block) {
        return new ChunkKey(block.getWorld().getUID(), block.getX() >> 4, block.getZ() >> 4);
    }

    private void load(Chunk chunk) {
        long[] stored = chunk.getPersistentDataContainer().get(plantsKey, PersistentDataType.LONG_ARRAY);
        if (stored != null && stored.length > 0) {
            plants.put(keyOf(chunk), new HashSet<>(SculkflowerPlants.decode(stored)));
        }
    }

    private boolean registered(Block block) {
        Set<Long> positions = plants.get(keyOf(block));
        return positions != null && positions.contains(SculkflowerPlants.pack(block.getX() & 15, block.getY(), block.getZ() & 15));
    }

    private void register(Block block) {
        plants.computeIfAbsent(keyOf(block), key -> new HashSet<>())
            .add(SculkflowerPlants.pack(block.getX() & 15, block.getY(), block.getZ() & 15));
        save(block);
    }

    private void unregister(Block block) {
        Set<Long> positions = plants.get(keyOf(block));
        if (positions != null && positions.remove(SculkflowerPlants.pack(block.getX() & 15, block.getY(), block.getZ() & 15))) {
            save(block);
        }
    }

    private void save(Block block) {
        Set<Long> positions = plants.getOrDefault(keyOf(block), Set.of());
        var data = block.getChunk().getPersistentDataContainer();
        if (positions.isEmpty()) {
            data.remove(plantsKey);
        } else {
            data.set(plantsKey, PersistentDataType.LONG_ARRAY, SculkflowerPlants.encode(positions));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        load(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        plants.remove(keyOf(event.getChunk()));
    }

    static boolean isCrop(Block block) {
        return block.getType() == Material.TORCHFLOWER_CROP;
    }

    static boolean isFlower(Block block) {
        return block.getType() == Material.WARPED_FUNGUS;
    }

    /** A registered position that is no longer one of the stand-ins is dropped; true when it still is one. */
    private boolean stillThere(Block block) {
        if (isCrop(block) || isFlower(block)) {
            return true;
        }
        unregister(block);
        return false;
    }

    private Optional<NamespacedId> customBlock(Block block) {
        return CustomBlockService.stateOf(block.getBlockData()).flatMap(blocks::byState).map(CustomBlockDefinition::id)
            .filter(id -> id.namespace().equals(SiftBlocks.NS));
    }

    private Floor floorOf(Block floor) {
        Material type = floor.getType();
        if (type == Material.FARMLAND) {
            return Floor.FARMLAND;
        }
        if (type == Material.SCULK) {
            return Floor.SCULK;
        }
        if (type == Material.RED_MUSHROOM_BLOCK) {
            Optional<NamespacedId> id = customBlock(floor);
            if (id.isPresent() && id.get().path().equals("healthy_sculk")) {
                return Floor.HEALTHY_SCULK;
            }
            if (id.isPresent() && id.get().path().equals("dry_healthy_sculk")) {
                return Floor.DRY_HEALTHY_SCULK;
            }
        }
        return Floor.OTHER;
    }

    // --- planting --------------------------------------------------------------------------

    /**
     * Vanilla placed a stand-in with a Sculkflower item: keep it where the mod allows it (the four floors) and
     * remember it, or take it back.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Optional<NamespacedId> id = ContentItemFactory.idOf(event.getItemInHand());
        if (id.isEmpty() || !(id.get().equals(SEEDS) || id.get().equals(FLOWER))) {
            return;
        }
        Block placed = event.getBlockPlaced();
        boolean expected = id.get().equals(SEEDS) ? isCrop(placed) : isFlower(placed);
        if (!expected || !floorOf(placed.getRelative(BlockFace.DOWN)).plantable()) {
            event.setCancelled(true);
            return;
        }
        register(placed);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        ItemStack held = event.getItem();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || clicked == null || held == null) {
            return;
        }
        Optional<NamespacedId> id = ContentItemFactory.idOf(held);
        if (id.isPresent() && id.get().equals(SEEDS) && event.getBlockFace() == BlockFace.UP && clicked.getType() == Material.SCULK
            && clicked.getRelative(BlockFace.UP).isReplaceable()) {
            SiftAdvancements.award(event.getPlayer(), SiftAdvancements.PLANTING_THE_DARK);
        }
        if (id.isPresent() && id.get().equals(FLOWER) && clicked.getType() == Material.FLOWER_POT) {
            event.setUseItemInHand(Event.Result.DENY);
            return;
        }
        if (held.getType() == Material.BONE_MEAL && registered(clicked) && isCrop(clicked)) {
            event.setCancelled(true);
            boneMeal(event.getPlayer(), clicked, held);
        }
    }

    private void boneMeal(Player player, Block crop, ItemStack bonemeal) {
        int age = ((Ageable) crop.getBlockData()).getAge();
        if (!grow(crop, SculkflowerCrop.ageAfterBoneMeal(age))) {
            return;
        }
        if (player.getGameMode() != GameMode.CREATIVE) {
            bonemeal.subtract(1);
        }
        crop.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, crop.getLocation().add(0.5, 0.4, 0.5), 15, 0.25, 0.2, 0.25);
    }

    // --- growth ----------------------------------------------------------------------------

    /** Vanilla's own growth of a Sculkflower crop (random tick, dispenser) is replaced by {@link #growthPass}. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVanillaGrow(BlockGrowEvent event) {
        if (!applyingGrowth && registered(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /** Moves a crop to {@code age}; 2 or more is the Sculkflower. False when another plugin vetoed it. */
    private boolean grow(Block crop, int age) {
        BlockState next = crop.getState();
        if (age >= SculkflowerCrop.MAX_AGE) {
            next.setType(Material.WARPED_FUNGUS);
        } else {
            BlockData data = crop.getBlockData();
            ((Ageable) data).setAge(age);
            next.setBlockData(data);
        }
        applyingGrowth = true;
        try {
            if (!new BlockGrowEvent(crop, next).callEvent()) {
                return false;
            }
        } finally {
            applyingGrowth = false;
        }
        next.update(true, true);
        return true;
    }

    private void growthPass() {
        for (Map.Entry<ChunkKey, Set<Long>> entry : new ArrayList<>(plants.entrySet())) {
            World world = Bukkit.getWorld(entry.getKey().world());
            if (world == null || !world.isChunkLoaded(entry.getKey().x(), entry.getKey().z())) {
                continue;
            }
            Chunk chunk = world.getChunkAt(entry.getKey().x(), entry.getKey().z(), false);
            if (chunk.getLoadLevel() != Chunk.LoadLevel.ENTITY_TICKING) {
                continue;
            }
            double chance = SculkflowerCrop.randomTickChance(randomTickSpeed(world), GROWTH_PERIOD);
            for (long packed : List.copyOf(entry.getValue())) {
                Block block = chunk.getBlock(SculkflowerPlants.localX(packed), SculkflowerPlants.y(packed),
                    SculkflowerPlants.localZ(packed));
                if (stillThere(block) && isCrop(block) && random.nextDouble() < chance) {
                    randomTick(block);
                }
            }
        }
    }

    static int randomTickSpeed(World world) {
        Integer speed = world.getGameRuleValue(GameRules.RANDOM_TICK_SPEED);
        return speed == null ? 3 : speed;
    }

    private void randomTick(Block crop) {
        int light = Math.max(crop.getLightFromBlocks(), crop.getLightFromSky());
        int age = ((Ageable) crop.getBlockData()).getAge();
        if (light < SculkflowerCrop.MIN_LIGHT_TO_GROW || age >= SculkflowerCrop.MAX_AGE) {
            return;
        }
        float speed = SculkflowerCrop.growthSpeed(new SculkflowerCrop.Surroundings() {
            @Override
            public Soil soil(int dx, int dz) {
                Block floor = crop.getRelative(dx, -1, dz);
                if (!floor.getWorld().isChunkLoaded(floor.getX() >> 4, floor.getZ() >> 4)) {
                    return Soil.OTHER;
                }
                int moisture = floor.getBlockData() instanceof Farmland farmland ? farmland.getMoisture() : 0;
                return Soil.of(floorOf(floor), moisture);
            }

            @Override
            public boolean cropAt(int dx, int dz) {
                Block other = crop.getRelative(dx, 0, dz);
                return other.getWorld().isChunkLoaded(other.getX() >> 4, other.getZ() >> 4) && isCrop(other) && registered(other);
            }
        });
        if (SculkflowerCrop.growsOnRandomTick(random, light, speed)) {
            grow(crop, age + 1);
        }
    }

    // --- breaking --------------------------------------------------------------------------

    /** The mod's loot table for a crop (by age) or the flower; {@code yield} is the explosion's survival share. */
    List<ItemStack> drops(Block block, Float yield) {
        boolean crop = isCrop(block);
        SiftFixedLoot table = crop ? cropLoot : flowerLoot;
        Map<String, String> state = crop ? Map.of("age", String.valueOf(((Ageable) block.getBlockData()).getAge())) : Map.of();
        SiftFixedLoot.Context context = new SiftFixedLoot.Context(yield, crop ? CROP_BLOCK : FLOWER_BLOCK, state);
        List<ItemStack> stacks = new ArrayList<>();
        for (SiftFixedLoot.Drop drop : table.roll(random, context)) {
            items.apply(drop.item(), drop.count()).ifPresent(stacks::add);
        }
        return stacks;
    }

    private void dropAll(Block block, List<ItemStack> stacks) {
        stacks.forEach(stack -> block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.25, 0.5), stack));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!registered(block) || !stillThere(block)) {
            return;
        }
        if (event.isDropItems() && event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            dropAll(block, drops(block, null));
        }
        event.setDropItems(false);
        unregister(block);
    }

    /** Physics, pistons' breaking of blocks, entities and commands that destroy a block. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDestroy(BlockDestroyEvent event) {
        Block block = event.getBlock();
        if (!registered(block) || !stillThere(block)) {
            return;
        }
        if (event.willDrop()) {
            dropAll(block, drops(block, null));
        }
        event.setWillDrop(false);
        unregister(block);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        explode(event.blockList(), event.getYield());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        explode(event.blockList(), event.getYield());
    }

    /** The block loot tables' {@code survives_explosion}, with the explosion's yield, replaces the stand-in's drops. */
    private void explode(List<Block> blockList, float yield) {
        List<Block> mine = blockList.stream().filter(((Predicate<Block>) this::registered).and(this::stillThere)).toList();
        for (Block block : mine) {
            List<ItemStack> stacks = drops(block, yield);
            blockList.remove(block);
            block.setType(Material.AIR);
            dropAll(block, stacks);
            unregister(block);
        }
    }
}
