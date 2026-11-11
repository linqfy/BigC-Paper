package dev.linqfy.bigCasares.modules.tombstone;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class TombstoneRuntime {
    public static final int VISIBLE_SIZE = 54;

    private final BigCasares plugin;
    private final TombstoneStorage storage;
    private final Clock clock;
    private final Duration lifetime;
    private final NamespacedKey tombstoneIdKey;
    private final TombstoneLedger ledger = new TombstoneLedger();
    private final Map<UUID, ActiveTombstone> active = new LinkedHashMap<>();
    private boolean closed;
    private boolean loaded;

    public TombstoneRuntime(
        BigCasares plugin,
        TombstoneStorage storage,
        Clock clock,
        Duration lifetime,
        BukkitRuntimeRegistrations registrations
    ) {
        this.plugin = plugin;
        this.storage = storage;
        this.clock = clock;
        this.lifetime = lifetime;
        this.tombstoneIdKey = new NamespacedKey(plugin, "tombstone_id");
        registrations.scheduleRepeating("tombstone-heartbeat", this::heartbeat, 20L, 20L);
    }

    public void load() {
        removeOrphanVisuals();
        Instant now = clock.instant();
        ledger.restore(storage.loadAll(), now);
        loaded = true;
        for (TombstoneRecord record : ledger.all()) {
            try {
                activate(record);
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.WARNING,
                    "No se pudo mostrar la tumba " + record.id() + "; se reintentará.", failure);
            }
        }
        persist();
    }

    /**
     * Stores the items in a new tombstone. Returns false, keeping nothing, when the tombstone could not be recorded;
     * the caller must then leave the items to vanilla so they are not lost. A tombstone whose visuals cannot be
     * spawned yet is still recorded and retried by the heartbeat.
     */
    public boolean create(UUID ownerId, String ownerName, Location requested, List<ItemStack> items) {
        if (closed || !loaded || !TombstoneItemLayout.hasItems(items)) return false;
        TombstoneRecord record = null;
        try {
            Location location = safeLocation(requested);
            record = new TombstoneRecord(
                UUID.randomUUID(), ownerId, ownerName, location.getWorld().getUID(),
                location.getX(), location.getY(), location.getZ(), location.getYaw(),
                clock.instant().plus(lifetime), items
            );
            ledger.put(record);
            persist();
        } catch (RuntimeException failure) {
            // Not kept in memory either: the items stay in the drops, and a second copy would come back later.
            if (record != null) ledger.remove(record.id());
            plugin.getLogger().log(Level.SEVERE,
                "No se pudo guardar la tumba de " + ownerName + "; sus objetos caen normalmente.", failure);
            return false;
        }
        try {
            activate(record);
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING,
                "No se pudo mostrar la tumba " + record.id() + "; se reintentará.", failure);
        }
        return true;
    }

    public boolean open(UUID tombstoneId, org.bukkit.entity.Player player) {
        if (closed || player.isDead()) return false;
        TombstoneRecord record = ledger.find(tombstoneId).orElse(null);
        if (record == null) return false;
        if (!record.expiresAt().isAfter(clock.instant())) {
            remove(tombstoneId, null);
            return false;
        }
        activate(record);
        ActiveTombstone tombstone = active.get(tombstoneId);
        if (tombstone == null) return false;
        player.openInventory(tombstone.inventory);
        return true;
    }

    /** Opens the tombstone behind a clicked visual; a visual whose tombstone no longer exists is discarded. */
    public boolean openFromEntity(Entity entity, org.bukkit.entity.Player player) {
        UUID id = tombstoneId(entity);
        if (id == null) return false;
        if (!closed && ledger.find(id).isEmpty()) {
            entity.remove();
            return false;
        }
        return open(id, player);
    }

    /**
     * Removes tombstone visuals that are not the current ones of a live tombstone, so a duplicate that survived in
     * a chunk (crash, older version, failed removal) disappears as soon as its chunk loads.
     */
    public void discardStrays(Collection<? extends Entity> entities) {
        if (closed) return;
        for (Entity entity : entities) {
            UUID id = tombstoneId(entity);
            if (id == null) continue;
            ActiveTombstone tombstone = active.get(id);
            if (tombstone == null || tombstone.visuals == null || !tombstone.visuals.owns(entity)) entity.remove();
        }
    }

    public boolean openNearby(org.bukkit.entity.Player player, double radius) {
        Location location = player.getLocation();
        World world = location.getWorld();
        if (world == null) return false;
        List<TombstoneRecord> nearby = TombstoneProximity.nearby(
            ledger.all(), world.getUID(), location.getX(), location.getY(), location.getZ(), radius
        );
        if (nearby.isEmpty()) return false;
        if (nearby.size() == 1) return open(nearby.getFirst().id(), player);
        openSelection(player, nearby, location);
        return true;
    }

    private void openSelection(org.bukkit.entity.Player player, List<TombstoneRecord> nearby, Location playerLocation) {
        List<TombstoneRecord> visible = nearby.stream().limit(VISIBLE_SIZE).toList();
        int size = Math.max(9, ((visible.size() + 8) / 9) * 9);
        Inventory selection = Bukkit.createInventory(
            new TombstoneSelectionHolder(visible.stream().map(TombstoneRecord::id).toList()),
            size,
            "Tumbas cercanas"
        );
        Instant now = clock.instant();
        for (int slot = 0; slot < visible.size(); slot++) {
            TombstoneRecord record = visible.get(slot);
            ItemStack icon = new ItemStack(Material.CHEST);
            var meta = icon.getItemMeta();
            double dx = record.x() - playerLocation.getX();
            double dy = record.y() - playerLocation.getY();
            double dz = record.z() - playerLocation.getZ();
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            long seconds = Math.max(0L, Duration.between(now, record.expiresAt()).toSeconds());
            int itemCount = record.items().stream().filter(java.util.Objects::nonNull)
                .mapToInt(ItemStack::getAmount).sum();
            meta.displayName(Component.text("Tumba de " + record.ownerName()));
            meta.lore(List.of(
                Component.text(String.format(java.util.Locale.ROOT, "Distancia: %.1f bloques", distance)),
                Component.text("Objetos: " + itemCount),
                Component.text("Desaparece en " + (seconds / 60) + ":"
                    + String.format(java.util.Locale.ROOT, "%02d", seconds % 60))
            ));
            icon.setItemMeta(meta);
            selection.setItem(slot, icon);
        }
        player.openInventory(selection);
    }

    /**
     * Saves what is left in the tombstone. {@code closing} is the player whose inventory is closing right now: when
     * the tombstone is emptied every other viewer is closed out, but that player is not closed a second time.
     */
    public void reconcile(UUID tombstoneId, HumanEntity closing) {
        if (closed) return;
        ActiveTombstone tombstone = active.get(tombstoneId);
        if (tombstone == null) return;
        refill(tombstone);
        if (!TombstoneItemLayout.hasItems(contents(tombstone))) {
            remove(tombstoneId, closing);
            return;
        }
        syncRecord(tombstone);
        persist();
    }

    public void shutdown() {
        if (closed) return;
        closed = true;
        List<ActiveTombstone> tombstones = List.copyOf(active.values());
        try {
            // Nobody may keep taking items from an inventory that is about to be saved and forgotten: after a
            // reload the new runtime would restore the same items again.
            tombstones.forEach(tombstone -> closeViewers(tombstone.inventory, null));
            tombstones.forEach(this::syncRecord);
            // Without a completed load the ledger is not the file's content; saving it would erase the file.
            if (loaded) persist();
        } finally {
            tombstones.forEach(ActiveTombstone::removeVisuals);
            active.clear();
        }
    }

    private void activate(TombstoneRecord record) {
        if (closed) return;
        ActiveTombstone tombstone = active.get(record.id());
        if (tombstone == null) {
            if (Bukkit.getWorld(record.worldId()) == null) return;
            Inventory inventory = Bukkit.createInventory(
                new TombstoneInventoryHolder(record.id()), VISIBLE_SIZE, "Tumba de " + record.ownerName()
            );
            List<ItemStack> source = new ArrayList<>(record.items());
            for (int slot = 0; slot < VISIBLE_SIZE && !source.isEmpty(); slot++) {
                inventory.setItem(slot, source.removeFirst());
            }
            tombstone = new ActiveTombstone(record, inventory, source);
            active.put(record.id(), tombstone);
        }
        ensureVisuals(tombstone);
        updateText(tombstone, clock.instant());
    }

    /**
     * Spawns the visuals only while their chunk is loaded. They are not persistent, so the server discards them
     * when the chunk unloads, and they come back here when it loads again. Spawning into an unloaded chunk would
     * leave entities that report themselves invalid without being gone, and each retry would stack another set.
     */
    private void ensureVisuals(ActiveTombstone tombstone) {
        TombstoneRecord record = tombstone.record;
        World world = Bukkit.getWorld(record.worldId());
        if (world == null) {
            tombstone.discardVisuals();
            return;
        }
        int chunkX = Location.locToBlock(record.x()) >> 4;
        int chunkZ = Location.locToBlock(record.z()) >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) return;
        if (tombstone.visuals != null && tombstone.visuals.valid()) return;
        tombstone.discardVisuals();
        tombstone.visuals = spawnVisuals(record, record.location(world));
    }

    private TombstoneVisuals spawnVisuals(TombstoneRecord record, Location location) {
        World world = location.getWorld();
        if (world == null) throw new IllegalStateException("Tombstone world became unavailable");
        List<Entity> spawned = new ArrayList<>(3);
        try {
            BlockDisplay block = world.spawn(location.clone().subtract(0.5, 0.0, 0.5), BlockDisplay.class, display -> {
                display.setPersistent(false);
                display.setBlock(Bukkit.createBlockData(Material.CHEST));
                mark(display, record.id());
            });
            spawned.add(block);
            Interaction interaction = world.spawn(location.clone().add(0, 0.5, 0), Interaction.class, entity -> {
                entity.setPersistent(false);
                entity.setInteractionWidth(1.2f);
                entity.setInteractionHeight(1.5f);
                entity.setResponsive(true);
                mark(entity, record.id());
            });
            spawned.add(interaction);
            TextDisplay text = world.spawn(location.clone().add(0, 1.7, 0), TextDisplay.class, display -> {
                display.setPersistent(false);
                display.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                display.setAlignment(TextDisplay.TextAlignment.CENTER);
                mark(display, record.id());
            });
            spawned.add(text);
            return new TombstoneVisuals(block, interaction, text);
        } catch (RuntimeException failure) {
            spawned.forEach(Entity::remove);
            throw failure;
        }
    }

    private void heartbeat() {
        if (closed) return;
        Instant now = clock.instant();
        for (TombstoneRecord record : ledger.all()) {
            try {
                if (!record.expiresAt().isAfter(now)) {
                    remove(record.id(), null);
                    continue;
                }
                activate(record);
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.WARNING,
                    "No se pudo actualizar la tumba " + record.id() + "; se reintentará.", failure);
            }
        }
    }

    private void refill(ActiveTombstone tombstone) {
        for (int slot = 0; slot < tombstone.inventory.getSize() && !tombstone.overflow.isEmpty(); slot++) {
            if (tombstone.inventory.getItem(slot) == null) tombstone.inventory.setItem(slot, tombstone.overflow.removeFirst());
        }
    }

    private List<ItemStack> contents(ActiveTombstone tombstone) {
        List<ItemStack> items = new ArrayList<>();
        Arrays.stream(tombstone.inventory.getContents())
            .map(TombstoneItemLayout::cloneOrNull).forEach(items::add);
        tombstone.overflow.stream().map(TombstoneItemLayout::cloneOrNull).forEach(items::add);
        return items;
    }

    private void persist() {
        storage.saveAll(ledger.all());
    }

    private void syncRecord(ActiveTombstone tombstone) {
        TombstoneRecord updated = new TombstoneRecord(
            tombstone.record.id(), tombstone.record.ownerId(), tombstone.record.ownerName(),
            tombstone.record.worldId(), tombstone.record.x(), tombstone.record.y(), tombstone.record.z(),
            tombstone.record.yaw(), tombstone.record.expiresAt(), contents(tombstone)
        );
        tombstone.record = updated;
        ledger.put(updated);
    }

    private void remove(UUID id, HumanEntity except) {
        ActiveTombstone removed = active.remove(id);
        ledger.remove(id);
        try {
            persist();
        } finally {
            if (removed != null) {
                removed.removeVisuals();
                // Removed from the map first, so the close events this triggers find nothing to reconcile.
                closeViewers(removed.inventory, except);
            }
        }
    }

    private static void closeViewers(Inventory inventory, HumanEntity except) {
        for (HumanEntity viewer : List.copyOf(inventory.getViewers())) {
            if (viewer != except) viewer.closeInventory();
        }
    }

    private void updateText(ActiveTombstone tombstone, Instant now) {
        if (tombstone.visuals == null || !tombstone.visuals.text.isValid()) return;
        long seconds = Math.max(0L, Duration.between(now, tombstone.record.expiresAt()).toSeconds());
        tombstone.visuals.text.text(Component.text("Tumba de " + tombstone.record.ownerName() + "\nDesaparece en "
            + (seconds / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds % 60)));
    }

    private Location safeLocation(Location requested) {
        World world = requested.getWorld();
        if (world == null) throw new IllegalArgumentException("Death location must have a world");
        int x = requested.getBlockX();
        int z = requested.getBlockZ();
        int top = world.getMaxHeight() - 2;
        int y = Math.min(requested.getBlockY(), top);
        if (y < world.getMinHeight() + 1) {
            // Fell into the void: put it on the column's surface if there is one, otherwise at the world spawn.
            org.bukkit.block.Block surface = world.getHighestBlockAt(x, z);
            if (surface.getType().isAir() || surface.getY() + 1 > top) {
                return world.getSpawnLocation().toCenterLocation().subtract(0.0, 0.5, 0.0);
            }
            y = surface.getY() + 1;
        }
        // Walk up a little to free space (suffocation, sand) instead of jumping to the column top, which in the
        // Nether is the bedrock roof.
        for (int candidate = y; candidate <= Math.min(top, y + 16); candidate++) {
            if (open(world, x, candidate, z)) return centered(world, x, candidate, z);
        }
        int surface = world.getHighestBlockYAt(x, z) + 1;
        if (surface >= world.getMinHeight() + 1 && surface <= top) return centered(world, x, surface, z);
        return centered(world, x, y, z);
    }

    private static boolean open(World world, int x, int y, int z) {
        return world.getBlockAt(x, y, z).isPassable() && world.getBlockAt(x, y + 1, z).isPassable();
    }

    private static Location centered(World world, int x, int y, int z) {
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    private void mark(Entity entity, UUID id) {
        entity.getPersistentDataContainer().set(tombstoneIdKey, PersistentDataType.STRING, id.toString());
    }

    public UUID tombstoneId(Entity entity) {
        String raw = entity.getPersistentDataContainer().get(tombstoneIdKey, PersistentDataType.STRING);
        if (raw == null) return null;
        try { return UUID.fromString(raw); } catch (IllegalArgumentException ignored) { return null; }
    }

    private void removeOrphanVisuals() {
        for (World world : Bukkit.getWorlds()) {
            world.getEntities().stream().filter(entity -> tombstoneId(entity) != null).forEach(Entity::remove);
        }
    }

    private static final class ActiveTombstone {
        private TombstoneRecord record;
        private final Inventory inventory;
        private final List<ItemStack> overflow;
        private TombstoneVisuals visuals;

        private ActiveTombstone(TombstoneRecord record, Inventory inventory, List<ItemStack> overflow) {
            this.record = record;
            this.inventory = inventory;
            this.overflow = overflow;
        }

        private void discardVisuals() {
            if (visuals != null) visuals.remove();
            visuals = null;
        }

        private void removeVisuals() {
            discardVisuals();
        }
    }

    private record TombstoneVisuals(BlockDisplay block, Interaction interaction, TextDisplay text) {
        private boolean valid() {
            return block.isValid() && interaction.isValid() && text.isValid();
        }

        private boolean owns(Entity entity) {
            UUID id = entity.getUniqueId();
            return id.equals(block.getUniqueId()) || id.equals(interaction.getUniqueId())
                || id.equals(text.getUniqueId());
        }

        // Unconditional: isValid() is also false while the chunk is not loaded, and such an entity still exists.
        private void remove() {
            block.remove();
            interaction.remove();
            text.remove();
        }
    }
}
