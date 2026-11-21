package dev.linqfy.bigCasares.module.runtime;

import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BukkitRuntimeRegistrationsTest {

    @Test
    void listenerCleanupIsOwnedOnlyAfterSuccessfulRegistration() {
        RuntimeRegistrationScope scope = new RuntimeRegistrationScope(new RuntimeGeneration(1), "missions");
        FakeBukkitAccess access = new FakeBukkitAccess();
        BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(scope, access);
        Listener listener = new Listener() { };

        assertSame(listener, registrations.registerListener("mission-listener", listener));
        scope.close();

        assertEquals(List.of(listener), access.registeredListeners);
        assertEquals(List.of(listener), access.unregisteredListeners);

        RuntimeRegistrationScope failedScope = new RuntimeRegistrationScope(new RuntimeGeneration(2), "failed");
        FakeBukkitAccess failedAccess = new FakeBukkitAccess();
        failedAccess.listenerFailure = new IllegalStateException("registration rejected");
        BukkitRuntimeRegistrations failed = new BukkitRuntimeRegistrations(failedScope, failedAccess);

        assertThrows(IllegalStateException.class, () -> failed.registerListener("listener", listener));
        assertTrue(failedScope.close().outcomes().isEmpty());
        assertTrue(failedAccess.unregisteredListeners.isEmpty());
    }

    @Test
    void failedOwnershipRollsBackAnAcquiredListener() {
        RuntimeRegistrationScope scope = new RuntimeRegistrationScope(new RuntimeGeneration(3), "shop");
        scope.close();
        FakeBukkitAccess access = new FakeBukkitAccess();
        BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(scope, access);
        Listener listener = new Listener() { };

        assertThrows(IllegalStateException.class, () -> registrations.registerListener("shop-listener", listener));

        assertEquals(List.of(listener), access.registeredListeners);
        assertEquals(List.of(listener), access.unregisteredListeners);
    }

    @Test
    void ownsLongLivedTasksAndCancelsThemWhenOwnershipFails() {
        RuntimeRegistrationScope scope = new RuntimeRegistrationScope(new RuntimeGeneration(4), "discord");
        BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(scope, new FakeBukkitAccess());
        FakeTask owned = new FakeTask();

        assertSame(owned, registrations.ownTask("reconciliation-task", owned));
        scope.close();
        scope.close();

        assertEquals(1, owned.cancelCalls);

        RuntimeRegistrationScope closedScope = new RuntimeRegistrationScope(new RuntimeGeneration(5), "closed");
        closedScope.close();
        FakeTask rejected = new FakeTask();

        assertThrows(IllegalStateException.class,
            () -> new BukkitRuntimeRegistrations(closedScope, new FakeBukkitAccess()).ownTask("task", rejected));
        assertEquals(1, rejected.cancelCalls);
    }

    @Test
    void recipeRemovalIsOwnedOnlyWhenRecipeWasAdded() {
        RuntimeRegistrationScope scope = new RuntimeRegistrationScope(new RuntimeGeneration(6), "copper-apple");
        FakeBukkitAccess access = new FakeBukkitAccess();
        BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(scope, access);
        NamespacedKey key = new NamespacedKey("bigcasares", "copper_apple");
        Recipe recipe = () -> null;

        assertTrue(registrations.registerRecipe("copper-apple-recipe", key, recipe));
        scope.close();

        assertEquals(List.of(recipe), access.addedRecipes);
        assertEquals(List.of(key), access.removedRecipes);

        RuntimeRegistrationScope skippedScope = new RuntimeRegistrationScope(new RuntimeGeneration(7), "skipped");
        FakeBukkitAccess skippedAccess = new FakeBukkitAccess();
        skippedAccess.recipeAdded = false;
        BukkitRuntimeRegistrations skipped = new BukkitRuntimeRegistrations(skippedScope, skippedAccess);

        assertFalse(skipped.registerRecipe("recipe", key, recipe));
        assertTrue(skippedScope.close().outcomes().isEmpty());
        assertTrue(skippedAccess.removedRecipes.isEmpty());
    }

    @Test
    void commandCleanupRestoresOnlyBindingsStillOwnedByTheScope() {
        RuntimeRegistrationScope scope = new RuntimeRegistrationScope(new RuntimeGeneration(8), "bounties");
        BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(scope, new FakeBukkitAccess());
        CommandExecutor priorExecutor = (sender, command, label, args) -> false;
        TabCompleter priorCompleter = (sender, command, alias, args) -> List.of("prior");
        CommandExecutor ownedExecutor = (sender, command, label, args) -> true;
        TabCompleter ownedCompleter = (sender, command, alias, args) -> List.of("owned");
        CommandExecutor replacementExecutor = (sender, command, label, args) -> true;
        FakeCommandBinding binding = new FakeCommandBinding(priorExecutor, priorCompleter);

        registrations.bindCommand("bounty-command", binding, ownedExecutor, ownedCompleter);
        assertSame(ownedExecutor, binding.executor());
        assertSame(ownedCompleter, binding.tabCompleter());

        binding.executor(replacementExecutor);
        scope.close();

        assertSame(replacementExecutor, binding.executor());
        assertSame(priorCompleter, binding.tabCompleter());
    }

    @Test
    void failedCommandOwnershipRestoresThePriorBinding() {
        RuntimeRegistrationScope scope = new RuntimeRegistrationScope(new RuntimeGeneration(9), "airdrop");
        scope.close();
        BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(scope, new FakeBukkitAccess());
        CommandExecutor priorExecutor = (sender, command, label, args) -> false;
        TabCompleter priorCompleter = (sender, command, alias, args) -> List.of();
        CommandExecutor ownedExecutor = (sender, command, label, args) -> true;
        TabCompleter ownedCompleter = (sender, command, alias, args) -> List.of("owned");
        FakeCommandBinding binding = new FakeCommandBinding(priorExecutor, priorCompleter);

        assertThrows(IllegalStateException.class,
            () -> registrations.bindCommand("airdrop-command", binding, ownedExecutor, ownedCompleter));

        assertSame(priorExecutor, binding.executor());
        assertSame(priorCompleter, binding.tabCompleter());
    }

    @Test
    void commandCleanupAttemptsExecutorRestorationWhenCompleterRestorationFails() {
        RuntimeRegistrationScope scope = new RuntimeRegistrationScope(new RuntimeGeneration(14), "commands");
        BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(scope, new FakeBukkitAccess());
        CommandExecutor priorExecutor = (sender, command, label, args) -> false;
        TabCompleter priorCompleter = (sender, command, alias, args) -> List.of("prior");
        CommandExecutor ownedExecutor = (sender, command, label, args) -> true;
        TabCompleter ownedCompleter = (sender, command, alias, args) -> List.of("owned");
        FakeCommandBinding binding = new FakeCommandBinding(priorExecutor, priorCompleter);
        registrations.bindCommand("root-command", binding, ownedExecutor, ownedCompleter);
        binding.failNextCompleterRestore = true;

        RuntimeCleanupReport report = scope.close();

        assertTrue(report.hasFailures());
        assertSame(priorExecutor, binding.executor());
        assertSame(ownedCompleter, binding.tabCompleter());
    }

    @Test
    void scheduledAndExternallyQueuedCallbacksAreGenerationGuarded() {
        RuntimeGeneration generation = new RuntimeGeneration(10);
        RuntimeRegistrationScope scope = new RuntimeRegistrationScope(generation, "server-control");
        FakeBukkitAccess access = new FakeBukkitAccess();
        BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(scope, access);
        AtomicInteger mutations = new AtomicInteger();

        registrations.scheduleImmediate("immediate", mutations::incrementAndGet);
        registrations.scheduleDelayed("delayed", mutations::incrementAndGet, 5L);
        registrations.scheduleRepeating("repeating", mutations::incrementAndGet, 10L, 20L);
        Runnable external = registrations.guard(mutations::incrementAndGet);

        access.scheduled.forEach(ScheduledCall::run);
        external.run();
        assertEquals(4, mutations.get());
        assertEquals(List.of(
            new ScheduleSpec(ScheduleKind.IMMEDIATE, 0L, 0L),
            new ScheduleSpec(ScheduleKind.DELAYED, 5L, 0L),
            new ScheduleSpec(ScheduleKind.REPEATING, 10L, 20L)
        ), access.scheduled.stream().map(ScheduledCall::spec).toList());

        RuntimeCleanupReport report = scope.close();
        access.scheduled.forEach(ScheduledCall::run);
        external.run();

        assertEquals(4, mutations.get());
        assertFalse(access.scheduled.get(0).task().isCancelled());
        assertFalse(access.scheduled.get(1).task().isCancelled());
        assertTrue(access.scheduled.get(2).task().isCancelled());
        assertEquals(1, report.outcomes().size());
        assertTrue(report.outcomes().getFirst().resourceId().startsWith("repeating#scheduled-"));
    }

    @Test
    void repeatedOneShotsDetachEvenWhenTheyRunBeforeSchedulingReturns() {
        RuntimeRegistrationScope scope = new RuntimeRegistrationScope(new RuntimeGeneration(12), "inventory-limit");
        FakeBukkitAccess access = new FakeBukkitAccess();
        access.runImmediateDuringSchedule = true;
        BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(scope, access);
        AtomicInteger mutations = new AtomicInteger();

        registrations.scheduleImmediate("enforcement", mutations::incrementAndGet);
        registrations.scheduleImmediate("enforcement", mutations::incrementAndGet);

        RuntimeCleanupReport report = scope.close();

        assertEquals(2, mutations.get());
        assertTrue(report.outcomes().isEmpty());
        assertTrue(access.scheduled.stream().noneMatch(call -> call.task().isCancelled()));
    }

    @Test
    void cancelledTasksReleaseTheirRegistration() {
        RuntimeRegistrationScope scope = new RuntimeRegistrationScope(new RuntimeGeneration(15), "mythic");
        FakeBukkitAccess access = new FakeBukkitAccess();
        BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(scope, access);
        registrations.scheduleRepeating("heartbeat", () -> { }, 1L, 1L);
        int baseline = scope.size();

        for (int index = 0; index < 200; index++) {
            BukkitTask[] self = new BukkitTask[1];
            AtomicInteger runs = new AtomicInteger();
            self[0] = registrations.scheduleRepeating("note", () -> {
                if (runs.incrementAndGet() == 3) {
                    self[0].cancel();
                }
            }, 0L, 1L);
        }
        BukkitTask cancelledBeforeRunning = registrations.scheduleDelayed("later", () -> { }, 40L);
        registrations.scheduleImmediate("next", () -> { });
        assertEquals(baseline + 202, scope.size());

        cancelledBeforeRunning.cancel();
        for (int tick = 0; tick < 3; tick++) {
            access.scheduled.stream().filter(call -> !call.task().isCancelled()).toList()
                .forEach(ScheduledCall::run);
        }

        assertEquals(baseline, scope.size());
        assertTrue(access.scheduled.get(1).task().isCancelled());
        assertTrue(access.scheduled.get(201).task().isCancelled());
        RuntimeCleanupReport report = scope.close();
        assertEquals(1, report.outcomes().size());
        assertTrue(report.outcomes().getFirst().resourceId().startsWith("heartbeat#scheduled-"));
    }

    @Test
    void ownsArbitraryCleanupAndCloseablesInTheScope() {
        RuntimeRegistrationScope scope = new RuntimeRegistrationScope(new RuntimeGeneration(11), "geyser");
        BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(scope, new FakeBukkitAccess());
        List<String> cleaned = new ArrayList<>();
        AutoCloseable closeable = () -> cleaned.add("closeable");

        assertSame(closeable, registrations.ownCloseable("subscription", closeable));
        registrations.ownCleanup("presentation", () -> cleaned.add("cleanup"));
        scope.close();

        assertEquals(List.of("cleanup", "closeable"), cleaned);

        RuntimeRegistrationScope closedScope = new RuntimeRegistrationScope(new RuntimeGeneration(13), "closed");
        closedScope.close();
        AtomicInteger rejectedCloseCalls = new AtomicInteger();

        assertThrows(IllegalStateException.class, () -> new BukkitRuntimeRegistrations(closedScope, new FakeBukkitAccess())
            .ownCloseable("gateway", rejectedCloseCalls::incrementAndGet));
        assertEquals(1, rejectedCloseCalls.get());
    }

    @Test
    void temporaryFallbackRemainsSeparateFromModuleScopeOwnership() {
        FakeBukkitAccess access = new FakeBukkitAccess();

        RuntimeCleanupReport report = BukkitRuntimeRegistrations.runTemporaryPluginWideFallback(access);

        assertEquals(List.of("listeners", "tasks"), access.fallbackCalls);
        assertFalse(report.hasFailures());
        assertEquals(List.of("temporary-plugin-listeners", "temporary-plugin-tasks"), report.outcomes().stream()
            .map(RuntimeCleanupOutcome::resourceId)
            .toList());
    }

    @Test
    void temporaryFallbackAttemptsEveryCleanupAndAggregatesFailures() {
        FakeBukkitAccess access = new FakeBukkitAccess();
        access.listenerFallbackFailure = new IllegalStateException("listener registry unavailable");
        access.taskFallbackFailure = new IllegalStateException("scheduler unavailable");

        RuntimeCleanupReport report = BukkitRuntimeRegistrations.runTemporaryPluginWideFallback(access);

        assertEquals(List.of("listeners", "tasks"), access.fallbackCalls);
        assertEquals(List.of("temporary-plugin-listeners", "temporary-plugin-tasks"), report.failures().stream()
            .map(RuntimeCleanupOutcome::resourceId)
            .toList());
        assertEquals("listener registry unavailable", report.failures().get(0).failure().getMessage());
        assertEquals("scheduler unavailable", report.failures().get(1).failure().getMessage());
    }

    private enum ScheduleKind {
        IMMEDIATE,
        DELAYED,
        REPEATING
    }

    private record ScheduleSpec(ScheduleKind kind, long delay, long period) {
    }

    private record ScheduledCall(ScheduleSpec spec, Runnable callback, FakeTask task) {

        private void run() {
            callback.run();
        }
    }

    private static final class FakeBukkitAccess implements BukkitRuntimeRegistrations.BukkitAccess {

        private final List<Listener> registeredListeners = new ArrayList<>();
        private final List<Listener> unregisteredListeners = new ArrayList<>();
        private final List<Recipe> addedRecipes = new ArrayList<>();
        private final List<NamespacedKey> removedRecipes = new ArrayList<>();
        private final List<ScheduledCall> scheduled = new ArrayList<>();
        private final List<String> fallbackCalls = new ArrayList<>();
        private RuntimeException listenerFailure;
        private boolean recipeAdded = true;
        private boolean runImmediateDuringSchedule;
        private RuntimeException listenerFallbackFailure;
        private RuntimeException taskFallbackFailure;

        @Override
        public void registerListener(Listener listener) {
            if (listenerFailure != null) {
                throw listenerFailure;
            }
            registeredListeners.add(listener);
        }

        @Override
        public void unregisterListener(Listener listener) {
            unregisteredListeners.add(listener);
        }

        @Override
        public boolean addRecipe(Recipe recipe) {
            addedRecipes.add(recipe);
            return recipeAdded;
        }

        @Override
        public void removeRecipe(NamespacedKey key) {
            removedRecipes.add(key);
        }

        @Override
        public BukkitTask scheduleImmediate(Runnable callback) {
            BukkitTask task = schedule(new ScheduleSpec(ScheduleKind.IMMEDIATE, 0L, 0L), callback);
            if (runImmediateDuringSchedule) {
                callback.run();
            }
            return task;
        }

        @Override
        public BukkitTask scheduleDelayed(Runnable callback, long delayTicks) {
            return schedule(new ScheduleSpec(ScheduleKind.DELAYED, delayTicks, 0L), callback);
        }

        @Override
        public BukkitTask scheduleRepeating(Runnable callback, long delayTicks, long periodTicks) {
            return schedule(new ScheduleSpec(ScheduleKind.REPEATING, delayTicks, periodTicks), callback);
        }

        @Override
        public void unregisterPluginListeners() {
            fallbackCalls.add("listeners");
            if (listenerFallbackFailure != null) {
                throw listenerFallbackFailure;
            }
        }

        @Override
        public void cancelPluginTasks() {
            fallbackCalls.add("tasks");
            if (taskFallbackFailure != null) {
                throw taskFallbackFailure;
            }
        }

        private BukkitTask schedule(ScheduleSpec spec, Runnable callback) {
            FakeTask task = new FakeTask();
            scheduled.add(new ScheduledCall(spec, callback, task));
            return task;
        }
    }

    private static final class FakeCommandBinding implements BukkitRuntimeRegistrations.CommandBinding {

        private CommandExecutor executor;
        private TabCompleter tabCompleter;
        private boolean failNextCompleterRestore;

        private FakeCommandBinding(CommandExecutor executor, TabCompleter tabCompleter) {
            this.executor = executor;
            this.tabCompleter = tabCompleter;
        }

        @Override
        public CommandExecutor executor() {
            return executor;
        }

        @Override
        public void executor(CommandExecutor executor) {
            this.executor = executor;
        }

        @Override
        public TabCompleter tabCompleter() {
            return tabCompleter;
        }

        @Override
        public void tabCompleter(TabCompleter tabCompleter) {
            if (failNextCompleterRestore) {
                failNextCompleterRestore = false;
                throw new IllegalStateException("completer restore failed");
            }
            this.tabCompleter = tabCompleter;
        }
    }

    private static final class FakeTask implements BukkitTask {

        private int cancelCalls;

        @Override
        public int getTaskId() {
            return 1;
        }

        @Override
        public Plugin getOwner() {
            return null;
        }

        @Override
        public boolean isSync() {
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelCalls > 0;
        }

        @Override
        public void cancel() {
            cancelCalls++;
        }
    }
}
