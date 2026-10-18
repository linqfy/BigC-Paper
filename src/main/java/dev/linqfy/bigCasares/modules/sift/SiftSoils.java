package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.blocks.CustomBlockDefinition;
import dev.linqfy.bigCasares.blocks.CustomBlockRegistry;
import dev.linqfy.bigCasares.blocks.CustomBlockService;
import dev.linqfy.bigCasares.dimension.DimensionPlatform;
import dev.linqfy.bigCasares.dimension.NamespacedId;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Sift soils ({@code SiftslateGrowthBlock}, {@code DryHealthySculkGrowthBlock} and
 * {@code SiftPlantInteractions} in the mod). Siftslate growth turns into siftslate
 * and dry sculk growth into dry healthy sculk when a full block or a fluid covers
 * them, and siftslate growth shows its snowy face under Ichor snow. Bone meal turns
 * siftslate next to growth into growth, grows patches of overgrown plants on growth
 * and spreads a Sift plant around itself.
 *
 * <p>Differences: custom blocks have no random ticks, so growth only reacts to
 * changes above it (a block placed or broken, a fluid flowing in, snow falling) and
 * not to darkness. Plants are their vanilla stand-ins, so in the Sift bone meal on a
 * stand-in (short grass for Overgrown Fronds, a fern for Overgrown Chard, ...)
 * spreads that stand-in instead of growing the vanilla plant.</p>
 */
final class SiftSoils implements Listener {

    static final String SIFTSLATE = "siftslate";
    static final String GROWTH = "siftslate_growth";
    static final String GROWTH_SNOWY = "siftslate_growth_snowy";
    static final String DRY_GROWTH = "dry_healthy_sculk_growth";
    static final String DRY_SCULK = "dry_healthy_sculk";
    /** The mod's {@code GROWTH_PLANTS}: fronds three times, chard twice, stalks once. */
    static final List<Material> GROWTH_PLANTS = List.of(Material.SHORT_GRASS, Material.SHORT_GRASS, Material.SHORT_GRASS,
        Material.FERN, Material.FERN, Material.BUSH);
    /** Stand-ins of the mod's {@code SiftPlantBlock}s. */
    static final Set<Material> SIFT_PLANTS = Set.of(Material.SHORT_GRASS, Material.FERN, Material.BUSH, Material.TORCHFLOWER,
        Material.LILY_OF_THE_VALLEY, Material.CRIMSON_ROOTS, Material.SHORT_DRY_GRASS);
    /** The mod's {@code findAnySiftFloor} soils. */
    static final Set<String> SIFT_FLOORS = Set.of(SIFTSLATE, GROWTH, GROWTH_SNOWY, "healthy_sculk", DRY_SCULK, DRY_GROWTH);

    private final JavaPlugin plugin;
    private final Supplier<Optional<DimensionPlatform>> platform;
    private final CustomBlockRegistry blocks;

    SiftSoils(JavaPlugin plugin, Supplier<Optional<DimensionPlatform>> platform, CustomBlockRegistry blocks) {
        this.plugin = plugin;
        this.platform = platform;
        this.blocks = blocks;
    }

    /** What a soil becomes with this cover above it, or empty when it stays. */
    static Optional<String> covered(String soil, boolean fullBlock, boolean fluid, boolean snow) {
        return switch (soil) {
            case GROWTH, GROWTH_SNOWY -> {
                if (snow) {
                    yield soil.equals(GROWTH_SNOWY) ? Optional.empty() : Optional.of(GROWTH_SNOWY);
                }
                if (fullBlock || fluid) {
                    yield Optional.of(SIFTSLATE);
                }
                yield soil.equals(GROWTH) ? Optional.empty() : Optional.of(GROWTH);
            }
            case DRY_GROWTH -> fullBlock || fluid ? Optional.of(DRY_SCULK) : Optional.empty();
            default -> Optional.empty();
        };
    }

    /** Both states of the mod's Siftslate Growth. */
    static boolean isGrowth(String path) {
        return path.equals(GROWTH) || path.equals(GROWTH_SNOWY);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        update(event.getBlockPlaced().getRelative(BlockFace.DOWN));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block below = event.getBlock().getRelative(BlockFace.DOWN);
        plugin.getServer().getScheduler().runTask(plugin, () -> update(below));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        Block below = event.getToBlock().getRelative(BlockFace.DOWN);
        plugin.getServer().getScheduler().runTask(plugin, () -> update(below));
    }

    /** Re-checks a soil after the block above it changed (also called by the Ichor snowfall). */
    void update(Block soil) {
        Optional<String> id = sift(soil);
        if (id.isEmpty()) {
            return;
        }
        Block above = soil.getRelative(BlockFace.UP);
        boolean fullBlock = above.getType().isOccluding();
        boolean fluid = above.isLiquid() || above.getBlockData() instanceof Waterlogged waterlogged
            && waterlogged.isWaterlogged();
        covered(id.get(), fullBlock, fluid, above.getType() == Material.SNOW).ifPresent(next -> set(soil, next));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBoneMeal(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        ItemStack held = event.getItem();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || clicked == null || held == null
            || held.getType() != Material.BONE_MEAL || event.useInteractedBlock() == Event.Result.DENY) {
            return;
        }
        Optional<String> soil = sift(clicked);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        boolean used;
        if (soil.filter(SIFTSLATE::equals).isPresent()) {
            used = clicked.getRelative(BlockFace.UP).getType().isAir() && hasAdjacentGrowth(clicked);
            if (used) {
                set(clicked, GROWTH);
            }
        } else if (soil.filter(SiftSoils::isGrowth).isPresent()) {
            used = true;
            growPatches(clicked, random);
        } else if (SIFT_PLANTS.contains(clicked.getType()) && inSift(clicked)) {
            used = true;
            spread(clicked, clicked.getType(), random);
        } else {
            return;
        }
        if (!used) {
            return;
        }
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            held.setAmount(held.getAmount() - 1);
        }
        clicked.getWorld().playEffect(clicked.getLocation(), Effect.BONE_MEAL_USE, 0);
    }

    private boolean hasAdjacentGrowth(Block block) {
        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)) {
            if (sift(block.getRelative(face)).filter(SiftSoils::isGrowth).isPresent()) {
                return true;
            }
        }
        return false;
    }

    /** The mod's {@code growGrowthPatch}: 3-5 patches of 4-10 tries within ±3 then ±2 blocks. */
    private void growPatches(Block origin, ThreadLocalRandom random) {
        int patches = 3 + random.nextInt(3);
        for (int patch = 0; patch < patches; patch++) {
            Material plant = GROWTH_PLANTS.get(random.nextInt(GROWTH_PLANTS.size()));
            int cx = origin.getX() + random.nextInt(7) - 3;
            int cz = origin.getZ() + random.nextInt(7) - 3;
            int attempts = 4 + random.nextInt(7);
            for (int i = 0; i < attempts; i++) {
                Block floor = surface(origin, cx + random.nextInt(5) - 2, origin.getY(), cz + random.nextInt(5) - 2,
                    Set.of(GROWTH, GROWTH_SNOWY));
                if (floor != null) {
                    plant(floor.getRelative(BlockFace.UP), plant);
                }
            }
        }
    }

    /** The mod's {@code spreadSamePlant}: 7-13 tries within ±3 blocks. */
    private void spread(Block origin, Material plant, ThreadLocalRandom random) {
        int attempts = 7 + random.nextInt(7);
        for (int i = 0; i < attempts; i++) {
            Block floor = surface(origin, origin.getX() + random.nextInt(7) - 3, origin.getY() - 1,
                origin.getZ() + random.nextInt(7) - 3, SIFT_FLOORS);
            if (floor != null) {
                plant(floor.getRelative(BlockFace.UP), plant);
            }
        }
    }

    /** A wanted soil with air above, searched from 3 above to 3 below. */
    private Block surface(Block origin, int x, int aroundY, int z, Set<String> wanted) {
        for (int dy = 3; dy >= -3; dy--) {
            Block candidate = origin.getWorld().getBlockAt(x, aroundY + dy, z);
            if (sift(candidate).filter(wanted::contains).isPresent() && candidate.getRelative(BlockFace.UP).getType().isAir()) {
                return candidate;
            }
        }
        return null;
    }

    private static void plant(Block block, Material plant) {
        BlockData data = plant.createBlockData();
        if (block.getType().isAir() && data.isSupported(block)) {
            block.setBlockData(data);
        }
    }

    private Optional<String> sift(Block block) {
        return CustomBlockService.stateOf(block.getBlockData()).flatMap(blocks::byState).map(CustomBlockDefinition::id)
            .filter(id -> id.namespace().equals(SiftBlocks.NS)).map(NamespacedId::path);
    }

    private void set(Block block, String path) {
        blocks.byId(NamespacedId.of(SiftBlocks.NS, path))
            .ifPresent(definition -> block.setBlockData(Bukkit.createBlockData(definition.state().asString()), false));
    }

    private boolean inSift(Block block) {
        return platform.get().flatMap(dimensions -> dimensions.dimensionOf(block.getWorld()))
            .filter(SiftDimension.KEY::equals).isPresent();
    }
}
