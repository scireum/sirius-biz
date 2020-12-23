/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer1.replication;

import sirius.biz.cluster.work.DistributedTasks;
import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.Sirius;
import sirius.kernel.async.BackgroundLoop;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Responsible for generating batches of replication tasks via {@link ReplicationTaskStorage#emitBatches()}.
 *
 * @see ReplicationTaskStorage
 */
@Register(framework = StorageUtils.FRAMEWORK_STORAGE)
public class ReplicationBackgroundLoop extends BackgroundLoop {

    private static final double EVERY_TWO_SECONDS = 1d / 2;
    private static final double EVERY_THIRTY_SECONDS = 1d / 30;

    @ConfigValue("storage.layer1.replication.minBatches")
    private int minBatches;

    @Part
    private DistributedTasks distributedTasks;

    @Part
    @Nullable
    private ReplicationTaskStorage taskStorage;

    @Nonnull
    @Override
    public String getName() {
        return "storage-layer1-replication";
    }

    @Override
    public double maxCallFrequency() {
        return Sirius.isStartedAsTest() ? EVERY_TWO_SECONDS : EVERY_THIRTY_SECONDS;
    }

    @Nullable
    @Override
    protected String doWork() throws Exception {
        if (taskStorage == null) {
            return null;
        }
        if (distributedTasks.getQueueLength(ReplicationTaskExecutor.REPLICATION_TASK_QUEUE) > minBatches) {
            return null;
        }

        int numberOfTasks = taskStorage.emitBatches();
        if (numberOfTasks > 0) {
            return "Emitted " + numberOfTasks + " tasks...";
        } else {
            return null;
        }
    }
}
