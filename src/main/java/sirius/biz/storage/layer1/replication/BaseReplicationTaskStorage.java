/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer1.replication;

import com.alibaba.fastjson.JSONObject;
import sirius.biz.cluster.work.DistributedTasks;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Mixing;
import sirius.db.mixing.query.BaseQuery;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base class for implementing a {@link ReplicationTaskStorage}.
 *
 * @param <T> the entity used to represent a replication task
 * @param <Q> the database dependent query type used to search for tasks
 */
public abstract class BaseReplicationTaskStorage<T extends BaseEntity<?>, Q extends BaseQuery<Q, T>>
        implements ReplicationTaskStorage {

    protected static final String TRANSACTION_ID = "transactionId";

    @ConfigValue("storage.layer1.replication.batchSize")
    protected int batchSize;

    @ConfigValue("storage.layer1.replication.maxBatches")
    protected int maxBatches;

    @ConfigValue("storage.layer1.replication.replicateDeleteDelay")
    protected Duration replicateDeleteDelay;

    @ConfigValue("storage.layer1.replication.replicateUpdateDelay")
    protected Duration replicateUpdateDelay;

    @ConfigValue("storage.layer1.replication.retryReplicationDelay")
    protected Duration retryReplicationDelay;

    @ConfigValue("storage.layer1.replication.maxReplicationAttempts")
    protected int maxReplicationAttempts;

    @Part
    private Mixing mixing;

    @Part
    protected ReplicationManager replicationManager;

    @Part
    protected DistributedTasks distributedTasks;

    @Override
    public int emitBatches() {
        AtomicInteger numberOfBatches = new AtomicInteger(maxBatches);
        AtomicInteger scheduledTasks = new AtomicInteger(0);

        while (numberOfBatches.decrementAndGet() > 0) {
            String transactionId = Strings.generateCode(32);
            AtomicBoolean tasksFound = new AtomicBoolean();
            queryExecutableTasks().limit(batchSize).iterateAll(task -> {
                markTaskAsScheduled(task, transactionId);
                tasksFound.set(true);
                scheduledTasks.incrementAndGet();
            });

            if (tasksFound.get()) {
                distributedTasks.submitFIFOTask(ReplicationTaskExecutor.class,
                                                new JSONObject().fluentPut(TRANSACTION_ID, transactionId));
            } else {
                return scheduledTasks.get();
            }
        }

        return scheduledTasks.get();
    }

    protected abstract Q queryExecutableTasks();

    protected abstract void markTaskAsScheduled(T task, String transactionId);

    @Override
    public int countNumberOfScheduledTasks() {
        return distributedTasks.getQueueLength(ReplicationTaskExecutor.REPLICATION_TASK_QUEUE) * batchSize;
    }
}
