/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer1.replication.mongo;

import com.alibaba.fastjson.JSONObject;
import sirius.biz.analytics.scheduler.MongoEntityBatchEmitter;
import sirius.biz.cluster.work.DistributedTasks;
import sirius.biz.storage.layer1.replication.ReplicationManager;
import sirius.biz.storage.layer1.replication.ReplicationTaskExecutor;
import sirius.biz.storage.layer1.replication.ReplicationTaskStorage;
import sirius.biz.storage.util.StorageUtils;
import sirius.db.mixing.Mixing;
import sirius.db.mongo.Mango;
import sirius.db.mongo.Mongo;
import sirius.db.mongo.MongoEntity;
import sirius.db.mongo.MongoQuery;
import sirius.db.mongo.QueryBuilder;
import sirius.db.mongo.Updater;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provides a replication task storage based on the underlying MongoDB.
 */
@Register(framework = MongoReplicationTaskStorage.FRAMEWORK_MONGO_REPLICATION)
public class MongoReplicationTaskStorage implements ReplicationTaskStorage {

    /**
     * Contains the name of the framework which has to be enabled to support the coordination of the
     * replication layer via MongoDB entities.
     */
    public static final String FRAMEWORK_MONGO_REPLICATION = "biz.storage-replication-mongo";

    @ConfigValue("storage.layer1.replication.batchSize")
    private int batchSize;

    @ConfigValue("storage.layer1.replication.maxBatches")
    private int maxBatches;

    @ConfigValue("storage.layer1.replication.replicateDeleteDelay")
    private Duration replicateDeleteDelay;

    @ConfigValue("storage.layer1.replication.replicateUpdateDelay")
    private Duration replicateUpdateDelay;

    @ConfigValue("storage.layer1.replication.retryReplicationDelay")
    private Duration retryReplicationDelay;

    @ConfigValue("storage.layer1.replication.maxReplicationAttempts")
    private int maxReplicationAttempts;

    @Part
    private MongoEntityBatchEmitter emitter;

    @Part
    private Mango mango;

    @Part
    private Mongo mongo;

    @Part
    private Mixing mixing;

    @Part
    private ReplicationManager replicationManager;

    @Part
    private DistributedTasks distributedTasks;

    @Override
    public void notifyAboutDelete(String primarySpace, String objectId) {
        mango.select(MongoReplicationTask.class)
             .eq(MongoReplicationTask.PRIMARY_SPACE, primarySpace)
             .eq(MongoReplicationTask.OBJECT_KEY, objectId)
             .delete();

        MongoReplicationTask task = new MongoReplicationTask();
        task.setPrimarySpace(primarySpace);
        task.setObjectKey(objectId);
        task.setPerformDelete(true);
        task.setEarliestExecution(LocalDateTime.now().plus(replicateDeleteDelay));
        mango.update(task);
    }

    @Override
    public void notifyAboutUpdate(String primarySpace, String objectId) {
        mango.select(MongoReplicationTask.class)
             .eq(MongoReplicationTask.PRIMARY_SPACE, primarySpace)
             .eq(MongoReplicationTask.OBJECT_KEY, objectId)
             .delete();

        MongoReplicationTask task = new MongoReplicationTask();
        task.setPrimarySpace(primarySpace);
        task.setObjectKey(objectId);
        task.setPerformDelete(false);
        task.setEarliestExecution(LocalDateTime.now().plus(replicateUpdateDelay));
        mango.update(task);
    }

    @Override
    public int emitBatches() {
        AtomicInteger numberOfBatches = new AtomicInteger(maxBatches);
        AtomicInteger scheduledTasks = new AtomicInteger(0);
        emitter.computeBatches(MongoReplicationTask.class,
                               qry -> filterOnExecutableTasks(qry, true),
                               batchSize,
                               batch -> {
                                   distributedTasks.submitFIFOTask(ReplicationTaskExecutor.class, batch);
                                   scheduledTasks.addAndGet(batchSize);

                                   markTasksAsScheduled(batch);

                                   return numberOfBatches.decrementAndGet() > 0;
                               });

        return scheduledTasks.get();
    }

    private void markTasksAsScheduled(JSONObject batch) {
        try {
            mongo.update()
                 .set(MongoReplicationTask.SCHEDULED, LocalDateTime.now())
                 .where(MongoReplicationTask.SCHEDULED, null)
                 .where(QueryBuilder.FILTERS.lt(MongoReplicationTask.EARLIEST_EXECUTION, LocalDateTime.now()))
                 .where(QueryBuilder.FILTERS.gte(MongoReplicationTask.ID,
                                                 batch.getString(MongoEntityBatchEmitter.START_ID)))
                 .where(QueryBuilder.FILTERS.lte(MongoReplicationTask.ID,
                                                 batch.getString(MongoEntityBatchEmitter.END_ID)))
                 .executeFor(MongoReplicationTask.class);
        } catch (Exception e) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .error(e)
                      .withSystemErrorMessage(
                              "Layer 1/replication: Failed to mark MongoDB replication tasks as scheduled: %s (%s)")
                      .handle();
        }
    }

    private void filterOnExecutableTasks(MongoQuery<MongoEntity> query, boolean forScheduling) {
        query.eq(MongoReplicationTask.FAILED, false);
        query.where(QueryBuilder.FILTERS.lt(MongoReplicationTask.EARLIEST_EXECUTION, LocalDateTime.now()));
        if (forScheduling) {
            query.eq(MongoReplicationTask.SCHEDULED, null);
        } else {
            query.ne(MongoReplicationTask.SCHEDULED, null);
        }
    }

    @Override
    public void executeBatch(JSONObject batch) {
        emitter.evaluateBatch(batch, query -> filterOnExecutableTasks(query, false), this::executeTask);
    }

    @Override
    public int countTotalNumberOfTasks() {
        return (int) mango.select(MongoReplicationTask.class).eq(MongoReplicationTask.FAILED, false).count();
    }

    @Override
    public int countNumberOfExecutableTasks() {
        return (int) mango.select(MongoReplicationTask.class)
                          .eq(MongoReplicationTask.FAILED, false)
                          .where(QueryBuilder.FILTERS.gt(MongoReplicationTask.EARLIEST_EXECUTION, LocalDateTime.now()))
                          .count();
    }

    @Override
    public int countNumberOfScheduledTasks() {
        return distributedTasks.getQueueLength(ReplicationTaskExecutor.REPLICATION_TASK_QUEUE) * batchSize;
    }

    private void executeTask(MongoReplicationTask task) {
        try {
            replicationManager.executeReplicationTask(task.getPrimarySpace(),
                                                      task.getObjectKey(),
                                                      task.isPerformDelete());
            mango.delete(task);
        } catch (Exception ex) {
            try {
                Updater updater = mongo.update()
                                       .where(MongoReplicationTask.ID, task.getId())
                                       .set(MongoReplicationTask.LAST_EXECUTION, LocalDateTime.now())
                                       .set(MongoReplicationTask.EARLIEST_EXECUTION,
                                            LocalDateTime.now().plus(retryReplicationDelay))
                                       .inc(MongoReplicationTask.FAILURE_COUNTER, 1);
                if (task.getFailureCounter() + 1 > maxReplicationAttempts) {
                    updater.set(MongoReplicationTask.FAILED, true);
                    Exceptions.handle()
                              .to(StorageUtils.LOG)
                              .error(ex)
                              .withSystemErrorMessage(
                                      "Layer 1/replication: A storage replication task (%s) ultimatively failed: Primary space: %s, object: %s - %s (%s)",
                                      task.getIdAsString(),
                                      task.getPrimarySpace(),
                                      task.getObjectKey())
                              .handle();
                }

                updater.executeFor(MongoReplicationTask.class);
            } catch (Exception e) {
                Exceptions.handle(StorageUtils.LOG, e);
            }
        }
    }
}
