package dev.linqfy.bigCasares.blocks;

import dev.linqfy.bigCasares.blocks.BreakSpeedModel.HeldTool;
import dev.linqfy.bigCasares.blocks.CustomBlockDefinition.ToolTier;
import dev.linqfy.bigCasares.blocks.CustomBlockDefinition.ToolType;
import dev.linqfy.bigCasares.content.ContentItem;
import dev.linqfy.bigCasares.content.ContentItemFactory;
import dev.linqfy.bigCasares.dimension.NamespacedId;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Server-side behaviour of custom blocks: exact-state placement, emulated
 * hardness/tool rules, custom drops (including explosions), sounds, and a guard
 * that keeps player-placed vanilla mushroom blocks out of the reserved pool.
 */
public final class CustomBlockService implements Listener {

    private final JavaPlugin plugin;
    private final CustomBlockRegistry blocks;
    private final ContentItemFactory items;
    private final NamespacedKey speedModifierKey;

    public CustomBlockService(JavaPlugin plugin, CustomBlockRegistry blocks, ContentItemFactory items) {
        this.plugin = plugin;
        this.blocks = blocks;
        this.items = items;
        this.speedModifierKey = new NamespacedKey(plugin, "custom_block_break_speed");
    }

    /** Warns when Paper may recompute mushroom faces, which could change block identity. */
    public void checkServerConfiguration() {
        Path serverRoot = plugin.getDataFolder().toPath().toAbsolutePath().getParent().getParent();
        Path paperGlobal = serverRoot.resolve("config/paper-global.yml");
        boolean disabled = false;
        if (Files.isRegularFile(paperGlobal)) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(paperGlobal.toFile());
            disabled = yaml.getBoolean("block-updates.disable-mushroom-block-updates", false);
        }
        if (!disabled) {
            plugin.getLogger().warning("[CustomBlocks] Set block-updates.disable-mushroom-block-updates: true in "
                + paperGlobal + " and restart. Without it, neighbour updates between same-backed custom blocks"
                + " (e.g. pistons moving them together) can change which custom block is shown.");
        }
    }

    public Optional<CustomBlockDefinition> definitionAt(Block block) {
        return stateOf(block.getBlockData()).flatMap(blocks::byState);
    }

    public static Optional<MushroomState> stateOf(BlockData data) {
        MushroomState.Base base = switch (data.getMaterial()) {
            case BROWN_MUSHROOM_BLOCK -> MushroomState.Base.BROWN;
            case RED_MUSHROOM_BLOCK -> MushroomState.Base.RED;
            case MUSHROOM_STEM -> MushroomState.Base.STEM;
            default -> null;
        };
        if (base == null || !(data instanceof MultipleFacing facing)) {
            return Optional.empty();
        }
        return Optional.of(MushroomState.of(base, facing.hasFace(BlockFace.NORTH), facing.hasFace(BlockFace.EAST),
            facing.hasFace(BlockFace.SOUTH), facing.hasFace(BlockFace.WEST), facing.hasFace(BlockFace.UP),
            facing.hasFace(BlockFace.DOWN)));
    }

    public BlockData blockData(CustomBlockDefinition definition) {
        return plugin.getServer().createBlockData(definition.state().asString());
    }

    /** Places a custom block without neighbour updates. */
    public void place(Block block, CustomBlockDefinition definition) {
        block.setBlockData(blockData(definition), false);
    }

    // --- placement -----------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack hand = event.getItemInHand();
        Optional<ContentItem> content = items.itemOf(hand);
        Block placed = event.getBlockPlaced();
        if (content.isPresent() && content.get().placesBlock() != null) {
            Optional<CustomBlockDefinition> definition = blocks.byId(axisVariant(content.get().placesBlock(),
                event.getBlockAgainst().getFace(placed)));
            if (definition.isEmpty()) {
                event.setCancelled(true);
                return;
            }
            place(placed, definition.get());
            playSound(placed.getLocation(), definition.get().soundGroup(), "place");
            return;
        }
        // Vanilla mushroom blocks must never land on a reserved state.
        stateOf(placed.getBlockData()).filter(MushroomState::isReservable).ifPresent(state ->
            placed.setBlockData(plugin.getServer().createBlockData(
                MushroomState.of(state.base(), true, true, true, true, true, true).asString()), false));
    }

    /**
     * Pillar convention: a block {@code foo} may register {@code foo_x} and {@code foo_z}
     * siblings; placing against a horizontal face picks the matching axis variant.
     */
    private NamespacedId axisVariant(NamespacedId base, BlockFace face) {
        if (face == null) {
            return base;
        }
        String suffix = switch (face) {
            case EAST, WEST -> "_x";
            case NORTH, SOUTH -> "_z";
            default -> null;
        };
        if (suffix == null) {
            return base;
        }
        NamespacedId variant = NamespacedId.of(base.namespace(), base.path() + suffix);
        return blocks.byId(variant).isPresent() ? variant : base;
    }

    // --- mining --------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(BlockDamageEvent event) {
        Optional<CustomBlockDefinition> definition = definitionAt(event.getBlock());
        clearSpeed(event.getPlayer());
        if (definition.isEmpty() || event.getInstaBreak()) {
            return;
        }
        double multiplier = BreakSpeedModel.multiplier(definition.get(), heldTool(event.getItemInHand()));
        AttributeInstance attribute = event.getPlayer().getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (attribute != null && Math.abs(multiplier - 1.0) > 1.0E-6) {
            attribute.addTransientModifier(new AttributeModifier(speedModifierKey, multiplier - 1.0,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAbort(BlockDamageAbortEvent event) {
        clearSpeed(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHeldChange(PlayerItemHeldEvent event) {
        clearSpeed(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        clearSpeed(event.getPlayer());
    }

    private void clearSpeed(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (attribute != null) {
            attribute.removeModifier(speedModifierKey);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Optional<CustomBlockDefinition> definition = definitionAt(event.getBlock());
        clearSpeed(event.getPlayer());
        if (definition.isEmpty()) {
            return;
        }
        CustomBlockDefinition block = definition.get();
        event.setDropItems(false);
        event.setExpToDrop(0);
        playSound(event.getBlock().getLocation(), block.soundGroup(), "break");
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) {
            return;
        }
        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        HeldTool held = heldTool(tool);
        if (!BreakSpeedModel.canHarvest(block, held)) {
            return;
        }
        Location location = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
        boolean silk = tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0;
        int fortune = tool.getEnchantmentLevel(Enchantment.FORTUNE);
        dropFor(block, location, silk, fortune);
        if (!silk && block.maxExperience() > 0) {
            int xp = ThreadLocalRandom.current().nextInt(block.minExperience(), block.maxExperience() + 1);
            if (xp > 0) {
                location.getWorld().spawn(location, ExperienceOrb.class, orb -> orb.setExperience(xp));
            }
        }
    }

    private void dropFor(CustomBlockDefinition block, Location location, boolean silk, int fortune) {
        if (silk && block.silkTouchDropsSelf() || block.drops().isEmpty()) {
            items.resolve(block.id().asString(), 1).ifPresent(stack -> location.getWorld().dropItemNaturally(location, stack));
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (CustomBlockDefinition.Drop drop : block.drops()) {
            int count = random.nextInt(drop.min(), drop.max() + 1);
            if (drop.fortune() && fortune > 0) {
                int bonus = random.nextInt(fortune + 2) - 1;
                count *= Math.max(0, bonus) + 1;
            }
            if (count > 0) {
                items.resolve(drop.item(), count).ifPresent(stack -> location.getWorld().dropItemNaturally(location, stack));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        handleExplosion(event.blockList(), event.getYield());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        handleExplosion(event.blockList(), event.getYield());
    }

    private void handleExplosion(List<Block> affected, float yield) {
        Iterator<Block> iterator = affected.iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            Optional<CustomBlockDefinition> definition = definitionAt(block);
            if (definition.isEmpty()) {
                continue;
            }
            iterator.remove();
            block.setType(Material.AIR, false);
            if (ThreadLocalRandom.current().nextFloat() < yield) {
                dropFor(definition.get(), block.getLocation().add(0.5, 0.5, 0.5), false, 0);
            }
        }
    }

    private static void playSound(Location location, String group, String action) {
        Sound sound = Registry.SOUNDS.get(NamespacedKey.minecraft(group + "." + action));
        if (sound != null && location.getWorld() != null) {
            location.getWorld().playSound(location, sound, 1.0F, 0.9F);
        }
    }

    /** Classifies the held item; content tools can declare tier via their base material. */
    static HeldTool heldTool(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return HeldTool.HAND;
        }
        String name = stack.getType().name();
        ToolType type;
        if (name.endsWith("_PICKAXE")) {
            type = ToolType.PICKAXE;
        } else if (name.endsWith("_AXE")) {
            type = ToolType.AXE;
        } else if (name.endsWith("_SHOVEL")) {
            type = ToolType.SHOVEL;
        } else if (name.endsWith("_HOE")) {
            type = ToolType.HOE;
        } else {
            return HeldTool.HAND;
        }
        String prefix = name.substring(0, name.indexOf('_')).toUpperCase(Locale.ROOT);
        ToolTier tier = switch (prefix) {
            case "WOODEN" -> ToolTier.WOOD;
            case "STONE" -> ToolTier.STONE;
            case "COPPER" -> ToolTier.COPPER;
            case "IRON" -> ToolTier.IRON;
            case "GOLDEN" -> ToolTier.GOLD;
            case "DIAMOND" -> ToolTier.DIAMOND;
            case "NETHERITE" -> ToolTier.NETHERITE;
            default -> ToolTier.WOOD;
        };
        return new HeldTool(type, tier, stack.getEnchantmentLevel(Enchantment.EFFICIENCY));
    }

    public static NamespacedId idOf(CustomBlockDefinition definition) {
        return definition.id();
    }
}
