package dev.linqfy.bigCasares.modules.alexscaves.core.block.remap;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Fixes the mining prediction of remapped blocks: while a Java player digs a block
 * the client sees as another one (leaves instead of a note block...), only that player's client gets its
 * {@code block_break_speed} scaled by {@link MiningFactor}, as an outgoing-packet modifier ({@link MiningFactors}
 * read by the packetevents listener); the server's attributes are never touched. The scaling ends with the dig
 * (abort, break, target change, quit...) by sending the real attribute again.
 */
final class MiningPredictionSync implements Listener {
    /** The packetevents side. */
    interface Sender {
        /** Whether the player is a Java client on the server's protocol (the only ones whose blocks are remapped). */
        boolean applicable(Player player);

        /** Sends the player's real {@code block_break_speed}; the listener adds the active factor to it. */
        void refresh(Player player);
    }

    private static final double MAX_DISTANCE_SQUARED = 10.0 * 10.0;

    private final Plugin plugin;
    private final Function<Block, BlockData> clientState;
    private final MiningFactors factors;
    private final Sender sender;
    /** One borrowed state per material: what the client may instabreak. */
    private final Supplier<List<BlockData>> borrowedSamples;
    private static final double ARM_REACH = 6.0;
    /** Server thread only. */
    private final Map<UUID, Dig> digs = new HashMap<>();

    /** {@code armed}: sent ahead of time for the block the player looks at (no dig started yet). */
    private record Dig(Block block, BlockData serverState, BlockData clientState, boolean armed) {
    }

    MiningPredictionSync(Plugin plugin, Function<Block, BlockData> clientState, MiningFactors factors, Sender sender,
                         Supplier<List<BlockData>> borrowedSamples) {
        this.borrowedSamples = borrowedSamples;
        this.plugin = plugin;
        this.clientState = clientState;
        this.factors = factors;
        this.sender = sender;
    }

    /** A cancelled damage event: the server will not dig it, so nothing may be scaled. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamageCancelled(BlockDamageEvent event) {
        if (event.isCancelled()) {
            end(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        BlockData client = event.getInstaBreak() ? null : clientState.apply(block);
        if (client == null || player.getGameMode() == GameMode.CREATIVE || !sender.applicable(player)) {
            end(player);
            return;
        }
        digs.put(player.getUniqueId(), new Dig(block, block.getBlockData(), client, false));
        update(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAbort(BlockDamageAbortEvent event) {
        end(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        end(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHeld(PlayerItemHeldEvent event) {
        recomputeLater(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        recomputeLater(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        end(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent event) {
        end(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        end(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        digs.remove(id);
        factors.remove(id);
    }

    /** Once a second: ends digs whose block changed or whose player left or walked away without an abort. */
    void tick() {
        for (Iterator<Map.Entry<UUID, Dig>> it = digs.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Dig> entry = it.next();
            Player player = plugin.getServer().getPlayer(entry.getKey());
            Dig dig = entry.getValue();
            Location at = player == null ? null : player.getLocation();
            boolean stale = player == null || !player.isOnline() || at.getWorld() != dig.block().getWorld()
                || at.distanceSquared(dig.block().getLocation()) > MAX_DISTANCE_SQUARED
                || !dig.block().getBlockData().equals(dig.serverState());
            if (stale) {
                it.remove();
                factors.remove(entry.getKey());
                if (player != null && player.isOnline()) {
                    refresh(player);
                }
            }
        }
    }

    void shutdown() {
        List<UUID> ids = List.copyOf(digs.keySet());
        digs.clear();
        factors.clear();
        for (UUID id : ids) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null && player.isOnline()) {
                refresh(player);
            }
        }
    }

    private void refresh(Player player) {
        try {
            sender.refresh(player);
        } catch (RuntimeException failure) {
            plugin.getLogger().log(java.util.logging.Level.FINE, "[AlexsCaves] Predicción de minado: no se pudo restaurar", failure);
        }
    }

    /**
     * Every few ticks, only for players whose held item could make the client break a borrowed block at once
     * (progress >= 1 on tick 0, before any dig event can fix the speed): the block they look at is armed ahead of
     * time when the server would not break it that fast. Everyone else costs one item check.
     */
    void scan() {
        List<BlockData> samples = borrowedSamples.get();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            Dig current = digs.get(id);
            if (current != null && !current.armed()) {
                continue;
            }
            Block target = null;
            if (player.getGameMode() != GameMode.CREATIVE && couldInstabreak(player, samples)) {
                target = player.getTargetBlockExact((int) ARM_REACH);
            }
            BlockData client = target == null ? null : clientState.apply(target);
            if (client == null || !instabreaks(client, player.getInventory().getItemInMainHand())) {
                if (current != null) {
                    end(player);
                }
                continue;
            }
            if (current != null && current.block().equals(target) && current.serverState().equals(target.getBlockData())) {
                continue;
            }
            if (!sender.applicable(player)) {
                continue;
            }
            digs.put(id, new Dig(target, target.getBlockData(), client, true));
            update(player);
            if (factors.get(id) == null) {
                digs.remove(id);
            }
        }
    }

    private boolean couldInstabreak(Player player, List<BlockData> samples) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.getType().isAir()) {
            return false;
        }
        for (BlockData sample : samples) {
            if (instabreaks(sample, tool)) {
                return true;
            }
        }
        return false;
    }

    private static boolean instabreaks(BlockData borrowed, ItemStack tool) {
        return profile(borrowed, tool).progress() >= 1.0;
    }

    int active() {
        return digs.size();
    }

    private void recomputeLater(Player player) {
        if (!digs.containsKey(player.getUniqueId())) {
            return;
        }
        // The events fire before the new item is in hand.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && digs.containsKey(player.getUniqueId())) {
                update(player);
            }
        });
    }

    private void update(Player player) {
        Dig dig = digs.get(player.getUniqueId());
        if (dig == null) {
            return;
        }
        OptionalDouble factor = factorFor(player, dig);
        MiningFactors.Active before = factors.get(player.getUniqueId());
        if (factor.isEmpty()) {
            if (before != null) {
                factors.remove(player.getUniqueId());
                refresh(player);
            }
            return;
        }
        if (before != null && Math.abs(before.factor() - factor.getAsDouble()) < 1e-9) {
            return;
        }
        factors.put(player.getUniqueId(), new MiningFactors.Active(player.getEntityId(), factor.getAsDouble()));
        refresh(player);
    }

    private OptionalDouble factorFor(Player player, Dig dig) {
        try {
            double serverProgress = dig.block().getBreakSpeed(player);
            // Instant (or unbreakable) on the server: nothing to predict slower.
            if (!(serverProgress > 0) || serverProgress >= 1.0) {
                return OptionalDouble.empty();
            }
            ItemStack tool = player.getInventory().getItemInMainHand();
            return MiningFactor.factor(profile(dig.serverState(), tool), profile(dig.clientState(), tool));
        } catch (RuntimeException failure) {
            return OptionalDouble.empty();
        }
    }

    static MiningFactor.Profile profile(BlockData data, ItemStack tool) {
        boolean harvest = !data.requiresCorrectToolForDrops() || data.isPreferredTool(tool);
        return new MiningFactor.Profile(data.getDestroySpeed(tool, true), data.getMaterial().getHardness(), harvest);
    }

    private void end(Player player) {
        UUID id = player.getUniqueId();
        boolean had = digs.remove(id) != null;
        boolean applied = factors.remove(id) != null;
        if (had && applied && player.isOnline()) {
            refresh(player);
        }
    }
}
