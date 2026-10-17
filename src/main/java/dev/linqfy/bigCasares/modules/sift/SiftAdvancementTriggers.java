package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.blocks.CustomBlockDefinition;
import dev.linqfy.bigCasares.blocks.CustomBlockRegistry;
import dev.linqfy.bigCasares.blocks.CustomBlockService;
import dev.linqfy.bigCasares.dimension.DimensionKey;
import dev.linqfy.bigCasares.dimension.DimensionPlatform;
import dev.linqfy.bigCasares.dimension.NamespacedId;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Awards the mod's code-granted advancements that are not tied to a creature or
 * the ritual ({@code ModAdvancements} in the mod): mining Charoite and Siftite
 * ore, and setting wood on fire in the Sift.
 */
final class SiftAdvancementTriggers implements Listener {

    private static final NamespacedId CHAROITE_ORE = NamespacedId.of(SiftBlocks.NS, "siftslate_charoite_ore");
    private static final NamespacedId SIFTITE_ORE = NamespacedId.of(SiftBlocks.NS, "siftslate_siftite_ore");
    /** Willow logs, wood and planks, including the axis variants. */
    private static final Pattern WILLOW_WOOD = Pattern.compile("(stripped_)?overgrown_willow_(log|wood|planks)(_[xz])?");
    private static final List<Tag<Material>> WOOD_TAGS = List.of(Tag.LOGS, Tag.PLANKS, Tag.WOODEN_STAIRS,
        Tag.WOODEN_SLABS, Tag.WOODEN_FENCES, Tag.WOODEN_DOORS, Tag.WOODEN_TRAPDOORS, Tag.WOODEN_BUTTONS,
        Tag.WOODEN_PRESSURE_PLATES, Tag.WOODEN_SHELVES);

    private final Supplier<Optional<DimensionPlatform>> platform;
    private final CustomBlockRegistry blocks;

    SiftAdvancementTriggers(Supplier<Optional<DimensionPlatform>> platform, CustomBlockRegistry blocks) {
        this.platform = platform;
        this.blocks = blocks;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Optional<NamespacedId> broken = customBlock(event.getBlock());
        if (broken.isEmpty()) {
            return;
        }
        if (broken.get().equals(CHAROITE_ORE)) {
            SiftAdvancements.award(event.getPlayer(), SiftAdvancements.KINDA_PURPLEISH);
        } else if (broken.get().equals(SIFTITE_ORE)) {
            SiftAdvancements.award(event.getPlayer(), SiftAdvancements.SIFTED_NUGGET);
        }
    }

    /** Flint and steel or a fire charge used on wood in the Sift where fire can be placed. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onIgnite(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        ItemStack held = event.getItem();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || clicked == null || held == null
            || event.useItemInHand() == Event.Result.DENY
            || (held.getType() != Material.FLINT_AND_STEEL && held.getType() != Material.FIRE_CHARGE)
            || !inSift(clicked) || !isWood(clicked)) {
            return;
        }
        Block fire = clicked.getRelative(event.getBlockFace());
        boolean replaceable = fire.isEmpty() || fire.isReplaceable();
        boolean supported = clicked.getType().isFlammable() || fire.getRelative(BlockFace.DOWN).getType().isSolid();
        if (replaceable && supported) {
            SiftAdvancements.award(event.getPlayer(), SiftAdvancements.SPICEWOOD);
        }
    }

    private boolean isWood(Block block) {
        Material type = block.getType();
        if (WOOD_TAGS.stream().anyMatch(tag -> tag.isTagged(type))) {
            return true;
        }
        return customBlock(block).filter(id -> WILLOW_WOOD.matcher(id.path()).matches()).isPresent();
    }

    private boolean inSift(Block block) {
        Optional<DimensionKey> dimension = platform.get().flatMap(dimensions -> dimensions.dimensionOf(block.getWorld()));
        return dimension.filter(SiftDimension.KEY::equals).isPresent();
    }

    private Optional<NamespacedId> customBlock(Block block) {
        return CustomBlockService.stateOf(block.getBlockData()).flatMap(blocks::byState).map(CustomBlockDefinition::id)
            .filter(id -> id.namespace().equals(SiftBlocks.NS));
    }
}
