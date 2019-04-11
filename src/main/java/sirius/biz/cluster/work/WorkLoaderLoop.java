/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster.work;

import sirius.kernel.async.AsyncExecutor;
import sirius.kernel.async.BackgroundLoop;
import sirius.kernel.async.Tasks;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Responsible for loading available work into our local thread pool.
 * <p>
 * This loop checks if regular intervals if work if our local thread pool has free resources and also if work is
 * available and schedules tasks accordingly. Also note, that once a task is completed,
 * {@link #executeWork(DistributedTasks.DistributedTask)} immediatelly tries to find another task. Therefore the
 * performance is not limited by the rather slow running background loop.
 */
@Register(classes = BackgroundLoop.class)
public class WorkLoaderLoop extends BackgroundLoop {

    @Part
    private Tasks tasks;

    @Part
    private DistributedTasks distributedTasks;

    private Lock schedulerLock = new ReentrantLock();

    @Nonnull
    @Override
    public String getName() {
        return "distributed-tasks-work-loader";
    }

    @Override
    public double maxCallFrequency() {
        return 1 / 5d;
    }

    @Override
    protected String doWork() throws Exception {
        AtomicInteger tasksScheduled = new AtomicInteger(0);
        locked(() -> tasksScheduled.set(scheduleAvailableWork()));
        return tasksScheduled.get() == 0 ? null : Strings.apply("Scheduled %d tasks.", tasksScheduled.get());
    }

    private int scheduleAvailableWork() {
        AsyncExecutor executor = distributedTasks.getLocalExecutor();
        int tasksScheduled = 0;
        while (executor.getQueue().isEmpty() && executor.getActiveCount() < executor.getMaximumPoolSize()) {
            Optional<DistributedTasks.DistributedTask> work = distributedTasks.fetchWork();
            if (work.isPresent()) {
                executor.submit(() -> executeWork(work.get()));
                tasksScheduled++;
            } else {
                return tasksScheduled;
            }
        }

        return tasksScheduled;
    }

    private void locked(Runnable runInLock) {
        try {
            if (schedulerLock.tryLock(1, TimeUnit.SECONDS)) {
                try {
                    runInLock.run();
                } finally {
                    schedulerLock.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void executeWork(DistributedTasks.DistributedTask work) {
        work.execute();
        scheduleNextWork();
    }

    private void scheduleNextWork() {
        locked(() -> distributedTasks.fetchWork()
                                     .ifPresent(work -> distributedTasks.getLocalExecutor()
                                                                        .submit(() -> executeWork(work))));
    }
}
