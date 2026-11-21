package dev.linqfy.bigCasares.module.runtime;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public final class BukkitRuntimeRegistrations {

    private final RuntimeRegistrationScope scope;
    private final BukkitAccess access;
    private final AtomicLong scheduledTaskSequence = new AtomicLong();

    public BukkitRuntimeRegistrations(Plugin plugin, RuntimeRegistrationScope scope) {
        this(scope, new ServerBukkitAccess(plugin));
    }

    BukkitRuntimeRegistrations(RuntimeRegistrationScope scope, BukkitAccess access) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.access = Objects.requireNonNull(access, "access");
    }

    public <T extends Listener> T registerListener(String resourceId, T listener) {
        Objects.requireNonNull(listener, "listener");
        access.registerListener(listener);
        try {
            scope.register(resourceId, () -> access.unregisterListener(listener));
        } catch (RuntimeException | Error failure) {
            rollback(failure, () -> access.unregisterListener(listener));
            throw failure;
        }
        return listener;
    }

    public <T extends BukkitTask> T ownTask(String resourceId, T task) {
        Objects.requireNonNull(task, "task");
        try {
            scope.register(resourceId, () -> cancel(task));
        } catch (RuntimeException | Error failure) {
            rollback(failure, () -> cancel(task));
            throw failure;
        }
        return task;
    }

    public boolean registerRecipe(String resourceId, NamespacedKey key, Recipe recipe) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(recipe, "recipe");
        if (!access.addRecipe(recipe)) {
            return false;
        }
        try {
            scope.register(resourceId, () -> access.removeRecipe(key));
        } catch (RuntimeException | Error failure) {
            rollback(failure, () -> access.removeRecipe(key));
            throw failure;
        }
        return true;
    }

    public void bindCommand(
        String resourceId,
        PluginCommand command,
        CommandExecutor executor,
        TabCompleter tabCompleter
    ) {
        bindCommand(resourceId, new PluginCommandBinding(command), executor, tabCompleter);
    }

    void bindCommand(
        String resourceId,
        CommandBinding binding,
        CommandExecutor executor,
        TabCompleter tabCompleter
    ) {
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(executor, "executor");
        CommandExecutor priorExecutor = binding.executor();
        TabCompleter priorCompleter = binding.tabCompleter();
        try {
            binding.executor(executor);
            binding.tabCompleter(tabCompleter);
            scope.register(resourceId,
                () -> restoreCommand(binding, executor, tabCompleter, priorExecutor, priorCompleter));
        } catch (RuntimeException | Error failure) {
            rollback(failure,
                () -> restoreCommand(binding, executor, tabCompleter, priorExecutor, priorCompleter));
            throw failure;
        }
    }

    public BukkitTask scheduleImmediate(String resourceId, Runnable callback) {
        return scheduleOneShot(resourceId, callback, access::scheduleImmediate);
    }

    public BukkitTask scheduleDelayed(String resourceId, Runnable callback, long delayTicks) {
        return scheduleOneShot(resourceId, callback,
            guardedCallback -> access.scheduleDelayed(guardedCallback, delayTicks));
    }

    public BukkitTask scheduleRepeating(
        String resourceId,
        Runnable callback,
        long delayTicks,
        long periodTicks
    ) {
        String ownershipId = scheduledOwnershipId(resourceId);
        BukkitTask task = ownTask(ownershipId,
            access.scheduleRepeating(scope.guard(callback), delayTicks, periodTicks));
        return new ForgettingTask(task, () -> scope.forget(ownershipId));
    }

    public Runnable guard(Runnable callback) {
        return scope.guard(callback);
    }

    public <T extends AutoCloseable> T ownCloseable(String resourceId, T closeable) {
        Objects.requireNonNull(closeable, "closeable");
        try {
            scope.register(resourceId, closeable::close);
        } catch (RuntimeException | Error failure) {
            rollback(failure, closeable::close);
            throw failure;
        }
        return closeable;
    }

    public void ownCleanup(String resourceId, RuntimeCleanupAction cleanup) {
        scope.register(resourceId, cleanup);
    }

    public static RuntimeCleanupReport runTemporaryPluginWideFallback(Plugin plugin) {
        return runTemporaryPluginWideFallback(new ServerBukkitAccess(plugin));
    }

    static RuntimeCleanupReport runTemporaryPluginWideFallback(BukkitAccess access) {
        Objects.requireNonNull(access, "access");
        List<RuntimeCleanupOutcome> outcomes = new ArrayList<>(2);
        runFallbackCleanup("temporary-plugin-listeners", access::unregisterPluginListeners, outcomes);
        runFallbackCleanup("temporary-plugin-tasks", access::cancelPluginTasks, outcomes);
        return new RuntimeCleanupReport(outcomes, false);
    }

    private static void cancel(BukkitTask task) {
        if (!task.isCancelled()) {
            task.cancel();
        }
    }

    private BukkitTask scheduleOneShot(
        String resourceId,
        Runnable callback,
        OneShotScheduler scheduler
    ) {
        String ownershipId = scheduledOwnershipId(resourceId);
        OneShotLease lease = new OneShotLease(scope, ownershipId);
        Runnable guardedCallback = scope.guard(Objects.requireNonNull(callback, "callback"));
        BukkitTask task = scheduler.schedule(() -> {
            try {
                guardedCallback.run();
            } finally {
                lease.complete();
            }
        });
        return lease.attach(task);
    }

    private String scheduledOwnershipId(String resourceId) {
        String normalizedId = Objects.requireNonNull(resourceId, "resourceId").trim();
        if (normalizedId.isEmpty()) {
            throw new IllegalArgumentException("resourceId must not be blank");
        }
        return normalizedId + "#scheduled-" + scheduledTaskSequence.incrementAndGet();
    }

    private static void restoreCommand(
        CommandBinding binding,
        CommandExecutor ownedExecutor,
        TabCompleter ownedCompleter,
        CommandExecutor priorExecutor,
        TabCompleter priorCompleter
    ) throws Exception {
        Throwable failure = null;
        try {
            if (binding.tabCompleter() == ownedCompleter) {
                binding.tabCompleter(priorCompleter);
            }
        } catch (Throwable caught) {
            failure = caught;
        }
        try {
            if (binding.executor() == ownedExecutor) {
                binding.executor(priorExecutor);
            }
        } catch (Throwable caught) {
            if (failure == null) {
                failure = caught;
            } else {
                failure.addSuppressed(caught);
            }
        }
        if (failure instanceof Exception exception) {
            throw exception;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }

    private static void rollback(Throwable failure, RuntimeCleanupAction cleanup) {
        try {
            cleanup.clean();
        } catch (Throwable cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private static void runFallbackCleanup(
        String resourceId,
        RuntimeCleanupAction cleanup,
        List<RuntimeCleanupOutcome> outcomes
    ) {
        try {
            cleanup.clean();
            outcomes.add(new RuntimeCleanupOutcome(resourceId, null));
        } catch (Throwable failure) {
            outcomes.add(new RuntimeCleanupOutcome(resourceId, failure));
        }
    }

    interface BukkitAccess {

        void registerListener(Listener listener);

        void unregisterListener(Listener listener);

        boolean addRecipe(Recipe recipe);

        void removeRecipe(NamespacedKey key);

        BukkitTask scheduleImmediate(Runnable callback);

        BukkitTask scheduleDelayed(Runnable callback, long delayTicks);

        BukkitTask scheduleRepeating(Runnable callback, long delayTicks, long periodTicks);

        void unregisterPluginListeners();

        void cancelPluginTasks();
    }

    interface CommandBinding {

        CommandExecutor executor();

        void executor(CommandExecutor executor);

        TabCompleter tabCompleter();

        void tabCompleter(TabCompleter tabCompleter);
    }

    @FunctionalInterface
    private interface OneShotScheduler {

        BukkitTask schedule(Runnable callback);
    }

    private static final class OneShotLease {

        private final RuntimeRegistrationScope scope;
        private final String resourceId;
        private boolean completed;

        private OneShotLease(RuntimeRegistrationScope scope, String resourceId) {
            this.scope = scope;
            this.resourceId = resourceId;
        }

        private synchronized BukkitTask attach(BukkitTask task) {
            Objects.requireNonNull(task, "task");
            if (completed) {
                return task;
            }
            try {
                scope.register(resourceId, () -> cancel(task));
            } catch (RuntimeException | Error failure) {
                rollback(failure, () -> cancel(task));
                throw failure;
            }
            return new ForgettingTask(task, this::complete);
        }

        private synchronized void complete() {
            completed = true;
            scope.forget(resourceId);
        }
    }

    /**
     * La tarea que recibe quien programa: cancelarla tambien suelta su registro en el scope, asi las tareas que se
     * cancelan solas (o que cancela su dueno antes del cierre) no se acumulan hasta que se apague el modulo.
     */
    private static final class ForgettingTask implements BukkitTask {

        private final BukkitTask delegate;
        private final Runnable forget;

        private ForgettingTask(BukkitTask delegate, Runnable forget) {
            this.delegate = delegate;
            this.forget = forget;
        }

        @Override
        public int getTaskId() {
            return delegate.getTaskId();
        }

        @Override
        public Plugin getOwner() {
            return delegate.getOwner();
        }

        @Override
        public boolean isSync() {
            return delegate.isSync();
        }

        @Override
        public boolean isCancelled() {
            return delegate.isCancelled();
        }

        @Override
        public void cancel() {
            try {
                delegate.cancel();
            } finally {
                forget.run();
            }
        }
    }

    private static final class ServerBukkitAccess implements BukkitAccess {

        private final Plugin plugin;

        private ServerBukkitAccess(Plugin plugin) {
            this.plugin = Objects.requireNonNull(plugin, "plugin");
        }

        @Override
        public void registerListener(Listener listener) {
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        }

        @Override
        public void unregisterListener(Listener listener) {
            HandlerList.unregisterAll(listener);
        }

        @Override
        public boolean addRecipe(Recipe recipe) {
            return Bukkit.addRecipe(recipe);
        }

        @Override
        public void removeRecipe(NamespacedKey key) {
            Bukkit.removeRecipe(key);
        }

        @Override
        public BukkitTask scheduleImmediate(Runnable callback) {
            return plugin.getServer().getScheduler().runTask(plugin, callback);
        }

        @Override
        public BukkitTask scheduleDelayed(Runnable callback, long delayTicks) {
            return plugin.getServer().getScheduler().runTaskLater(plugin, callback, delayTicks);
        }

        @Override
        public BukkitTask scheduleRepeating(Runnable callback, long delayTicks, long periodTicks) {
            return plugin.getServer().getScheduler().runTaskTimer(plugin, callback, delayTicks, periodTicks);
        }

        @Override
        public void unregisterPluginListeners() {
            HandlerList.unregisterAll(plugin);
        }

        @Override
        public void cancelPluginTasks() {
            plugin.getServer().getScheduler().cancelTasks(plugin);
        }
    }

    private static final class PluginCommandBinding implements CommandBinding {

        private final PluginCommand command;

        private PluginCommandBinding(PluginCommand command) {
            this.command = Objects.requireNonNull(command, "command");
        }

        @Override
        public CommandExecutor executor() {
            return command.getExecutor();
        }

        @Override
        public void executor(CommandExecutor executor) {
            command.setExecutor(executor);
        }

        @Override
        public TabCompleter tabCompleter() {
            return command.getTabCompleter();
        }

        @Override
        public void tabCompleter(TabCompleter tabCompleter) {
            command.setTabCompleter(tabCompleter);
        }
    }
}
