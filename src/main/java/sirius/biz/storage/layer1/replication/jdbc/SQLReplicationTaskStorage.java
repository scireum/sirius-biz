/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer1.replication.jdbc;

import com.alibaba.fastjson.JSONObject;
import sirius.biz.analytics.scheduler.SQLEntityBatchEmitter;
import sirius.biz.cluster.work.DistributedTasks;
import sirius.biz.storage.layer1.replication.ReplicationManager;
import sirius.biz.storage.layer1.replication.ReplicationTaskExecutor;
import sirius.biz.storage.layer1.replication.ReplicationTaskStorage;
import sirius.biz.storage.util.StorageUtils;
import sirius.db.jdbc.Database;
import sirius.db.jdbc.OMA;
import sirius.db.jdbc.SmartQuery;
import sirius.db.mixing.Mixing;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;

import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provides a replication task storage based on the underlying JDBC database.
 */
@Register(framework = SQLReplicationTaskStorage.FRAMEWORK_JDBC_REPLICATION)
public class SQLReplicationTaskStorage implements ReplicationTaskStorage {

    public static final String FRAMEWORK_JDBC_REPLICATION = "biz.storage-replication-jdbc";

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
    private SQLEntityBatchEmitter emitter;

    @Part
    private OMA oma;

    @Part
    private Mixing mixing;

    @Part
    private ReplicationManager replicationManager;

    @Part
    private DistributedTasks distributedTasks;

    @Override
    public void notifyAboutDelete(String primarySpace, String objectId) {
        oma.select(SQLReplicationTask.class)
           .eq(SQLReplicationTask.PRIMARY_SPACE, primarySpace)
           .eq(SQLReplicationTask.OBJECT_KEY, objectId)
           .delete();

        SQLReplicationTask task = new SQLReplicationTask();
        task.setPrimarySpace(primarySpace);
        task.setObjectKey(objectId);
        task.setPerformDelete(true);
        task.setEarliestExecution(LocalDateTime.now().plus(replicateDeleteDelay));
        oma.update(task);
    }

    @Override
    public void notifyAboutUpdate(String primarySpace, String objectId) {
        oma.select(SQLReplicationTask.class)
           .eq(SQLReplicationTask.PRIMARY_SPACE, primarySpace)
           .eq(SQLReplicationTask.OBJECT_KEY, objectId)
           .delete();

        SQLReplicationTask task = new SQLReplicationTask();
        task.setPrimarySpace(primarySpace);
        task.setObjectKey(objectId);
        task.setPerformDelete(false);
        task.setEarliestExecution(LocalDateTime.now().plus(replicateUpdateDelay));
        oma.update(task);
    }

    @Override
    public int emitBatches() {
        Database database = oma.getDatabase(mixing.getDescriptor(SQLReplicationTask.class).getRealm());
        AtomicInteger numberOfBatches = new AtomicInteger(maxBatches);
        AtomicInteger scheduledTasks = new AtomicInteger(0);
        emitter.computeBatches(SQLReplicationTask.class, qry -> enhanceQuery(qry, true), batchSize, batch -> {
            distributedTasks.submitFIFOTask(ReplicationTaskExecutor.class, batch);
            scheduledTasks.addAndGet(batchSize);

            markTasksAsScheduled(database, batch);

            return numberOfBatches.decrementAndGet() > 0;
        });

        return scheduledTasks.get();
    }

    private void markTasksAsScheduled(Database database, JSONObject batch) {
        try {
            database.createQuery("UPDATE sqlreplicationtask "
                                 + "SET scheduled = ${scheduled} "
                                 + "WHERE id >= ${lowerBound}"
                                 + " AND id <= ${upperBound}"
                                 + " AND failed = 0"
                                 + " AND earliestExecution < ${executionLimit}")
                    .set("scheduled", LocalDateTime.now())
                    .set("executionLimit", LocalDateTime.now())
                    .set("lowerBound", batch.getLongValue(SQLEntityBatchEmitter.START_ID))
                    .set("upperBound", batch.getLongValue(SQLEntityBatchEmitter.END_ID))
                    .set("scheduled", LocalDateTime.now())
                    .executeUpdate();
        } catch (SQLException e) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .error(e)
                      .withSystemErrorMessage(
                              "Layer 1/replication: Failed to mark SQL replication tasks as scheduled: %s (%s)")
                      .handle();
        }
    }

    private void enhanceQuery(SmartQuery<SQLReplicationTask> qry, boolean forScheduling) {
        qry.eq(SQLReplicationTask.FAILED, false);
        qry.where(OMA.FILTERS.lt(SQLReplicationTask.EARLIEST_EXECUTION, LocalDateTime.now()));
        if (forScheduling) {
            qry.eq(SQLReplicationTask.SCHEDULED, null);
        } else {
            qry.ne(SQLReplicationTask.SCHEDULED, null);
        }
    }

    @Override
    public void executeBatch(JSONObject batch) {
        emitter.evaluateBatch(batch, qry -> enhanceQuery(qry, false), this::executeTask);
    }

    @Override
    public int countTotalNumberOfTasks() {
        return (int) oma.select(SQLReplicationTask.class).eq(SQLReplicationTask.FAILED, false).count();
    }

    @Override
    public int countNumberOfExecutableTasks() {
        return (int) oma.select(SQLReplicationTask.class)
                        .eq(SQLReplicationTask.FAILED, false)
                        .where(OMA.FILTERS.gt(SQLReplicationTask.EARLIEST_EXECUTION, LocalDateTime.now()))
                        .count();
    }

    @Override
    public int countNumberOfScheduledTasks() {
        return distributedTasks.getQueueLength(ReplicationTaskExecutor.REPLICATION_TASK_QUEUE) * batchSize;
    }

    private void executeTask(SQLReplicationTask task) {
        try {
            replicationManager.executeReplicationTask(task.getPrimarySpace(),
                                                      task.getObjectKey(),
                                                      task.isPerformDelete());
            oma.delete(task);
        } catch (Exception ex) {
            try {
                task = oma.refreshOrFail(task);
                task.setLastExecution(LocalDateTime.now());
                task.setEarliestExecution(LocalDateTime.now().plus(retryReplicationDelay));
                task.setFailureCounter(task.getFailureCounter() + 1);
                if (task.getFailureCounter() > maxReplicationAttempts) {
                    task.setFailed(true);
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

                oma.update(task);
            } catch (Exception e) {
                // If a task cannot be refreshed or update it was most probably deleted
                // or updated by an administrative task -> simply ignore this error as we're either done
                // or we'll eventually re-process this task...
                Exceptions.ignore(e);
            }
        }
    }
}
