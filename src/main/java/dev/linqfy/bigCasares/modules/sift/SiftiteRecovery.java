package dev.linqfy.bigCasares.modules.sift;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import dev.linqfy.bigCasares.content.ContentItemFactory;
import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Siftite items are not lost on death: the mod's {@code SiftiteDeathMixin} and {@code SiftiteRecoveryManager}
 * take every stack of the {@code the_sift:siftite_items} tag (ore block item, nugget, ingot, upgrade
 * template and the ten pieces of gear) out of a dying player's drops, keep it for the player, and two seconds
 * after the next respawn open a small rift next to the player that sends the items back, one every
 * five ticks, as glowing items that fly to the player's chest and drop into the inventory (waiting in orbit
 * while there is no room or an earlier item has not arrived).
 *
 * <p>Here the death handler runs before the tombstone module (high priority, not highest), keeps the
 * Siftite drops in {@link SiftiteRecoveryBook} (bytes from {@code ItemStack#serializeAsBytes}, one file per
 * player, see {@link SiftiteRecoveryFiles}) and empties their inventory slots; {@code PlayerPostRespawnEvent}
 * and {@code PlayerJoinEvent} schedule the recovery 40 ticks ahead exactly like the mod's {@code AFTER_RESPAWN}
 * and {@code JOIN}; a one-tick task starts due recoveries and advances them. The rift is shown with the
 * timing of {@link SiftiteRift} (12 ticks to open, one item per 5 ticks, 12 ticks to close) and the items
 * move with {@link SiftiteReturnFlight}, both drawn with particles.</p>
 *
 * <p>Differences: the mini rift and the returning items are not entities. The rift is a cube of end-rod
 * particles with some reverse-portal sparks, and an item is an end-rod trail that flies, waits and
 * arrives exactly as in the mod but is not itself visible; a resonance sound opens the rift and a pickup
 * chime marks each arrival (the mod is silent). Items stay in the player's file until they are in the
 * inventory, so a restart, a disconnect, a death or a dimension change during a recovery interrupts it
 * without losing anything and a fresh recovery starts at the next respawn or join (a dimension change
 * restarts it 40 ticks later); in the mod the items live in entities that wait where they are, even in
 * another dimension. The stacks are chosen from the death drops and the slots are emptied (see
 * {@link SiftiteKeep}), the tombstone module and other plugins that read the inventory see none of them.
 * A stack that cannot be read back is skipped with a warning and stays in the file. The
 * {@code the_sift/keep_inventory} advancement is not awarded here: {@link #recovered} marks where it goes.</p>
 */
final class SiftiteRecovery implements Listener {

    private static final String PREFIX = "[TheSift] ";

    private final JavaPlugin plugin;
    private final SiftiteRecoveryBook book;
    private final Map<UUID, Session> sessions = new LinkedHashMap<>();

    SiftiteRecovery(JavaPlugin plugin, SiftiteRecoveryBook book) {
        this.plugin = plugin;
        this.book = book;
    }

    /** Registers the listener and the tick through {@code registrations}; released with the module's scope. */
    static SiftiteRecovery install(JavaPlugin plugin, BukkitRuntimeRegistrations registrations, Path directory) {
        SiftiteRecoveryBook book = new SiftiteRecoveryBook(new SiftiteRecoveryFiles(directory, plugin.getLogger()));
        SiftiteRecovery recovery = new SiftiteRecovery(plugin, book);
        registrations.registerListener("siftite-recovery-listener", recovery);
        registrations.scheduleRepeating("siftite-recovery-tick", recovery::tick, 1L, 1L);
        registrations.ownCleanup("siftite-recovery-cleanup", recovery::shutdown);
        // Players already online (plugin reload): same as the mod's JOIN hook.
        for (Player player : Bukkit.getOnlinePlayers()) {
            recovery.scheduleOnJoin(player);
        }
        return recovery;
    }

    static boolean isSiftite(ItemStack stack) {
        return ContentItemFactory.idOf(stack).map(NamespacedId::asString).filter(SiftiteKeep::isProtected).isPresent();
    }

    // --- capture ---------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        List<ItemStack> drops = event.getDrops();
        List<ItemStack> kept = SiftiteKeep.select(drops, event.getKeepInventory(), SiftiteRecovery::isSiftite);
        if (kept.isEmpty()) {
            return;
        }
        Player player = event.getEntity();
        try {
            List<byte[]> entries = new ArrayList<>(kept.size());
            for (ItemStack stack : kept) {
                entries.add(stack.serializeAsBytes());
            }
            book.capture(player.getUniqueId(), entries);
        } catch (IOException | RuntimeException failure) {
            // Nothing is taken out of the drops: the items fall as in vanilla rather than being lost.
            plugin.getLogger().log(Level.SEVERE, PREFIX + "Could not keep the Siftite items of " + player.getName()
                + "; they drop normally", failure);
            return;
        }
        SiftiteKeep.remove(drops, SiftiteRecovery::isSiftite);
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isSiftite(contents[slot])) {
                inventory.setItem(slot, null);
            }
        }
        plugin.getLogger().info(PREFIX + "Kept " + kept.size() + " Siftite stack(s) of " + player.getName()
            + " for their next respawn");
    }

    // --- scheduling ------------------------------------------------------------------------------

    @EventHandler
    public void onRespawn(PlayerPostRespawnEvent event) {
        try {
            book.afterRespawn(event.getPlayer().getUniqueId(), Bukkit.getCurrentTick());
        } catch (IOException failure) {
            plugin.getLogger().log(Level.SEVERE, PREFIX + "Could not read the Siftite items of "
                + event.getPlayer().getName(), failure);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        scheduleOnJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        sessions.remove(id);
        book.forget(id);
    }

    private void scheduleOnJoin(Player player) {
        try {
            book.afterJoin(player.getUniqueId(), Bukkit.getCurrentTick());
        } catch (IOException failure) {
            plugin.getLogger().log(Level.SEVERE, PREFIX + "Could not read the Siftite items of " + player.getName(), failure);
        }
    }

    void shutdown() {
        sessions.clear();
        book.clearTransient();
    }

    // --- recovery --------------------------------------------------------------------------------

    private void tick() {
        long now = Bukkit.getCurrentTick();
        for (UUID id : book.ready(now, this::canStart)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                start(player);
            }
        }
        if (sessions.isEmpty()) {
            return;
        }
        for (Iterator<Session> iterator = sessions.values().iterator(); iterator.hasNext(); ) {
            Session session = iterator.next();
            try {
                if (!session.tick()) {
                    iterator.remove();
                }
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.SEVERE, PREFIX + "Siftite recovery failed; it restarts at the next join", failure);
                iterator.remove();
            }
        }
    }

    private boolean canStart(UUID id) {
        Player player = Bukkit.getPlayer(id);
        return player != null && player.isOnline() && !player.isDead() && !sessions.containsKey(id);
    }

    private void start(Player player) {
        UUID id = player.getUniqueId();
        Deque<byte[]> queue = new ArrayDeque<>();
        try {
            queue.addAll(book.pending(id));
        } catch (IOException failure) {
            plugin.getLogger().log(Level.SEVERE, PREFIX + "Could not read the Siftite items of " + player.getName(), failure);
        }
        book.started(id);
        if (queue.isEmpty()) {
            return;
        }
        Session session = new Session(player, queue);
        sessions.put(id, session);
        session.world.playSound(session.riftLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS,
            1.0F, 0.7F);
    }

    /** One player's recovery: the rift, the items still inside it and the items on their way. */
    private final class Session {
        private final UUID owner;
        private final World world;
        private final Deque<byte[]> queue;
        private final SiftiteRift rift;
        private final List<Flight> flights = new ArrayList<>();
        private final double riftX;
        private final double riftY;
        private final double riftZ;
        private int released;

        private Session(Player player, Deque<byte[]> queue) {
            this.owner = player.getUniqueId();
            this.world = player.getWorld();
            this.queue = queue;
            this.rift = new SiftiteRift(queue.size());
            Location origin = player.getLocation();
            double[] offset = SiftiteRift.fallback();
            for (int index = 0; index < SiftiteRift.CANDIDATES; index++) {
                double[] candidate = SiftiteRift.candidate(index);
                Location spot = origin.clone().add(candidate[0], candidate[1], candidate[2]);
                if (spot.getBlock().isEmpty() && spot.clone().add(0, 1, 0).getBlock().isEmpty()) {
                    offset = candidate;
                    break;
                }
            }
            this.riftX = origin.getX() + offset[0];
            this.riftY = origin.getY() + offset[1];
            this.riftZ = origin.getZ() + offset[2];
        }

        private Location riftLocation() {
            return new Location(world, riftX, riftY, riftZ);
        }

        /** Returns false when the recovery is over or interrupted. */
        private boolean tick() {
            Player player = Bukkit.getPlayer(owner);
            if (player == null || !player.isOnline() || player.isDead()) {
                return false;
            }
            if (!player.getWorld().equals(world)) {
                book.reschedule(owner, Bukkit.getCurrentTick());
                return false;
            }
            boolean release = rift.tick();
            drawRift();
            if (release) {
                release();
            }
            double height = player.getHeight() * 0.62;
            Location where = player.getLocation();
            double targetX = where.getX();
            double targetY = where.getY() + height;
            double targetZ = where.getZ();
            // Release order is delivery order: only the first flight may arrive, the others wait in orbit.
            for (int index = 0; index < flights.size(); ) {
                if (flights.get(index).fly(player, targetX, targetY, targetZ, index > 0)) {
                    flights.remove(index);
                } else {
                    index++;
                }
            }
            return !(queue.isEmpty() && rift.isFinished() && flights.isEmpty());
        }

        private void release() {
            byte[] entry = queue.poll();
            if (entry == null) {
                return;
            }
            ItemStack stack;
            try {
                stack = ItemStack.deserializeBytes(entry);
            } catch (RuntimeException unreadable) {
                plugin.getLogger().log(Level.WARNING, PREFIX + "A kept Siftite item cannot be read back; it stays in the "
                    + "player's recovery file", unreadable);
                return;
            }
            flights.add(new Flight(world, entry, stack, new SiftiteReturnFlight(riftX, riftY, riftZ, released++)));
            world.playSound(riftLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.6F,
                1.2F + 0.1F * Math.min(released, 6));
        }

        /** The rift: the corners of the mod's cube, scaled like its renderer, plus a few sparks. */
        private void drawRift() {
            float size = rift.size();
            if (size <= 0.02F) {
                return;
            }
            double half = SiftiteRift.HALF_SIZE * size;
            for (int corner = 0; corner < 8; corner++) {
                double dx = (corner & 1) == 0 ? -half : half;
                double dy = (corner & 2) == 0 ? -half : half;
                double dz = (corner & 4) == 0 ? -half : half;
                world.spawnParticle(Particle.END_ROD, riftX + dx, riftY + dy, riftZ + dz, 1, 0.0, 0.0, 0.0, 0.0);
            }
            if (rift.age() % 3 == 0) {
                world.spawnParticle(Particle.REVERSE_PORTAL, riftX, riftY, riftZ, 4, half, half, half, 0.02);
            }
        }
    }

    /** An item on its way to the player. */
    private final class Flight {
        private byte[] entry;
        private ItemStack stack;
        private final SiftiteReturnFlight motion;
        private final World world;

        private Flight(World world, byte[] entry, ItemStack stack, SiftiteReturnFlight motion) {
            this.world = world;
            this.entry = entry;
            this.stack = stack;
            this.motion = motion;
        }

        /** One tick of {@code SiftiteReturnEntity.tick}; true once the whole stack is in the inventory. */
        private boolean fly(Player owner, double targetX, double targetY, double targetZ, boolean waitingForEarlier) {
            motion.beginTick();
            if (!waitingForEarlier && motion.arrived(targetX, targetY, targetZ) && deliver(owner)) {
                return true;
            }
            boolean waiting = waitingForEarlier || !canFitAny(owner, stack);
            motion.move(targetX, targetY, targetZ, waiting);
            if (motion.leavesTrail()) {
                world.spawnParticle(Particle.END_ROD, motion.x(), motion.y(), motion.z(), 1, 0.03, 0.03, 0.03, 0.005);
            }
            return false;
        }

        /** Puts what fits in the inventory; true when nothing is left. */
        private boolean deliver(Player owner) {
            int before = stack.getAmount();
            Map<Integer, ItemStack> leftover = owner.getInventory().addItem(stack.clone());
            ItemStack rest = null;
            for (ItemStack part : leftover.values()) {
                if (rest == null) {
                    rest = part.clone();
                } else {
                    rest.setAmount(rest.getAmount() + part.getAmount());
                }
            }
            int after = rest == null ? 0 : rest.getAmount();
            if (after < before) {
                recovered(owner);
                owner.playSound(owner.getLocation(), Sound.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.3F, 1.5F);
            }
            try {
                if (rest == null) {
                    book.delivered(owner.getUniqueId(), entry);
                } else if (after < before) {
                    byte[] updated = rest.serializeAsBytes();
                    book.partlyDelivered(owner.getUniqueId(), entry, updated);
                    entry = updated;
                    stack = rest;
                }
            } catch (IOException | RuntimeException failure) {
                plugin.getLogger().log(Level.SEVERE, PREFIX + "Could not update the Siftite recovery file of "
                    + owner.getName() + "; a delivered item may come back after a restart", failure);
            }
            return rest == null;
        }
    }

    /** Whether any inventory slot is empty or holds a stack {@code incoming} still fits on. */
    private static boolean canFitAny(Player owner, ItemStack incoming) {
        for (ItemStack existing : owner.getInventory().getStorageContents()) {
            if (existing == null || existing.getType().isAir()) {
                return true;
            }
            if (existing.isSimilar(incoming) && existing.getAmount() < existing.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    /** The place of the mod's {@code ModAdvancements.award(owner, "the_sift/keep_inventory")}. */
    private void recovered(Player owner) {
        // keep_inventory advancement: awarded when any part of a kept item first reaches the inventory.
    }
}
