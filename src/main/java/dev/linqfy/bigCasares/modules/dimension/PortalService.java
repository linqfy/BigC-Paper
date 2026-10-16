package dev.linqfy.bigCasares.modules.dimension;

import dev.linqfy.bigCasares.dimension.DimensionDefinition;
import dev.linqfy.bigCasares.dimension.DimensionKey;
import dev.linqfy.bigCasares.dimension.event.DimensionPortalActivateEvent;
import dev.linqfy.bigCasares.dimension.event.DimensionTravelEvent;
import dev.linqfy.bigCasares.dimension.persistence.DimensionDataStore;
import dev.linqfy.bigCasares.dimension.persistence.DimensionDataStore.StoredPortal;
import dev.linqfy.bigCasares.dimension.travel.PortalDefinition;
import dev.linqfy.bigCasares.dimension.travel.PortalShape;
import dev.linqfy.bigCasares.dimension.travel.TravelResult;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * Builds, indexes and operates portals declared by registered dimensions.
 *
 * <p>Entry detection is a hash lookup on block-position changes in worlds that
 * contain portals, so worlds without portals pay nothing beyond a map miss.</p>
 */
final class PortalService {

    private record BuiltPortal(StoredPortal stored, PortalShape shape, PortalDefinition definition, DimensionKey dimension) {
        boolean leadsInto() {
            return stored.linkedPortal() == null || !stored.id().startsWith("exit-");
        }
    }

    private final JavaPlugin plugin;
    private final DimensionRuntime runtime;
    private final DimensionDataStore store;
    private final BiPredicate<ItemStack, String> itemMatcher;
    private final NamespacedKey portalTag;
    private final Map<String, Map<Long, List<BuiltPortal>>> index = new HashMap<>();
    private final Map<String, BuiltPortal> byId = new HashMap<>();
    /** Players who must step out of a portal before it can trigger again. */
    private final Set<UUID> suppressed = new HashSet<>();

    PortalService(JavaPlugin plugin, DimensionRuntime runtime, DimensionDataStore store, BiPredicate<ItemStack, String> itemMatcher) {
        this.plugin = plugin;
        this.runtime = runtime;
        this.store = store;
        this.itemMatcher = itemMatcher;
        this.portalTag = new NamespacedKey(plugin, "dimension_portal");
    }

    /** Rebuilds the in-memory index from persisted portals of registered dimensions. */
    void reload() {
        index.clear();
        byId.clear();
        for (StoredPortal stored : store.portals()) {
            DimensionKey key;
            try {
                key = DimensionKey.parse(stored.dimension());
            } catch (IllegalArgumentException invalid) {
                plugin.getLogger().warning("[Dimensions] Ignoring portal " + stored.id() + " with invalid dimension");
                continue;
            }
            Optional<PortalDefinition> definition = runtime.definition(key)
                .flatMap(def -> def.travel().portals().stream().filter(p -> p.id().equals(stored.definition())).findFirst());
            if (definition.isEmpty()) {
                continue;
            }
            PortalShape shape = new PortalShape(PortalShape.Axis.valueOf(stored.axis()),
                stored.minX(), stored.minY(), stored.minZ(), stored.maxX(), stored.maxY(), stored.maxZ());
            add(new BuiltPortal(stored, shape, definition.get(), key));
        }
    }

    private void add(BuiltPortal portal) {
        byId.put(portal.stored().id(), portal);
        Map<Long, List<BuiltPortal>> chunks = index.computeIfAbsent(portal.stored().world(), ignored -> new HashMap<>());
        for (int cx = portal.shape().minX() >> 4; cx <= portal.shape().maxX() >> 4; cx++) {
            for (int cz = portal.shape().minZ() >> 4; cz <= portal.shape().maxZ() >> 4; cz++) {
                chunks.computeIfAbsent(chunkKey(cx, cz), ignored -> new ArrayList<>()).add(portal);
            }
        }
    }

    private void remove(BuiltPortal portal) {
        byId.remove(portal.stored().id());
        Map<Long, List<BuiltPortal>> chunks = index.get(portal.stored().world());
        if (chunks != null) {
            chunks.values().forEach(list -> list.remove(portal));
            chunks.values().removeIf(List::isEmpty);
        }
        store.removePortal(portal.stored().id());
    }

    Optional<BuiltPortal> portalAt(World world, int x, int y, int z) {
        Map<Long, List<BuiltPortal>> chunks = index.get(world.getName());
        if (chunks == null) {
            return Optional.empty();
        }
        List<BuiltPortal> candidates = chunks.get(chunkKey(x >> 4, z >> 4));
        if (candidates == null) {
            return Optional.empty();
        }
        return candidates.stream().filter(portal -> portal.shape().contains(x, y, z)).findFirst();
    }

    boolean hasPortals(World world) {
        Map<Long, List<BuiltPortal>> chunks = index.get(world.getName());
        return chunks != null && !chunks.isEmpty();
    }

    // --- activation -----------------------------------------------------------------------

    /** Attempts to light a frame with the held item; returns true when the click was consumed. */
    boolean tryActivate(Player player, Block clicked, ItemStack held) {
        if (held == null || held.getType().isAir()) {
            return false;
        }
        String clickedId = clicked.getType().getKey().asString();
        for (DimensionDefinition definition : runtime.definitions()) {
            if (runtime.active(definition.key()).isEmpty()) {
                continue;
            }
            for (PortalDefinition portal : definition.travel().portals()) {
                if (!portal.frameBlocks().contains(clickedId) || !itemMatcher.test(held, portal.activationItem())) {
                    continue;
                }
                if (runtime.dimensionOf(clicked.getWorld()).isPresent()) {
                    player.sendMessage("§cEste portal solo se puede encender desde fuera de la dimensión.");
                    return true;
                }
                BukkitBlockGrid grid = new BukkitBlockGrid(clicked.getWorld());
                Optional<PortalShape> shape = portal.detector().detect(grid, clicked.getX(), clicked.getY(), clicked.getZ());
                if (shape.isEmpty()) {
                    continue;
                }
                if (portalAt(clicked.getWorld(), shape.get().minX(), shape.get().minY(), shape.get().minZ()).isPresent()) {
                    return true;
                }
                DimensionPortalActivateEvent event = new DimensionPortalActivateEvent(
                    player, definition.key(), portal.id(), clicked.getWorld(), shape.get());
                plugin.getServer().getPluginManager().callEvent(event);
                if (event.isCancelled()) {
                    return true;
                }
                build(clicked.getWorld(), shape.get(), portal, definition.key(), "portal-" + UUID.randomUUID(), null);
                if (portal.consumeActivationItem() && player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                    held.setAmount(held.getAmount() - 1);
                }
                clicked.getWorld().playSound(clicked.getLocation(), org.bukkit.Sound.BLOCK_END_PORTAL_SPAWN, 0.8F, 1.2F);
                return true;
            }
        }
        return false;
    }

    private BuiltPortal build(World world, PortalShape shape, PortalDefinition portal, DimensionKey key, String id, String linked) {
        fillInterior(world, shape, portal.interiorLight());
        StoredPortal stored = new StoredPortal(id, portal.id(), key.asString(), world.getName(), shape.axis().name(),
            shape.minX(), shape.minY(), shape.minZ(), shape.maxX(), shape.maxY(), shape.maxZ(), linked);
        store.putPortal(stored);
        BuiltPortal built = new BuiltPortal(stored, shape, portal, key);
        add(built);
        spawnSurface(world, built);
        return built;
    }

    private static void fillInterior(World world, PortalShape shape, int lightLevel) {
        for (int x = shape.minX(); x <= shape.maxX(); x++) {
            for (int y = shape.minY(); y <= shape.maxY(); y++) {
                for (int z = shape.minZ(); z <= shape.maxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (lightLevel > 0) {
                        Light light = (Light) Material.LIGHT.createBlockData();
                        light.setLevel(lightLevel);
                        block.setBlockData(light, false);
                    } else if (block.getType() == Material.LIGHT) {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    private void spawnSurface(World world, BuiltPortal portal) {
        String model = portal.definition().surfaceItemModel();
        if (model == null) {
            return;
        }
        PortalShape shape = portal.shape();
        Location center = new Location(world, shape.centerX(), shape.minY() + shape.height() / 2.0, shape.centerZ());
        world.spawn(center, ItemDisplay.class, display -> {
            ItemStack stack = new ItemStack(Material.PAPER);
            ItemMeta meta = stack.getItemMeta();
            meta.setItemModel(NamespacedKey.fromString(model));
            stack.setItemMeta(meta);
            display.setItemStack(stack);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setBrightness(new Display.Brightness(15, 15));
            float yaw = shape.axis() == PortalShape.Axis.Z ? (float) (Math.PI / 2.0) : 0.0F;
            display.setTransformation(new Transformation(
                new Vector3f(), new AxisAngle4f(yaw, 0, 1, 0),
                new Vector3f(shape.width(), shape.height(), 1.0F), new AxisAngle4f()));
            display.setPersistent(true);
            display.getPersistentDataContainer().set(portalTag, PersistentDataType.STRING, portal.stored().id());
        });
    }

    private void removeSurface(World world, BuiltPortal portal) {
        Location center = new Location(world, portal.shape().centerX(), portal.shape().minY(), portal.shape().centerZ());
        double radius = Math.max(portal.shape().width(), portal.shape().height()) + 2.0;
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius,
            entity -> entity instanceof ItemDisplay)) {
            String id = entity.getPersistentDataContainer().get(portalTag, PersistentDataType.STRING);
            if (portal.stored().id().equals(id)) {
                entity.remove();
            }
        }
    }

    /** Called when a block changes; breaks portals whose frame or interior was disturbed. */
    void onBlockDisturbed(Block block) {
        World world = block.getWorld();
        if (!hasPortals(world)) {
            return;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Optional<BuiltPortal> portal = portalAt(world, block.getX() + dx, block.getY() + dy, block.getZ() + dz);
                    if (portal.isPresent() && isPartOf(portal.get(), block)) {
                        deactivate(world, portal.get());
                        return;
                    }
                }
            }
        }
    }

    private static boolean isPartOf(BuiltPortal portal, Block block) {
        PortalShape shape = portal.shape();
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        boolean inPlane = shape.axis() == PortalShape.Axis.X ? z == shape.minZ() : x == shape.minX();
        return inPlane && x >= shape.minX() - 1 && x <= shape.maxX() + 1 && y >= shape.minY() - 1
            && y <= shape.maxY() + 1 && z >= shape.minZ() - 1 && z <= shape.maxZ() + 1;
    }

    private void deactivate(World world, BuiltPortal portal) {
        fillInterior(world, portal.shape(), 0);
        removeSurface(world, portal);
        remove(portal);
    }

    // --- entry ---------------------------------------------------------------------------

    void onMove(Player player, Location to) {
        Optional<BuiltPortal> portal = portalAt(to.getWorld(), to.getBlockX(), to.getBlockY(), to.getBlockZ());
        if (portal.isEmpty()) {
            suppressed.remove(player.getUniqueId());
            return;
        }
        if (!suppressed.add(player.getUniqueId())) {
            return;
        }
        BuiltPortal used = portal.get();
        boolean insideDimension = runtime.dimensionOf(to.getWorld()).isPresent();
        if (insideDimension) {
            runtime.leave(player, DimensionTravelEvent.Cause.PORTAL).thenAccept(result -> report(player, result));
        } else {
            runtime.enter(player, used.dimension(), DimensionTravelEvent.Cause.PORTAL).thenAccept(result -> {
                report(player, result);
                if (result.succeeded() && used.definition().createExitPortal()) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> ensureExitPortal(player, used));
                }
            });
        }
    }

    private void report(Player player, TravelResult result) {
        if (!result.succeeded() && player.isOnline()) {
            player.sendMessage("§cEl portal no responde: " + result.status().name().toLowerCase()
                + (result.message().isBlank() ? "" : " (" + result.message() + ")"));
        }
    }

    /** Builds a frame around the arrival point so players can return the way they came. */
    private void ensureExitPortal(Player player, BuiltPortal source) {
        World world = player.getWorld();
        Location at = player.getLocation();
        for (BuiltPortal existing : byId.values()) {
            if (existing.stored().world().equals(world.getName())
                && Math.abs(existing.shape().centerX() - at.getX()) < 24
                && Math.abs(existing.shape().centerZ() - at.getZ()) < 24) {
                suppressed.add(player.getUniqueId());
                return;
            }
        }
        Material frame = Optional.ofNullable(Material.matchMaterial(source.definition().frameBlocks().iterator().next()))
            .filter(Material::isBlock).orElse(Material.CRYING_OBSIDIAN);
        int width = Math.max(2, source.definition().minWidth());
        int height = Math.max(3, source.definition().minHeight());
        int baseX = at.getBlockX() + 2;
        int baseY = at.getBlockY();
        int z = at.getBlockZ();
        for (int dx = -1; dx <= width; dx++) {
            for (int dy = -1; dy <= height; dy++) {
                Block block = world.getBlockAt(baseX + dx, baseY + dy, z);
                boolean border = dx == -1 || dx == width || dy == -1 || dy == height;
                if (border) {
                    block.setType(frame, false);
                } else {
                    block.setType(Material.AIR, false);
                }
            }
        }
        PortalShape shape = new PortalShape(PortalShape.Axis.X, baseX, baseY, z, baseX + width - 1, baseY + height - 1, z);
        build(world, shape, source.definition(), source.dimension(), "exit-" + UUID.randomUUID(), source.stored().id());
        suppressed.add(player.getUniqueId());
    }

    // --- ambience -------------------------------------------------------------------------

    void emitAmbience() {
        for (BuiltPortal portal : byId.values()) {
            String particleId = portal.definition().ambientParticle();
            if (particleId == null) {
                continue;
            }
            World world = plugin.getServer().getWorld(portal.stored().world());
            if (world == null || !world.isChunkLoaded(portal.shape().minX() >> 4, portal.shape().minZ() >> 4)) {
                continue;
            }
            Particle particle = Registry.PARTICLE_TYPE.get(NamespacedKey.fromString(particleId));
            if (particle == null || particle.getDataType() != Void.class) {
                continue;
            }
            Location center = new Location(world, portal.shape().centerX(),
                portal.shape().minY() + portal.shape().height() / 2.0, portal.shape().centerZ());
            if (world.getNearbyPlayers(center, 32).isEmpty()) {
                continue;
            }
            world.spawnParticle(particle, center, 6, portal.shape().width() / 3.0, portal.shape().height() / 3.0, 0.2, 0.01);
        }
    }

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }
}
