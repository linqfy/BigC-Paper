package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.platform.ClientPlatform;
import dev.linqfy.bigCasares.platform.ClientPlatformGateway;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Draws the mod's own block models over placed look-alikes ({@link SiftStandIns}): a willow slab is a warped slab
 * that Java players near it see with the mod's model. Each model of the block's state is an item display (the
 * models are exported as items by tools/export_sift_block_models.py) at the block, turned as the mod's blockstate
 * turns it. The exported models are grown by a hair so their faces cover the look-alike's instead of flickering
 * with them.
 *
 * <p>Proximity based: a placed look-alike gets its displays while a player who wants them is within
 * {@code the-sift.block-models.radius} blocks (the nearest {@link #MAX_PER_PLAYER} per player), and only those
 * players see them. Players switch the models off for themselves with {@code /siftmodels off}, and staff with
 * {@value #OTHERS_PERMISSION} for another player with {@code /siftmodels off <player>} (Bedrock players
 * never get them: Geyser does not show display entities). A state change (a door opening, stairs connecting)
 * rebuilds the displays within {@link #STATE_CHECK_TICKS} ticks.</p>
 *
 * <p>Not drawn: world-generated plants of the Sift, which are not remembered positions; a look-alike state the mod
 * has no model for (a wall sign). The displays are not saved; a restart or chunk unload makes them again.</p>
 */
final class SiftBlockModels implements CommandExecutor, TabCompleter {

    static final int SCAN_TICKS = 10;
    static final int STATE_CHECK_TICKS = 2;
    static final int MAX_PER_PLAYER = 600;
    static final String OTHERS_PERMISSION = "bigcasares.sift.models.others";

    /** {@code the-sift.block-models}: on/off and the radius in blocks. */
    record Settings(boolean enabled, int radius) {
        static final int DEFAULT_RADIUS = 24;

        static Settings load(ConfigurationSection root) {
            ConfigurationSection section = root == null ? null : root.getConfigurationSection("the-sift.block-models");
            int radius = section == null ? DEFAULT_RADIUS : section.getInt("radius", DEFAULT_RADIUS);
            return new Settings(section == null || section.getBoolean("enabled", true), Math.clamp(radius, 4, 96));
        }
    }

    private record Position(UUID world, long packed) {
    }

    private record Overlay(String state, List<ItemDisplay> displays, Set<UUID> viewers) {
        boolean valid() {
            return displays.stream().allMatch(ItemDisplay::isValid);
        }
    }

    private final JavaPlugin plugin;
    private final SiftStandIns standIns;
    private final SiftBlockModelStates states;
    private final ClientPlatformGateway platforms;
    private final int radius;
    private final NamespacedKey offKey;
    private final Map<Position, Overlay> overlays = new HashMap<>();
    private final Map<Position, SiftStandIns.StandIn> placed = new HashMap<>();

    SiftBlockModels(JavaPlugin plugin, SiftStandIns standIns, SiftBlockModelStates states,
                    ClientPlatformGateway platforms, int radius) {
        this.plugin = plugin;
        this.standIns = standIns;
        this.states = states;
        this.platforms = platforms;
        this.radius = radius;
        this.offKey = new NamespacedKey(plugin, "sift_block_models_off");
    }

    /** Every {@link #SCAN_TICKS}: which look-alikes each player wants drawn. */
    void scan() {
        Map<Position, Set<UUID>> wanted = new HashMap<>();
        Map<Position, SiftStandIns.StandIn> found = new HashMap<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!wants(player)) {
                continue;
            }
            List<Position> near = nearby(player, found);
            near.forEach(position -> wanted.computeIfAbsent(position, ignored -> new HashSet<>()).add(player.getUniqueId()));
        }
        for (Position position : List.copyOf(overlays.keySet())) {
            if (!wanted.containsKey(position)) {
                remove(position);
            }
        }
        placed.clear();
        placed.putAll(found);
        wanted.forEach((position, viewers) -> refresh(position, viewers));
    }

    /** Every {@link #STATE_CHECK_TICKS}: rebuilds the overlays whose block changed state. */
    void checkStates() {
        for (Map.Entry<Position, Overlay> entry : List.copyOf(overlays.entrySet())) {
            Block block = block(entry.getKey());
            if (block == null || !block.getBlockData().getAsString().equals(entry.getValue().state())
                || !entry.getValue().valid()) {
                refresh(entry.getKey(), Set.copyOf(entry.getValue().viewers()));
            }
        }
    }

    void removeAll() {
        List.copyOf(overlays.keySet()).forEach(this::remove);
    }

    private List<Position> nearby(Player player, Map<Position, SiftStandIns.StandIn> found) {
        World world = player.getWorld();
        Location at = player.getLocation();
        int chunkRadius = (radius >> 4) + 1;
        int cx = at.getBlockX() >> 4;
        int cz = at.getBlockZ() >> 4;
        double limit = (double) radius * radius;
        List<Position> near = new ArrayList<>();
        Map<Position, Double> distance = new HashMap<>();
        for (int x = cx - chunkRadius; x <= cx + chunkRadius; x++) {
            for (int z = cz - chunkRadius; z <= cz + chunkRadius; z++) {
                if (!world.isChunkLoaded(x, z)) {
                    continue;
                }
                Chunk chunk = world.getChunkAt(x, z);
                standIns.forEachPlaced(chunk, (standIn, packed) -> {
                    double dx = unpackX(packed) + 0.5 - at.getX();
                    double dy = unpackY(packed) + 0.5 - at.getY();
                    double dz = unpackZ(packed) + 0.5 - at.getZ();
                    double squared = dx * dx + dy * dy + dz * dz;
                    if (squared <= limit) {
                        Position position = new Position(world.getUID(), packed);
                        found.put(position, standIn);
                        distance.put(position, squared);
                        near.add(position);
                    }
                });
            }
        }
        if (near.size() > MAX_PER_PLAYER) {
            near.sort(Comparator.comparingDouble(distance::get));
            return near.subList(0, MAX_PER_PLAYER);
        }
        return near;
    }

    private void refresh(Position position, Set<UUID> viewers) {
        Block block = block(position);
        SiftStandIns.StandIn standIn = placed.get(position);
        Overlay current = overlays.get(position);
        if (block == null || standIn == null) {
            remove(position);
            return;
        }
        BlockData data = block.getBlockData();
        String state = data.getAsString();
        if (current == null || !current.state().equals(state) || !current.valid()) {
            remove(position);
            // A look-alike broken by water or a piston keeps its mark until its chunk is next checked; draw nothing.
            if (!standIn.item().equals(data.getPlacementMaterial())) {
                return;
            }
            List<SiftBlockModelStates.Piece> pieces = states.pieces(standIn.path(), SiftBlockModelStates.properties(state));
            if (pieces.isEmpty()) {
                return;
            }
            List<ItemDisplay> displays = new ArrayList<>();
            for (SiftBlockModelStates.Piece piece : pieces) {
                displays.add(spawn(block, piece));
            }
            current = new Overlay(state, displays, new HashSet<>());
            overlays.put(position, current);
        }
        Overlay overlay = current;
        for (UUID viewer : Set.copyOf(overlay.viewers())) {
            if (!viewers.contains(viewer)) {
                Player player = plugin.getServer().getPlayer(viewer);
                if (player != null) {
                    overlay.displays().forEach(display -> player.hideEntity(plugin, display));
                }
                overlay.viewers().remove(viewer);
            }
        }
        for (UUID viewer : viewers) {
            Player player = plugin.getServer().getPlayer(viewer);
            if (player != null && overlay.viewers().add(viewer)) {
                overlay.displays().forEach(display -> player.showEntity(plugin, display));
            }
        }
    }

    private ItemDisplay spawn(Block block, SiftBlockModelStates.Piece piece) {
        ItemStack item = new ItemStack(Material.PAPER);
        item.setData(DataComponentTypes.ITEM_MODEL, Key.key(piece.model()));
        // Blockstates turn a model around the block's centre, x first, both clockwise: rotationYXZ(-y, -x, 0). An item
        // display draws its item turned half a turn around y, which the last factor undoes.
        Quaternionf rotation = new Quaternionf()
            .rotateY((float) Math.toRadians(-piece.y()))
            .rotateX((float) Math.toRadians(-piece.x()))
            .rotateY((float) Math.PI);
        return block.getWorld().spawn(block.getLocation().add(0.5, 0.5, 0.5), ItemDisplay.class, display -> {
            display.setVisibleByDefault(false);
            display.setPersistent(false);
            display.setItemStack(item);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            display.setTransformation(new Transformation(new Vector3f(), rotation,
                new Vector3f(1.0F, 1.0F, 1.0F), new Quaternionf()));
            display.setViewRange(radius / 64.0F + 0.25F);
            display.setBillboard(Display.Billboard.FIXED);
        });
    }

    private void remove(Position position) {
        Overlay overlay = overlays.remove(position);
        if (overlay != null) {
            overlay.displays().forEach(ItemDisplay::remove);
        }
    }

    private Block block(Position position) {
        World world = plugin.getServer().getWorld(position.world());
        long packed = position.packed();
        int x = unpackX(packed);
        int z = unpackZ(packed);
        if (world == null || !world.isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }
        return world.getBlockAt(x, unpackY(packed), z);
    }

    private boolean wants(Player player) {
        return !player.getPersistentDataContainer().has(offKey, PersistentDataType.BYTE)
            && platforms.resolvePlatform(player.getUniqueId()) != ClientPlatform.BEDROCK;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Staff (and the console) may set it for another player: /siftmodels <on|off> <player>.
        CommandSender target = sender;
        if (args.length >= 2 && sender.hasPermission(OTHERS_PERMISSION)) {
            target = plugin.getServer().getPlayerExact(args[1]);
        }
        if (!(target instanceof Player player)) {
            sender.sendMessage(args.length >= 2 ? "Jugador no encontrado." : "Solo los jugadores tienen este ajuste.");
            return true;
        }
        boolean off = player.getPersistentDataContainer().has(offKey, PersistentDataType.BYTE);
        boolean turnOff = args.length == 0 ? !off : args[0].equalsIgnoreCase("off");
        if (args.length > 0 && !args[0].equalsIgnoreCase("on") && !args[0].equalsIgnoreCase("off")) {
            sender.sendMessage(Component.text("Uso: /" + label + " [on|off]", NamedTextColor.GRAY));
            return true;
        }
        if (turnOff) {
            player.getPersistentDataContainer().set(offKey, PersistentDataType.BYTE, (byte) 1);
        } else {
            player.getPersistentDataContainer().remove(offKey);
        }
        if (player != sender) {
            sender.sendMessage(player.getName() + ": " + (turnOff ? "off" : "on"));
        }
        player.sendMessage(Component.text(turnOff
            ? "Modelos de bloques de The Sift desactivados: losas, escaleras y plantas se ven como sus equivalentes vanilla."
            : "Modelos de bloques de The Sift activados.", NamedTextColor.GRAY));
        scan();
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("on", "off").stream().filter(option -> option.startsWith(args[0])).toList();
        }
        if (args.length == 2 && sender.hasPermission(OTHERS_PERMISSION)) {
            return plugin.getServer().getOnlinePlayers().stream().map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }

    // Packed as SiftChunkMarks.pack (vanilla's BlockPos.asLong).
    static int unpackX(long packed) {
        return (int) (packed >> 38);
    }

    static int unpackY(long packed) {
        return (int) (packed << 52 >> 52);
    }

    static int unpackZ(long packed) {
        return (int) (packed << 26 >> 38);
    }
}
