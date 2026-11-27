package dev.linqfy.bigCasares.modules.alexscaves.core;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModTasksTest {
    private static final class FakeTask implements BukkitTask {
        private final Runnable action;
        private boolean cancelled;

        private FakeTask(Runnable action) {
            this.action = action;
        }

        @Override
        public int getTaskId() {
            return 0;
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
            return cancelled;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }

    private static final class FakeScheduler implements ModTasks.Scheduler {
        private final List<FakeTask> tasks = new ArrayList<>();

        @Override
        public BukkitTask later(Runnable action, long delayTicks) {
            FakeTask task = new FakeTask(action);
            tasks.add(task);
            return task;
        }

        @Override
        public BukkitTask repeat(BukkitRunnable action, long delayTicks, long periodTicks) {
            return later(action, delayTicks);
        }
    }

    private final FakeScheduler scheduler = new FakeScheduler();
    private final ModTasks tasks = new ModTasks(scheduler, Logger.getLogger("test"));
    private final List<String> calls = new ArrayList<>();

    private static BukkitRunnable runnable() {
        return new BukkitRunnable() {
            @Override
            public void run() {
            }
        };
    }

    @Test
    void closingCancelsWhatIsPendingAndCleansItUp() {
        tasks.later(30L, () -> calls.add("explode"), () -> calls.add("remove display"));
        tasks.repeat(runnable(), 1L, 1L, () -> calls.add("remove bolt"));
        tasks.run(() -> calls.add("next tick"));
        tasks.close();
        tasks.close();
        assertEquals(List.of("remove display", "remove bolt"), calls);
        assertTrue(scheduler.tasks.stream().allMatch(FakeTask::isCancelled));
    }

    @Test
    void aTaskThatAlreadyEndedIsNotCleanedUpAgain() {
        tasks.later(5L, () -> calls.add("ran"), () -> calls.add("cleanup"));
        tasks.repeat(runnable(), 1L, 1L, () -> calls.add("cleanup repeat"));
        scheduler.tasks.get(0).action.run();
        // A repeating task ends by cancelling itself.
        scheduler.tasks.get(1).cancel();
        assertEquals(0, tasks.pending());
        tasks.close();
        assertEquals(List.of("ran"), calls);
    }

    @Test
    void nothingIsScheduledOnceClosedAndItsCleanupRunsAtOnce() {
        tasks.close();
        tasks.later(5L, () -> calls.add("late"), () -> calls.add("cleaned"));
        tasks.run(() -> calls.add("late next tick"));
        tasks.repeat(runnable(), 1L, 1L, () -> calls.add("cleaned repeat"));
        assertEquals(List.of("cleaned", "cleaned repeat"), calls);
        assertTrue(scheduler.tasks.isEmpty());
    }

    @Test
    void aFailingCleanupDoesNotStopTheOthers() {
        tasks.later(5L, () -> { }, () -> {
            throw new IllegalStateException("broken");
        });
        tasks.later(5L, () -> { }, () -> calls.add("still cleaned"));
        tasks.close();
        assertEquals(List.of("still cleaned"), calls);
    }
}
