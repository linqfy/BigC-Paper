package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.blocks.CustomBlockDefinition;
import dev.linqfy.bigCasares.blocks.CustomBlockRegistry;
import dev.linqfy.bigCasares.blocks.CustomBlockService;
import dev.linqfy.bigCasares.content.ContentItem;
import dev.linqfy.bigCasares.content.ContentItemFactory;
import dev.linqfy.bigCasares.dimension.DimensionPlatform;
import dev.linqfy.bigCasares.dimension.NamespacedId;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Boat;
import org.bukkit.entity.ChestBoat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * The mod's blocks that are not full cubes, as vanilla look-alikes. Unmodified
 * clients cannot show new slabs, stairs, doors, signs or plants, so each is a
 * content item (the mod's icon and name) on the vanilla item of a close stand-in:
 * cobbled deepslate and deepslate brick shapes for Siftslate, warped wood for
 * Overgrown Willow (the nearest colour), light blue glass for Ichor glass and the
 * world generator's plant stand-ins for the plants. Vanilla places, orients and
 * connects them; the positions are remembered per chunk, so breaking one drops the
 * mod's item again (with vanilla's tool, silk touch and double-slab rules, which the
 * mod's loot tables copy), and plants in the Sift drop the mod's plant items.
 *
 * <p>This is a substitute, not a port: the placed blocks look like their stand-ins,
 * the boats are oak boats, and a stand-in broken by water or a piston drops the vanilla
 * item.</p>
 */
final class SiftStandIns implements Listener {

    /** A content item that places the vanilla block of {@code item}; {@code shearsOnly} plants need shears or silk touch. */
    record StandIn(String path, Material item, boolean shearsOnly, boolean siftWorldgen, String name) {
    }

    static final List<StandIn> ALL = List.of(
        block("cobbled_siftslate_slab", Material.COBBLED_DEEPSLATE_SLAB),
        block("cobbled_siftslate_stairs", Material.COBBLED_DEEPSLATE_STAIRS),
        block("cobbled_siftslate_wall", Material.COBBLED_DEEPSLATE_WALL),
        block("siftslate_brick_slab", Material.DEEPSLATE_BRICK_SLAB),
        block("siftslate_brick_stairs", Material.DEEPSLATE_BRICK_STAIRS),
        block("siftslate_brick_wall", Material.DEEPSLATE_BRICK_WALL),
        block("overgrown_willow_slab", Material.WARPED_SLAB),
        block("overgrown_willow_stairs", Material.WARPED_STAIRS),
        block("overgrown_willow_fence", Material.WARPED_FENCE),
        block("overgrown_willow_fence_gate", Material.WARPED_FENCE_GATE),
        block("overgrown_willow_door", Material.WARPED_DOOR),
        block("overgrown_willow_trapdoor", Material.WARPED_TRAPDOOR),
        block("overgrown_willow_button", Material.WARPED_BUTTON),
        block("overgrown_willow_pressure_plate", Material.WARPED_PRESSURE_PLATE),
        block("overgrown_willow_sign", Material.WARPED_SIGN),
        block("overgrown_willow_hanging_sign", Material.WARPED_HANGING_SIGN),
        block("overgrown_willow_shelf", Material.WARPED_SHELF),
        block("ichor_glass", Material.LIGHT_BLUE_STAINED_GLASS),
        block("ichor_glass_pane", Material.LIGHT_BLUE_STAINED_GLASS_PANE),
        new StandIn("overgrown_willow_boat", Material.OAK_BOAT, false, false, "item"),
        new StandIn("overgrown_willow_chest_boat", Material.OAK_CHEST_BOAT, false, false, "item"),
        plant("ichor_snow", Material.SNOW, false),
        plant("overgrown_lotus", Material.CORNFLOWER, false),
        plant("whisperbloom", Material.LILY_OF_THE_VALLEY, false),
        plant("sunburst_plant", Material.TORCHFLOWER, false),
        plant("overgrown_fronds", Material.SHORT_GRASS, true),
        plant("overgrown_chard", Material.FERN, true),
        plant("overgrown_stalks", Material.BUSH, true),
        plant("healthy_sculk_sprouts", Material.CRIMSON_ROOTS, true),
        plant("siftslate_stalks", Material.CRIMSON_ROOTS, true),
        plant("dry_healthy_sculk_sprouts", Material.SHORT_DRY_GRASS, true),
        plant("overgrown_hanging_roots", Material.HANGING_ROOTS, true),
        plant("siftslate_hanging_roots", Material.WEEPING_VINES, true),
        plant("overgrown_willow_vines", Material.VINE, true));

    /** Ichor glass items show vanilla glass: the mod's glass models use 26.3-only syntax and are not imported. */
    private static final Map<String, NamespacedId> VANILLA_MODELS = Map.of(
        "ichor_glass", NamespacedId.minecraft("light_blue_stained_glass"),
        "ichor_glass_pane", NamespacedId.minecraft("light_blue_stained_glass_pane"));

    private final Supplier<Optional<DimensionPlatform>> platform;
    private final ContentItemFactory items;
    private final CustomBlockRegistry blocks;
    private final Map<String, SiftChunkMarks> marks = new LinkedHashMap<>();
    private final NamespacedKey boatKey = new NamespacedKey(SiftBlocks.NS, "stand_in");

    SiftStandIns(Supplier<Optional<DimensionPlatform>> platform, ContentItemFactory items, CustomBlockRegistry blocks) {
        this.platform = platform;
        this.items = items;
        this.blocks = blocks;
        for (StandIn standIn : ALL) {
            marks.put(standIn.path(), new SiftChunkMarks(new NamespacedKey(SiftBlocks.NS, "placed_" + standIn.path())));
        }
    }

    private static StandIn block(String path, Material item) {
        return new StandIn(path, item, false, false, "block");
    }

    private static StandIn plant(String path, Material item, boolean shearsOnly) {
        return new StandIn(path, item, shearsOnly, true, "block");
    }

    /** The content items, for {@link SiftItems}. */
    static List<ContentItem> items() {
        List<ContentItem> list = new ArrayList<>();
        for (StandIn standIn : ALL) {
            ContentItem.Builder builder = ContentItem.builder(NamespacedId.of(SiftBlocks.NS, standIn.path()), standIn.item().name())
                .name(standIn.name() + ".the_sift." + standIn.path(), SiftItems.pretty(standIn.path()))
                .maxStack(maxStack(standIn.item()));
            NamespacedId model = VANILLA_MODELS.get(standIn.path());
            if (model != null) {
                builder.model(model);
            }
            list.add(builder.build());
        }
        return list;
    }

    /** Vanilla's stack sizes for these items (read without the server's item registry). */
    static int maxStack(Material item) {
        String name = item.name();
        if (name.endsWith("_BOAT")) {
            return 1;
        }
        return name.endsWith("_SIGN") ? 16 : 64;
    }

    /** Stand-ins whose item is {@code material}, in {@link #ALL} order. */
    static List<StandIn> forItem(Material material) {
        return ALL.stream().filter(standIn -> standIn.item() == material).toList();
    }

    /**
     * Which world-generated Sift plant a stand-in is: Siftslate Stalks and Healthy Sculk
     * Sprouts share crimson roots and are told apart by their floor.
     */
    static Optional<StandIn> worldgenPlant(Material material, boolean onSiftslate) {
        List<StandIn> candidates = forItem(material).stream().filter(StandIn::siftWorldgen).toList();
        if (candidates.size() > 1) {
            String wanted = onSiftslate ? "siftslate_stalks" : "healthy_sculk_sprouts";
            return candidates.stream().filter(standIn -> standIn.path().equals(wanted)).findFirst();
        }
        return candidates.stream().findFirst();
    }

    /** Placing a block sets or clears the marks of its position, so a vanilla block never inherits one. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        Optional<String> placed = ContentItemFactory.idOf(event.getItemInHand())
            .filter(id -> id.namespace().equals(SiftBlocks.NS)).map(NamespacedId::path).filter(marks::containsKey);
        for (StandIn standIn : forItem(block.getBlockData().getPlacementMaterial())) {
            boolean mine = placed.filter(standIn.path()::equals).isPresent();
            mark(standIn, block, mine);
            if (block.getBlockData() instanceof Bisected) {
                mark(standIn, block.getRelative(BlockFace.UP), mine);
            }
        }
    }

    /** A door broken at its upper half drops from the lower half in vanilla, outside any drop event. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!(block.getBlockData() instanceof Bisected bisected) || bisected.getHalf() != Bisected.Half.TOP) {
            return;
        }
        Block lower = block.getRelative(BlockFace.DOWN);
        Optional<StandIn> standIn = marked(lower);
        if (standIn.isEmpty()) {
            return;
        }
        mark(standIn.get(), block, false);
        mark(standIn.get(), lower, false);
        lower.setType(Material.AIR, false);
        if (event.isDropItems() && event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            items.create(NamespacedId.of(SiftBlocks.NS, standIn.get().path()).asString(), 1)
                .ifPresent(stack -> block.getWorld().dropItemNaturally(lower.getLocation().add(0.5, 0.5, 0.5), stack));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent event) {
        BlockState state = event.getBlockState();
        Block block = event.getBlock();
        Optional<StandIn> standIn = marked(block);
        if (standIn.isEmpty() && inSift(block)) {
            standIn = worldgenPlant(state.getBlockData().getPlacementMaterial(), onSiftslate(block));
        }
        if (standIn.isEmpty()) {
            return;
        }
        StandIn found = standIn.get();
        mark(found, block, false);
        if (state.getBlockData() instanceof Bisected) {
            mark(found, block.getRelative(BlockFace.UP), false);
        }
        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        boolean sheared = tool.getType() == Material.SHEARS || tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0;
        Iterator<Item> drops = event.getItems().iterator();
        while (drops.hasNext()) {
            Item drop = drops.next();
            ItemStack stack = drop.getItemStack();
            if (stack.getType() == found.item() && ContentItemFactory.idOf(stack).isEmpty()) {
                if (found.shearsOnly() && !sheared) {
                    drops.remove();
                } else {
                    items.create(NamespacedId.of(SiftBlocks.NS, found.path()).asString(), stack.getAmount())
                        .ifPresent(drop::setItemStack);
                }
            } else if (found.siftWorldgen() && found.item() != Material.SNOW && stack.getType() != Material.SNOWBALL) {
                // The mod's plants drop only themselves (no wheat seeds from "grass").
                drops.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        explode(event.blockList(), event.getYield());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        explode(event.blockList(), event.getYield());
    }

    /** Marked stand-ins in an explosion drop the mod's item with the explosion's chance. */
    private void explode(List<Block> destroyed, float yield) {
        Iterator<Block> iterator = destroyed.iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            Optional<StandIn> standIn = marked(block);
            if (standIn.isEmpty()) {
                continue;
            }
            iterator.remove();
            mark(standIn.get(), block, false);
            boolean dropped = !standIn.get().shearsOnly() && ThreadLocalRandom.current().nextFloat() < yield;
            BlockData data = block.getBlockData();
            boolean upper = data instanceof Bisected bisected && bisected.getHalf() == Bisected.Half.TOP;
            block.setType(Material.AIR, false);
            if (dropped && !upper) {
                items.create(NamespacedId.of(SiftBlocks.NS, standIn.get().path()).asString(), 1)
                    .ifPresent(stack -> block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), stack));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBoatPlace(EntityPlaceEvent event) {
        Player player = event.getPlayer();
        if (!(event.getEntity() instanceof Boat boat) || player == null) {
            return;
        }
        ItemStack held = player.getInventory().getItem(event.getHand());
        ContentItemFactory.idOf(held).filter(id -> id.namespace().equals(SiftBlocks.NS)).map(NamespacedId::path)
            .filter(path -> path.endsWith("_boat"))
            .ifPresent(path -> boat.getPersistentDataContainer().set(boatKey, PersistentDataType.STRING, path));
    }

    /** A willow boat breaks into the willow boat item (and its chest's contents). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBoatDestroy(VehicleDestroyEvent event) {
        Entity vehicle = event.getVehicle();
        String path = vehicle.getPersistentDataContainer().get(boatKey, PersistentDataType.STRING);
        if (path == null) {
            return;
        }
        event.setCancelled(true);
        if (vehicle instanceof ChestBoat chest) {
            for (ItemStack content : chest.getInventory().getContents()) {
                if (content != null && !content.getType().isAir()) {
                    vehicle.getWorld().dropItemNaturally(vehicle.getLocation(), content);
                }
            }
            chest.getInventory().clear();
        }
        boolean creative = event.getAttacker() instanceof Player player && player.getGameMode() == GameMode.CREATIVE;
        if (!creative) {
            items.create(NamespacedId.of(SiftBlocks.NS, path).asString(), 1)
                .ifPresent(stack -> vehicle.getWorld().dropItemNaturally(vehicle.getLocation(), stack));
        }
        vehicle.eject();
        vehicle.remove();
    }

    /** Calls {@code action} with every placed look-alike remembered in the chunk and its packed position. */
    void forEachPlaced(Chunk chunk, BiConsumer<StandIn, Long> action) {
        for (StandIn standIn : ALL) {
            SiftChunkMarks standInMarks = marks.get(standIn.path());
            for (long position : standInMarks.positions(chunk)) {
                action.accept(standIn, position);
            }
        }
    }

    private Optional<StandIn> marked(Block block) {
        for (StandIn standIn : forItem(block.getBlockData().getPlacementMaterial())) {
            if (marks.get(standIn.path()).isMarked(block)) {
                return Optional.of(standIn);
            }
        }
        return Optional.empty();
    }

    private void mark(StandIn standIn, Block block, boolean marked) {
        marks.get(standIn.path()).mark(block, marked);
    }

    private boolean onSiftslate(Block block) {
        return CustomBlockService.stateOf(block.getRelative(BlockFace.DOWN).getBlockData()).flatMap(blocks::byState)
            .map(CustomBlockDefinition::id).map(NamespacedId::path)
            .filter(path -> path.equals(SiftSoils.SIFTSLATE) || SiftSoils.isGrowth(path)).isPresent();
    }

    private boolean inSift(Block block) {
        return platform.get().flatMap(dimensions -> dimensions.dimensionOf(block.getWorld()))
            .filter(SiftDimension.KEY::equals).isPresent();
    }
}
