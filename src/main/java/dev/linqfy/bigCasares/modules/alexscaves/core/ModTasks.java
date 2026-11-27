package dev.linqfy.bigCasares.modules.alexscaves.core;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The tasks of one enabled generation of the module. Turning the module off or reloading it does not disable the
 * plugin, so the server would keep running them: {@link #close()} cancels every task still pending and runs its
 * cleanup (removing the displays it moves, or finishing a block change that must not be left half done). Once
 * closed, nothing more is scheduled: what would have been is cleaned up at once. Main thread only.
 */
public final class ModTasks implements AutoCloseable {
    /** The two scheduler calls used, so the ownership rules can be tested without a server. */
    interface Scheduler {
        BukkitTask later(Runnable action, long delayTicks);

        BukkitTask repeat(BukkitRunnable action, long delayTicks, long periodTicks);
    }

    private static final Runnable NOTHING = () -> { };

    private static final class Entry {
        private final Runnable cleanup;
        private BukkitTask task;
        private boolean done;

        private Entry(Runnable cleanup) {
            this.cleanup = cleanup;
        }
    }

    private final Scheduler scheduler;
    private final Logger logger;
    private final Set<Entry> entries = new LinkedHashSet<>();
    private boolean closed;

    public ModTasks(Plugin plugin) {
        this(new Scheduler() {
            @Override
            public BukkitTask later(Runnable action, long delayTicks) {
                return plugin.getServer().getScheduler().runTaskLater(plugin, action, delayTicks);
            }

            @Override
            public BukkitTask repeat(BukkitRunnable action, long delayTicks, long periodTicks) {
                return action.runTaskTimer(plugin, delayTicks, periodTicks);
            }
        }, plugin.getLogger());
    }

    ModTasks(Scheduler scheduler, Logger logger) {
        this.scheduler = scheduler;
        this.logger = logger;
    }

    /** Runs next tick; dropped when the module stops first. */
    public void run(Runnable action) {
        later(1L, action, NOTHING);
    }

    /** Runs after {@code delayTicks}; dropped when the module stops first. */
    public void later(long delayTicks, Runnable action) {
        later(delayTicks, action, NOTHING);
    }

    /** Runs after {@code delayTicks}; when the module stops first, {@code onStop} runs instead. */
    public void later(long delayTicks, Runnable action, Runnable onStop) {
        Objects.requireNonNull(action, "action");
        Entry entry = new Entry(Objects.requireNonNull(onStop, "onStop"));
        if (closed) {
            clean(entry);
            return;
        }
        prune();
        entries.add(entry);
        entry.task = scheduler.later(() -> {
            entry.done = true;
            entries.remove(entry);
            if (!closed) {
                action.run();
            }
        }, delayTicks);
    }

    /**
     * A repeating task that ends by cancelling itself; when the module stops first it is cancelled and
     * {@code onStop} runs.
     */
    public void repeat(BukkitRunnable action, long delayTicks, long periodTicks, Runnable onStop) {
        Objects.requireNonNull(action, "action");
        Entry entry = new Entry(Objects.requireNonNull(onStop, "onStop"));
        if (closed) {
            clean(entry);
            return;
        }
        prune();
        entry.task = scheduler.repeat(action, delayTicks, periodTicks);
        entries.add(entry);
    }

    /** Tasks still pending. */
    int pending() {
        prune();
        return entries.size();
    }

    /** Forgets the repeating tasks that cancelled themselves. */
    private void prune() {
        entries.removeIf(entry -> entry.done || entry.task != null && entry.task.isCancelled());
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        List<Entry> left = new ArrayList<>(entries);
        entries.clear();
        for (Entry entry : left) {
            if (entry.done || entry.task != null && entry.task.isCancelled()) {
                continue;
            }
            if (entry.task != null) {
                entry.task.cancel();
            }
            clean(entry);
        }
    }

    private void clean(Entry entry) {
        try {
            entry.cleanup.run();
        } catch (RuntimeException | LinkageError failure) {
            logger.log(Level.WARNING, "[AlexsCaves] Falló la limpieza de una tarea", failure);
        }
    }
}
