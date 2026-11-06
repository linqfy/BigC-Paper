package dev.linqfy.bigCasares.modules.tideframe.engine;

import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.atomic.AtomicLong;

/** The embedded engine uses BigCasares' Paper scheduler and its owned runtime scope. */
public final class SchedulerBridge {
    public interface Ticket { void cancel(); }
    private final Plugin plugin;
    private final RuntimeRegistrationScope scope;
    private final BukkitRuntimeRegistrations registrations;
    private final AtomicLong sequence = new AtomicLong();

    public SchedulerBridge(Plugin plugin, RuntimeRegistrationScope scope) {
        this.plugin = plugin;
        this.scope = scope;
        this.registrations = new BukkitRuntimeRegistrations(plugin, scope);
    }

    public void global(Runnable work) {
        enqueue(work, 1);
    }

    public void entity(Entity entity, Runnable work) {
        global(() -> { if (entity.isValid()) work.run(); });
    }

    public void region(Location at, Runnable work) { global(work); }

    public void later(Entity entity, int ticks, Runnable work) {
        enqueue(() -> { if (entity.isValid()) work.run(); }, Math.max(1, ticks));
    }

    private void enqueue(Runnable work, long delay) {
        if (!plugin.isEnabled() || !scope.generation().isActive()) return;
        String id = "engine-once-" + sequence.incrementAndGet();
        var task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                // Paper callbacks can themselves request a module reload: never hold a generation lock across them.
                if (plugin.isEnabled() && scope.generation().isActive()) work.run();
            } finally { scope.forget(id); }
        }, delay);
        registrations.ownTask(id, task);
    }

    public Ticket repeat(Entity entity, Runnable work) {
        if (!plugin.isEnabled() || !scope.generation().isActive()) return () -> { };
        String id = "engine-repeat-" + sequence.incrementAndGet();
        var task = registrations.ownTask(id, Bukkit.getScheduler().runTaskTimer(plugin,
            () -> { if (scope.generation().isActive() && entity.isValid()) work.run(); }, 1, 1));
        return () -> { task.cancel(); scope.forget(id); };
    }
}
