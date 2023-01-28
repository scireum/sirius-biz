/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer1.replication.jdbc;

import com.alibaba.fastjson.JSONObject;
import sirius.biz.storage.layer1.replication.BaseReplicationTaskStorage;
import sirius.biz.storage.util.StorageUtils;
import sirius.db.jdbc.OMA;
import sirius.db.jdbc.Operator;
import sirius.db.jdbc.SmartQuery;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;

import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * Provides a replication task storage based on the underlying JDBC database.
 */
@Register(framework = SQLReplicationTaskStorage.FRAMEWORK_JDBC_REPLICATION)
public class SQLReplicationTaskStorage
        extends BaseReplicationTaskStorage<SQLReplicationTask, SmartQuery<SQLReplicationTask>> {

    /**
     * Contains the name of the framework which has to be enabled to support the coordination of the
     * replication layer via JDBC entities.
     */
    public static final String FRAMEWORK_JDBC_REPLICATION = "biz.storage-replication-jdbc";

    @Part
    private OMA oma;

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
    public void notifyAboutUpdate(String primarySpace, String objectId, long contentLength) {
        oma.select(SQLReplicationTask.class)
           .eq(SQLReplicationTask.PRIMARY_SPACE, primarySpace)
           .eq(SQLReplicationTask.OBJECT_KEY, objectId)
           .delete();

        SQLReplicationTask task = new SQLReplicationTask();
        task.setPrimarySpace(primarySpace);
        task.setObjectKey(objectId);
        task.setPerformDelete(false);
        task.setContentLength(contentLength);
        task.setEarliestExecution(LocalDateTime.now().plus(replicateUpdateDelay));
        oma.update(task);
    }

    @Override
    protected SmartQuery<SQLReplicationTask> queryExecutableTasks() {
        SmartQuery<SQLReplicationTask> query = oma.select(SQLReplicationTask.class);
        query.eq(SQLReplicationTask.FAILED, false);
        query.where(OMA.FILTERS.lt(SQLReplicationTask.EARLIEST_EXECUTION, LocalDateTime.now()));
        query.eq(SQLReplicationTask.TRANSACTION_ID, null);
        query.eq(SQLReplicationTask.SCHEDULED, null);

        query.orderAsc(SQLReplicationTask.EARLIEST_EXECUTION);

        return query;
    }

    @Override
    protected void markTaskAsScheduled(SQLReplicationTask task, String txnId) {
        try {
            oma.updateStatement(SQLReplicationTask.class)
               .set(SQLReplicationTask.TRANSACTION_ID, txnId)
               .setToNow(SQLReplicationTask.SCHEDULED)
               .where(SQLReplicationTask.EARLIEST_EXECUTION, Operator.LT, LocalDateTime.now())
               .where(SQLReplicationTask.ID, task.getId())
               .executeUpdate();
        } catch (SQLException e) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .error(e)
                      .withSystemErrorMessage(
                              "Layer 1/replication: Failed to mark SQL replication task %s as scheduled: %s (%s)",
                              task.getId())
                      .handle();
        }
    }

    @Override
    public void executeBatch(JSONObject batch) {
        String txnId = batch.getString(TRANSACTION_ID);
        if (Strings.isFilled(txnId)) {
            SmartQuery<SQLReplicationTask> query = oma.select(SQLReplicationTask.class);
            query.eq(SQLReplicationTask.FAILED, false);
            query.eq(SQLReplicationTask.TRANSACTION_ID, txnId);
            query.iterateAll(this::executeTask);
        }
    }

    @Override
    public int countTotalNumberOfTasks() {
        return (int) oma.select(SQLReplicationTask.class).eq(SQLReplicationTask.FAILED, false).count();
    }

    @Override
    public int countNumberOfDelayedTasks() {
        return (int) oma.select(SQLReplicationTask.class)
                        .eq(SQLReplicationTask.FAILED, false)
                        .where(OMA.FILTERS.gte(SQLReplicationTask.EARLIEST_EXECUTION, LocalDateTime.now()))
                        .count();
    }

    @Override
    public int countNumberOfExecutableTasks() {
        return (int) oma.select(SQLReplicationTask.class)
                        .eq(SQLReplicationTask.FAILED, false)
                        .where(OMA.FILTERS.lt(SQLReplicationTask.EARLIEST_EXECUTION, LocalDateTime.now()))
                        .count();
    }

    private void executeTask(SQLReplicationTask task) {
        try {
            replicationManager.executeReplicationTask(task.getPrimarySpace(),
                                                      task.getObjectKey(),
                                                      task.getContentLength(),
                                                      task.isPerformDelete());
            oma.delete(task);
        } catch (Exception ex) {
            try {
                task = oma.refreshOrFail(task);
                task.setLastExecution(LocalDateTime.now());
                task.setEarliestExecution(LocalDateTime.now().plus(retryReplicationDelay));
                task.setFailureCounter(task.getFailureCounter() + 1);
                task.setScheduled(null);
                task.setTransactionId(null);
                if (task.getFailureCounter() > maxReplicationAttempts) {
                    task.setFailed(true);
                    Exceptions.handle()
                              .to(StorageUtils.LOG)
                              .error(ex)
                              .withSystemErrorMessage(
                                      "Layer 1/replication: A storage replication task (%s) ultimately failed: Primary space: %s, object: %s - %s (%s)",
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
