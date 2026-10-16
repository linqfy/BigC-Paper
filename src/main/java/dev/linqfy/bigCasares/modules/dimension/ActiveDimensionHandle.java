package dev.linqfy.bigCasares.modules.dimension;

import dev.linqfy.bigCasares.dimension.DimensionDefinition;
import dev.linqfy.bigCasares.dimension.environment.EnvironmentCycle;
import dev.linqfy.bigCasares.dimension.event.DimensionPhaseChangeEvent;
import dev.linqfy.bigCasares.dimension.persistence.DimensionDataStore;
import dev.linqfy.bigCasares.dimension.runtime.ActiveDimension;
import dev.linqfy.bigCasares.dimension.runtime.DimensionExtension;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeCleanupReport;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runtime state of one active dimension: its scoped registrations, populators,
 * environment cycle tracking and extension dispatch.
 */
final class ActiveDimensionHandle implements ActiveDimension {

    /** Phase checks are O(1) arithmetic on the world clock; once per second is plenty. */
    static final long PHASE_CHECK_TICKS = 20L;

    private final JavaPlugin plugin;
    private final DimensionDefinition definition;
    private final World world;
    private final RuntimeRegistrationScope scope;
    private final BukkitRuntimeRegistrations registrations;
    private final DimensionDataStore store;
    private final Map<String, EnvironmentCycle.Snapshot> snapshots = new LinkedHashMap<>();
    private final List<BlockPopulator> installedPopulators = new ArrayList<>();
    private long pulseTicks;

    ActiveDimensionHandle(
        JavaPlugin plugin, DimensionDefinition definition, World world,
        RuntimeRegistrationScope parentScope, DimensionDataStore store
    ) {
        this.plugin = plugin;
        this.definition = definition;
        this.world = world;
        this.scope = new RuntimeRegistrationScope(parentScope.generation(), "dimension/" + definition.key());
        this.registrations = new BukkitRuntimeRegistrations(plugin, scope);
        this.store = store;
    }

    void start() {
        for (DimensionExtension extension : definition.extensions()) {
            for (BlockPopulator populator : extension.populators(this)) {
                world.getPopulators().add(populator);
                installedPopulators.add(populator);
            }
        }
        registrations.ownCleanup("populators", () -> world.getPopulators().removeAll(installedPopulators));
        for (EnvironmentCycle cycle : definition.cycles()) {
            EnvironmentCycle.Snapshot initial = cycle.snapshotAt(world.getFullTime());
            snapshots.put(cycle.id(), initial);
        }
        for (DimensionExtension extension : definition.extensions()) {
            safely("activate", () -> extension.onActivate(this));
        }
        for (EnvironmentCycle cycle : definition.cycles()) {
            firePhaseChange(cycle, null, snapshots.get(cycle.id()));
        }
        if (!definition.cycles().isEmpty()) {
            registrations.scheduleRepeating("environment-cycles", this::checkCycles, PHASE_CHECK_TICKS, PHASE_CHECK_TICKS);
        }
        long interval = pulseInterval();
        if (interval > 0) {
            registrations.scheduleRepeating("environment-pulse", () -> pulse(interval), interval, interval);
        }
    }

    /** Re-evaluates every cycle against the world clock; fires events on phase changes. */
    void checkCycles() {
        long time = world.getFullTime();
        for (EnvironmentCycle cycle : definition.cycles()) {
            EnvironmentCycle.Snapshot previous = snapshots.get(cycle.id());
            EnvironmentCycle.Snapshot current = cycle.snapshotAt(time);
            snapshots.put(cycle.id(), current);
            if (previous == null || previous.phaseIndex() != current.phaseIndex()
                || previous.cycleNumber() != current.cycleNumber() && cycle.phases().size() == 1) {
                firePhaseChange(cycle, previous, current);
            }
        }
    }

    private void firePhaseChange(EnvironmentCycle cycle, EnvironmentCycle.Snapshot from, EnvironmentCycle.Snapshot to) {
        plugin.getServer().getPluginManager().callEvent(
            new DimensionPhaseChangeEvent(definition.key(), world, cycle.id(), from, to));
        for (DimensionExtension extension : definition.extensions()) {
            safely("phase change", () -> extension.onPhaseChange(this, cycle, from, to));
        }
    }

    private long pulseInterval() {
        long interval = 0;
        for (DimensionExtension extension : definition.extensions()) {
            long value = Math.max(1L, extension.pulseIntervalTicks());
            interval = interval == 0 ? value : gcd(interval, value);
        }
        return interval;
    }

    private void pulse(long interval) {
        pulseTicks += interval;
        List<Player> players = world.getPlayers();
        if (players.isEmpty()) {
            return;
        }
        for (DimensionExtension extension : definition.extensions()) {
            if (pulseTicks % Math.max(1L, extension.pulseIntervalTicks()) == 0) {
                safely("pulse", () -> extension.onPulse(this, players));
            }
        }
    }

    void playerEntered(Player player) {
        for (DimensionExtension extension : definition.extensions()) {
            safely("player enter", () -> extension.onPlayerEnter(this, player));
        }
    }

    void playerExited(Player player) {
        for (DimensionExtension extension : definition.extensions()) {
            safely("player exit", () -> extension.onPlayerExit(this, player));
        }
    }

    RuntimeCleanupReport stop() {
        for (DimensionExtension extension : definition.extensions()) {
            safely("deactivate", () -> extension.onDeactivate(this));
        }
        return scope.close();
    }

    private void safely(String phase, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.SEVERE,
                "Dimension " + definition.key() + " extension failed during " + phase, failure);
        }
    }

    private static long gcd(long a, long b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    @Override
    public DimensionDefinition definition() {
        return definition;
    }

    @Override
    public World world() {
        return world;
    }

    @Override
    public BukkitRuntimeRegistrations registrations() {
        return registrations;
    }

    @Override
    public Optional<EnvironmentCycle.Snapshot> snapshot(String cycleId) {
        return Optional.ofNullable(snapshots.get(cycleId));
    }

    @Override
    public Collection<Player> players() {
        return List.copyOf(world.getPlayers());
    }

    @Override
    public DimensionValues values() {
        String dimension = definition.key().asString();
        return new DimensionValues() {
            @Override
            public Optional<String> get(String key) {
                return store.value(dimension, key);
            }

            @Override
            public void put(String key, String value) {
                store.putValue(dimension, key, value);
            }

            @Override
            public void remove(String key) {
                store.removeValue(dimension, key);
            }
        };
    }

    @Override
    public Logger logger() {
        return plugin.getLogger();
    }
}
