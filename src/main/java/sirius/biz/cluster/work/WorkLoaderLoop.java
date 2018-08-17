/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster.work;

import org.jetbrains.annotations.NotNull;
import sirius.kernel.async.AsyncExecutor;
import sirius.kernel.async.BackgroundLoop;
import sirius.kernel.async.Tasks;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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
        return "cluster-loader";
    }

    @Override
    protected double maxCallFrequency() {
        return 1 / 5d;
    }

    @Override
    protected void doWork() throws Exception {
        try {
            if (schedulerLock.tryLock(1, TimeUnit.SECONDS)) {
                try {
                    scheduleAvailableWork();
                } finally {
                    schedulerLock.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void scheduleAvailableWork() throws Exception {
        AsyncExecutor executor = getExecutor();
        while (executor.getQueue().isEmpty() && executor.getActiveCount() < executor.getMaximumPoolSize()) {
            Runnable work = distributedTasks.fetchWork();
            if (work == null) {
                return;
            }

            executor.submit(() -> executeWork(work));
        }
    }

    @NotNull
    private AsyncExecutor getExecutor() {
        return tasks.executorService("distributed-tasks");
    }

    private void executeWork(Runnable work) {
        work.run();

        scheduleNextWork();
    }

    private void scheduleNextWork() {
        try {
            if (schedulerLock.tryLock(1, TimeUnit.SECONDS)) {
                try {
                    Runnable nextWork = distributedTasks.fetchWork();
                    if (nextWork != null) {
                        getExecutor().submit(() -> executeWork(nextWork));
                    }
                } finally {
                    schedulerLock.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
