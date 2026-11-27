package dev.linqfy.bigCasares.modules.alexscaves.abyssal;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Mod {@code AbyssalAltarBlock} and its block entity. A player right-clicks the altar with an item to leave one on it,
 * and right-clicks it again to take it back (it is handed over 7 ticks later). A Deep One that sees a pearl (or a heart
 * of the sea) on an altar goes to it, takes it and, after admiring it, leaves the barter loot there: the altar turns
 * active, and with it every abyssmarine brick, tile and pillar linked to it within 14 blocks
 * ({@code ActivatedByAltar}). Taking the loot from the altar turns it off again.
 *
 * <p>The mod's block entity is a chunk-saved record here (the item, the player who placed it and a pending hand-over)
 * with an item display on top. The altar's light (level 5 while active) is not reproduced.
 */
public final class AbyssalAltar implements Listener, Runnable, AutoCloseable {
    /** Mod {@code ActivatedByAltar.MAX_DISTANCE}: a block is lit when its distance to an active altar is below this. */
    static final int MAX_DISTANCE = 15;
    private static final int POP_DELAY = 6;
    private static final Map<Plugin, AbyssalAltar> INSTANCES = new WeakHashMap<>();

    private final ModMobService mobs;
    private final Plugin plugin;
    private final BlockData altarData;
    private final BlockData activeAltarData;
    private final Map<BlockData, BlockData> toActive;
    private final Map<BlockData, BlockData> toInactive = new HashMap<>();
    private final NamespacedKey chunkKey;
    private final Map<Key, Altar> altars = new LinkedHashMap<>();
    private long tick;

    public AbyssalAltar(ModMobService mobs, BlockData altarData, BlockData activeAltarData, Map<BlockData, BlockData> toActive) {
        this.mobs = mobs;
        this.plugin = mobs.plugin();
        this.altarData = altarData;
        this.activeAltarData = activeAltarData;
        this.toActive = Map.copyOf(toActive);
        toActive.forEach((inactive, active) -> toInactive.put(active, inactive));
        this.chunkKey = new NamespacedKey(plugin, "alexscaves_altars");
        synchronized (INSTANCES) {
            INSTANCES.put(plugin, this);
        }
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                load(chunk);
            }
        }
    }

    public static Optional<AbyssalAltar> get(Plugin plugin) {
        synchronized (INSTANCES) {
            return Optional.ofNullable(INSTANCES.get(plugin));
        }
    }

    record Key(UUID world, int x, int y, int z) {
        static Key of(Block block) {
            return new Key(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }

    /** The mod {@code AbyssalAltarBlockEntity}: one item slot and the hand-over of its last interaction. */
    public final class Altar {
        private final Key key;
        private ItemStack item = ItemStack.empty();
        private ItemStack popStack;
        private UUID placingPlayer;
        private UUID lastInteracter;
        private int popDelay;
        private long lastInteractionTime;
        private float angle;
        private ItemDisplay display;

        private Altar(Key key) {
            this.key = key;
        }

        public Block block() {
            World world = plugin.getServer().getWorld(key.world());
            return world == null ? null : world.getBlockAt(key.x(), key.y(), key.z());
        }

        public Location center() {
            Block block = block();
            return block == null ? null : block.getLocation().add(0.5, 0.5, 0.5);
        }

        public ItemStack item() {
            return item.clone();
        }

        public boolean isEmpty() {
            return item.isEmpty();
        }

        public void setItem(ItemStack stack) {
            item = stack == null ? ItemStack.empty() : stack.clone();
            refreshDisplay();
            save(key);
        }

        public long lastInteractionTime() {
            return lastInteractionTime;
        }

        public boolean active() {
            Block block = block();
            return block != null && block.getBlockData().equals(activeAltarData);
        }

        /** Mod {@code queueItemDrop}: false when a hand-over is already pending. */
        public boolean queueItemDrop(ItemStack copy) {
            if (popStack != null) {
                return false;
            }
            popStack = copy.clone();
            return true;
        }

        /**
         * Mod {@code onEntityInteract}: a flip turns an active altar off; a Deep One that does not flip turns it on.
         */
        public void onEntityInteract(LivingEntity entity, boolean flip) {
            Block block = block();
            if (block != null && isAltar(block)) {
                if (flip) {
                    if (active()) {
                        block.setBlockData(altarData, false);
                        propagate(block);
                    }
                } else if (mobs.typeOf(entity).filter(DeepOnes.ALL::contains).isPresent()) {
                    block.setBlockData(activeAltarData, false);
                    propagate(block);
                }
                Location center = center();
                angle = (float) Math.toDegrees(Math.atan2(entity.getX() - center.getX(), entity.getZ() - center.getZ()));
            }
            lastInteracter = entity.getUniqueId();
            popDelay = 0;
            if (entity instanceof Player) {
                placingPlayer = entity.getUniqueId();
            } else {
                lastInteractionTime = entity.getWorld().getFullTime();
            }
            refreshDisplay();
            save(key);
        }

        private void tickPop() {
            if (popStack == null || popDelay++ <= POP_DELAY) {
                return;
            }
            ItemStack drop = popStack;
            popStack = null;
            Entity interacter = lastInteracter == null ? null : plugin.getServer().getEntity(lastInteracter);
            lastInteracter = null;
            Location center = center();
            if (interacter instanceof Player player && player.isValid()) {
                // Mod: handed straight to the player; what does not fit drops by the altar.
                player.getInventory().addItem(drop).values().forEach(rest -> dropAt(rest, center));
                player.getWorld().playSound(player.getLocation(), "minecraft:entity.item.pickup", 0.2F, 1.6F);
            } else if (interacter instanceof LivingEntity living && mobs.behaviourOf(living).orElse(null) instanceof DeepOnes.Base deepOne) {
                deepOne.receiveBarterItem(living, drop, placingPlayer);
                placingPlayer = null;
            } else if (center != null) {
                dropAt(drop, center);
            }
            save(key);
        }

        private void dropAt(ItemStack stack, Location center) {
            if (center == null) {
                return;
            }
            double radians = Math.toRadians(angle);
            // Mod: the centre of the block plus half a block up, and one block toward the angle.
            Location at = center.clone().add(Math.sin(radians), 0.5, Math.cos(radians));
            at.getWorld().dropItem(at, stack);
        }

        private void refreshDisplay() {
            Location center = center();
            if (item.isEmpty() || center == null) {
                removeDisplay();
                return;
            }
            if (display == null || !display.isValid()) {
                display = center.getWorld().spawn(center.clone().add(0, 0.95, 0), ItemDisplay.class, spawned -> {
                    spawned.setPersistent(false);
                    spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
                    spawned.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(1.4F, 1.4F, 1.4F),
                        new AxisAngle4f()));
                });
            }
            display.setItemStack(item);
            display.setRotation(angle, 0);
        }

        private void removeDisplay() {
            if (display != null) {
                display.remove();
                display = null;
            }
        }
    }

    // --- lookup ---------------------------------------------------------------------------------------------

    public boolean isAltar(Block block) {
        BlockData data = block.getBlockData();
        return data.equals(altarData) || data.equals(activeAltarData);
    }

    public Optional<Altar> at(Block block) {
        return Optional.ofNullable(altars.get(Key.of(block)));
    }

    /** The altar record of a block, made when it does not have one yet. */
    public Altar ensure(Block block) {
        return recordOf(block);
    }

    private Altar recordOf(Block block) {
        return altars.computeIfAbsent(Key.of(block), Altar::new);
    }

    /** Altars within {@code range} blocks of a spot (mod: the altar points of interest within 64). */
    public List<Altar> near(Location location, double range) {
        List<Altar> found = new ArrayList<>();
        for (Altar altar : altars.values()) {
            Location center = altar.center();
            if (center != null && center.getWorld().equals(location.getWorld())
                && center.distanceSquared(location) <= range * range) {
                found.add(altar);
            }
        }
        return found;
    }

    // --- events ----------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null || event.getHand() != EquipmentSlot.HAND
            || event.getPlayer().isSneaking() || event.useItemInHand() == Event.Result.DENY || !isAltar(block)) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        Altar altar = recordOf(block);
        if (altar.isEmpty()) {
            if (held.getType().isAir()) {
                return;
            }
            event.setUseItemInHand(Event.Result.DENY);
            event.setCancelled(true);
            ItemStack copy = held.clone();
            copy.setAmount(1);
            altar.setItem(copy);
            altar.onEntityInteract(player, false);
            if (player.getGameMode() != GameMode.CREATIVE) {
                held.setAmount(held.getAmount() - 1);
            }
        } else {
            event.setUseItemInHand(Event.Result.DENY);
            event.setCancelled(true);
            if (altar.queueItemDrop(altar.item())) {
                altar.onEntityInteract(player, true);
                altar.setItem(null);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (toActive.containsKey(block.getBlockData()) || toInactive.containsKey(block.getBlockData())) {
            mobs.tasks().run(() -> {
                for (Altar altar : near(block.getLocation(), MAX_DISTANCE * 2)) {
                    Block altarBlock = altar.block();
                    if (altarBlock != null && altar.active()) {
                        propagate(altarBlock);
                    }
                }
            });
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        load(event.getChunk());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        for (Altar altar : List.copyOf(altars.values())) {
            if (altar.key.world().equals(chunk.getWorld().getUID()) && altar.key.x() >> 4 == chunk.getX()
                && altar.key.z() >> 4 == chunk.getZ()) {
                altar.removeDisplay();
                altars.remove(altar.key);
            }
        }
    }

    @Override
    public void run() {
        tick++;
        for (Altar altar : List.copyOf(altars.values())) {
            altar.tickPop();
        }
        if (tick % 10 != 0) {
            return;
        }
        for (Altar altar : List.copyOf(altars.values())) {
            Block block = altar.block();
            if (block == null || !block.getWorld().isChunkLoaded(altar.key.x() >> 4, altar.key.z() >> 4)) {
                continue;
            }
            if (!isAltar(block)) {
                // Broken or blown up: what it held falls out (mod {@code Containers.dropContents}).
                Location center = altar.center();
                if (!altar.item.isEmpty()) {
                    block.getWorld().dropItemNaturally(center, altar.item);
                }
                if (altar.popStack != null) {
                    block.getWorld().dropItemNaturally(center, altar.popStack);
                }
                altar.removeDisplay();
                altars.remove(altar.key);
                save(altar.key);
                propagate(block);
            } else if (altar.item.isEmpty() && altar.popStack == null) {
                if (altar.active()) {
                    // An active altar keeps lighting its linked blocks even with nothing on it.
                    altar.removeDisplay();
                    continue;
                }
                altar.removeDisplay();
                altars.remove(altar.key);
                save(altar.key);
            } else if (altar.display == null || !altar.display.isValid()) {
                altar.refreshDisplay();
            }
        }
    }

    @Override
    public void close() {
        for (Altar altar : List.copyOf(altars.values())) {
            save(altar.key);
            altar.removeDisplay();
        }
        altars.clear();
        synchronized (INSTANCES) {
            INSTANCES.remove(plugin, this);
        }
    }

    // --- activation of linked blocks (ActivatedByAltar) ----------------------------------------------------

    private boolean linked(BlockData data) {
        return toActive.containsKey(data) || toInactive.containsKey(data);
    }

    /**
     * Mod {@code ActivatedByAltar.updateDistance}: the linked blocks within 14 steps of an active altar (through other
     * linked blocks) are active; the rest are not. The mod spreads it one block a tick, so a layer changes each tick.
     */
    void propagate(Block origin) {
        World world = origin.getWorld();
        Map<Block, Integer> reach = flood(List.of(origin), null);
        List<Block> sources = new ArrayList<>();
        for (Altar altar : altars.values()) {
            Block block = altar.block();
            if (block != null && block.getWorld().equals(world) && block.getLocation().distanceSquared(origin.getLocation()) <= 900
                && block.getBlockData().equals(activeAltarData)) {
                sources.add(block);
            }
        }
        if (origin.getBlockData().equals(activeAltarData) && !sources.contains(origin)) {
            sources.add(origin);
        }
        Map<Block, Integer> lit = flood(sources, reach.keySet());
        Map<Integer, List<Block>> layers = new LinkedHashMap<>();
        reach.forEach((block, distance) -> layers.computeIfAbsent(distance, key -> new ArrayList<>()).add(block));
        layers.forEach((distance, blocks) -> {
            Runnable layer = () -> {
                int sounds = 0;
                for (Block block : blocks) {
                    if (!block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
                        continue;
                    }
                    BlockData data = block.getBlockData();
                    boolean want = lit.containsKey(block);
                    BlockData next = want ? toActive.get(data) : toInactive.get(data);
                    if (next != null) {
                        block.setBlockData(next, false);
                        if (sounds++ < 6) {
                            block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5),
                                want ? "alexscaves:abyssmarine_glow_on" : "alexscaves:abyssmarine_glow_off", 1.5F,
                                0.8F + ThreadLocalRandom.current().nextFloat() * 0.4F);
                        }
                    }
                }
            };
            // When the module stops first the layer changes at once, so no block is left half lit.
            mobs.tasks().later(distance, layer, layer);
        });
    }

    /** Breadth-first from the sources through linked blocks; the distance of the first ring is 1. */
    private Map<Block, Integer> flood(List<Block> sources, Set<Block> within) {
        Map<Block, Integer> found = new LinkedHashMap<>();
        Set<Block> seen = new HashSet<>(sources);
        ArrayDeque<Block> queue = new ArrayDeque<>(sources);
        Map<Block, Integer> depth = new HashMap<>();
        for (Block source : sources) {
            depth.put(source, 0);
        }
        while (!queue.isEmpty() && found.size() < 20000) {
            Block current = queue.poll();
            int distance = depth.get(current);
            if (distance + 1 >= MAX_DISTANCE) {
                continue;
            }
            for (BlockFace face : new BlockFace[] {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH,
                BlockFace.EAST, BlockFace.WEST}) {
                Block next = current.getRelative(face);
                if (seen.contains(next) || !next.getWorld().isChunkLoaded(next.getX() >> 4, next.getZ() >> 4)) {
                    continue;
                }
                seen.add(next);
                if (!linked(next.getBlockData())) {
                    continue;
                }
                depth.put(next, distance + 1);
                if (within == null || within.contains(next)) {
                    found.put(next, distance + 1);
                }
                queue.add(next);
            }
        }
        return found;
    }

    // --- persistence ------------------------------------------------------------------------------------------

    private void save(Key key) {
        World world = plugin.getServer().getWorld(key.world());
        if (world == null || !world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) {
            return;
        }
        Chunk chunk = world.getChunkAt(key.x() >> 4, key.z() >> 4);
        StringBuilder text = new StringBuilder();
        for (Altar altar : altars.values()) {
            if (altar.key.world().equals(key.world()) && altar.key.x() >> 4 == chunk.getX() && altar.key.z() >> 4 == chunk.getZ()
                && (!altar.item.isEmpty() || altar.popStack != null || altar.active())) {
                text.append(altar.key.x()).append(';').append(altar.key.y()).append(';').append(altar.key.z()).append(';')
                    .append(altar.placingPlayer == null ? "-" : altar.placingPlayer).append(';')
                    .append(encode(altar.item)).append(';').append(encode(altar.popStack)).append(';')
                    .append(altar.angle).append('\n');
            }
        }
        PersistentDataContainer data = chunk.getPersistentDataContainer();
        if (text.isEmpty()) {
            data.remove(chunkKey);
        } else {
            data.set(chunkKey, PersistentDataType.STRING, text.toString());
        }
    }

    private void load(Chunk chunk) {
        String text = chunk.getPersistentDataContainer().get(chunkKey, PersistentDataType.STRING);
        if (text == null) {
            return;
        }
        for (String line : text.split("\n")) {
            String[] parts = line.split(";");
            if (parts.length < 7) {
                continue;
            }
            try {
                Block block = chunk.getWorld().getBlockAt(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]));
                Altar altar = recordOf(block);
                altar.placingPlayer = "-".equals(parts[3]) ? null : UUID.fromString(parts[3]);
                altar.item = decode(parts[4]);
                altar.popStack = decode(parts[5]).isEmpty() ? null : decode(parts[5]);
                altar.angle = Float.parseFloat(parts[6]);
                altar.refreshDisplay();
            } catch (RuntimeException invalid) {
                plugin.getLogger().log(Level.WARNING, "[AlexsCaves] Altar abisal con datos inválidos en el chunk "
                    + chunk.getX() + "," + chunk.getZ(), invalid);
            }
        }
    }

    private static String encode(ItemStack stack) {
        return stack == null || stack.isEmpty() ? "-" : Base64.getEncoder().encodeToString(stack.serializeAsBytes());
    }

    private static ItemStack decode(String text) {
        return "-".equals(text) ? ItemStack.empty() : ItemStack.deserializeBytes(Base64.getDecoder().decode(text));
    }

    /** Mod tag {@code deep_one_barters}: pearls and hearts of the sea. */
    public boolean isBarterItem(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        Optional<String> id = mobs.itemId(stack);
        return id.map("ac_pearl"::equals).orElse(stack.getType() == Material.HEART_OF_THE_SEA);
    }
}
