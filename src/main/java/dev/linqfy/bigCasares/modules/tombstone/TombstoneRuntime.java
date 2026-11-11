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
        ledger.all().forEach(this::activate);
        persist();
    }

    public void create(UUID ownerId, String ownerName, Location requested, List<ItemStack> items) {
        if (!TombstoneItemLayout.hasItems(items)) return;
        Location location = safeLocation(requested);
        TombstoneRecord record = new TombstoneRecord(
            UUID.randomUUID(), ownerId, ownerName, location.getWorld().getUID(),
            location.getX(), location.getY(), location.getZ(), location.getYaw(),
            clock.instant().plus(lifetime), items
        );
        ledger.put(record);
        activate(record);
        persist();
    }

    /** Keeps an audit trail of what each death stored, for item-loss support tickets. */
    public void logDeathInventory(String playerName, List<ItemStack> items) {
        StringBuilder summary = new StringBuilder();
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                if (!summary.isEmpty()) summary.append(", ");
                summary.append(item.getAmount()).append('x').append(item.getType().name());
            }
        }
        if (!summary.isEmpty()) {
            plugin.getLogger().info("Tumba de " + playerName + ": " + summary);
        }
    }

    public boolean open(UUID tombstoneId, org.bukkit.entity.Player player) {
        ledger.find(tombstoneId).ifPresent(this::activate);
        ActiveTombstone tombstone = active.get(tombstoneId);
        if (tombstone == null) return false;
        player.openInventory(tombstone.inventory);
        return true;
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

    public void reconcile(UUID tombstoneId) {
        ActiveTombstone tombstone = active.get(tombstoneId);
        if (tombstone == null) return;
        refill(tombstone);
        if (!TombstoneItemLayout.hasItems(contents(tombstone))) {
            remove(tombstoneId);
            return;
        }
        syncRecord(tombstone);
        persist();
    }

    public void shutdown() {
        // Close viewers first: an inventory left open after the runtime is gone would
        // still hand out items that were already persisted, duplicating them.
        active.values().forEach(ActiveTombstone::closeViewers);
        active.values().forEach(this::syncRecord);
        persist();
        active.values().forEach(ActiveTombstone::removeVisuals);
        active.clear();
    }

    private void activate(TombstoneRecord record) {
        if (active.containsKey(record.id())) return;
        World world = Bukkit.getWorld(record.worldId());
        if (world == null) return;
        Inventory inventory = Bukkit.createInventory(
            new TombstoneInventoryHolder(record.id()), VISIBLE_SIZE, "Tumba de " + record.ownerName()
        );
        List<ItemStack> source = new ArrayList<>(record.items());
        for (int slot = 0; slot < VISIBLE_SIZE && !source.isEmpty(); slot++) inventory.setItem(slot, source.removeFirst());
        Location location = record.location(world);
        TombstoneVisuals visuals = spawnVisuals(record, location);
        ActiveTombstone tombstone = new ActiveTombstone(record, inventory, source, visuals);
        active.put(record.id(), tombstone);
        updateText(tombstone, clock.instant());
    }

    private TombstoneVisuals spawnVisuals(TombstoneRecord record, Location location) {
        World world = location.getWorld();
        if (world == null) throw new IllegalStateException("Tombstone world became unavailable");
        BlockDisplay block = world.spawn(location.clone().subtract(0.5, 0.0, 0.5), BlockDisplay.class, display -> {
            display.setPersistent(false);
            display.setBlock(Bukkit.createBlockData(Material.CHEST));
            mark(display, record.id());
        });
        Interaction interaction = world.spawn(location.clone().add(0, 0.5, 0), Interaction.class, entity -> {
            entity.setPersistent(false);
            entity.setInteractionWidth(1.2f);
            entity.setInteractionHeight(1.5f);
            entity.setResponsive(true);
            mark(entity, record.id());
        });
        TextDisplay text = world.spawn(location.clone().add(0, 1.7, 0), TextDisplay.class, display -> {
            display.setPersistent(false);
            display.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            mark(display, record.id());
        });
        return new TombstoneVisuals(block, interaction, text);
    }

    private void heartbeat() {
        Instant now = clock.instant();
        for (TombstoneRecord record : ledger.all()) {
            try {
                if (!record.expiresAt().isAfter(now)) {
                    remove(record.id());
                    continue;
                }
                ActiveTombstone tombstone = active.get(record.id());
                if (tombstone == null) {
                    activate(record);
                    continue;
                }
                if (!tombstone.visuals.valid()) {
                    tombstone.visuals.remove();
                    tombstone.visuals = spawnVisuals(tombstone.record, tombstone.record.location(
                        Bukkit.getWorld(tombstone.record.worldId())));
                }
                updateText(tombstone, now);
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

    private void remove(UUID id) {
        ActiveTombstone removed = active.remove(id);
        if (removed != null) {
            removed.closeViewers();
            removed.removeVisuals();
        }
        ledger.remove(id);
        persist();
    }

    private void updateText(ActiveTombstone tombstone, Instant now) {
        long seconds = Math.max(0L, Duration.between(now, tombstone.record.expiresAt()).toSeconds());
        tombstone.visuals.text.text(Component.text("Tumba de " + tombstone.record.ownerName() + "\nDesaparece en "
            + (seconds / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds % 60)));
    }

    private Location safeLocation(Location requested) {
        World world = requested.getWorld();
        if (world == null) throw new IllegalArgumentException("Death location must have a world");
        if (requested.getY() < world.getMinHeight() + 1 || requested.getY() >= world.getMaxHeight() - 2) {
            return world.getSpawnLocation().clone().add(0.5, 0.0, 0.5);
        }
        Location centered = requested.getBlock().getLocation().add(0.5, 0.0, 0.5);
        if (centered.getBlock().isPassable() && centered.clone().add(0, 1, 0).getBlock().isPassable()) return centered;
        int y = world.getHighestBlockYAt(centered.getBlockX(), centered.getBlockZ()) + 1;
        return new Location(world, centered.getBlockX() + 0.5, y, centered.getBlockZ() + 0.5);
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

        private ActiveTombstone(TombstoneRecord record, Inventory inventory, List<ItemStack> overflow,
                                TombstoneVisuals visuals) {
            this.record = record;
            this.inventory = inventory;
            this.overflow = overflow;
            this.visuals = visuals;
        }

        private void removeVisuals() {
            visuals.remove();
        }

        private void closeViewers() {
            for (org.bukkit.entity.HumanEntity viewer : List.copyOf(inventory.getViewers())) {
                viewer.closeInventory();
            }
        }
    }

    private record TombstoneVisuals(BlockDisplay block, Interaction interaction, TextDisplay text) {
        private boolean valid() {
            return block.isValid() && interaction.isValid() && text.isValid();
        }

        private void remove() {
            if (block.isValid()) block.remove();
            if (interaction.isValid()) interaction.remove();
            if (text.isValid()) text.remove();
        }
    }
}
