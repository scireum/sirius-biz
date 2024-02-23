/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer1.replication.mongo;

import com.fasterxml.jackson.databind.node.ObjectNode;
import sirius.biz.storage.layer1.replication.BaseReplicationTaskStorage;
import sirius.biz.storage.util.StorageUtils;
import sirius.db.mongo.Mango;
import sirius.db.mongo.Mongo;
import sirius.db.mongo.MongoQuery;
import sirius.db.mongo.QueryBuilder;
import sirius.db.mongo.Updater;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;

import java.time.LocalDateTime;

/**
 * Provides a replication task storage based on the underlying MongoDB.
 */
@Register(framework = MongoReplicationTaskStorage.FRAMEWORK_MONGO_REPLICATION)
public class MongoReplicationTaskStorage
        extends BaseReplicationTaskStorage<MongoReplicationTask, MongoQuery<MongoReplicationTask>> {

    /**
     * Contains the name of the framework which has to be enabled to support the coordination of the
     * replication layer via MongoDB entities.
     */
    public static final String FRAMEWORK_MONGO_REPLICATION = "biz.storage-replication-mongo";

    @Part
    private Mango mango;

    @Part
    private Mongo mongo;

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
    public void notifyAboutUpdate(String primarySpace, String objectId, long contentLength) {
        mango.select(MongoReplicationTask.class)
             .eq(MongoReplicationTask.PRIMARY_SPACE, primarySpace)
             .eq(MongoReplicationTask.OBJECT_KEY, objectId)
             .delete();

        MongoReplicationTask task = new MongoReplicationTask();
        task.setPrimarySpace(primarySpace);
        task.setObjectKey(objectId);
        task.setContentLength(contentLength);
        task.setPerformDelete(false);
        task.setEarliestExecution(LocalDateTime.now().plus(replicateUpdateDelay));
        mango.update(task);
    }

    @Override
    protected MongoQuery<MongoReplicationTask> queryExecutableTasks() {
        MongoQuery<MongoReplicationTask> query = mango.select(MongoReplicationTask.class);
        query.eq(MongoReplicationTask.FAILED, false);
        query.where(QueryBuilder.FILTERS.lt(MongoReplicationTask.EARLIEST_EXECUTION, LocalDateTime.now()));
        query.eq(MongoReplicationTask.TRANSACTION_ID, null);
        query.eq(MongoReplicationTask.SCHEDULED, null);

        query.orderAsc(MongoReplicationTask.EARLIEST_EXECUTION);

        return query;
    }

    @Override
    protected void markTaskAsScheduled(MongoReplicationTask task, String txnId) {
        try {
            mongo.update()
                 .set(MongoReplicationTask.SCHEDULED, LocalDateTime.now())
                 .set(MongoReplicationTask.TRANSACTION_ID, txnId)
                 .where(MongoReplicationTask.FAILED, false)
                 .where(MongoReplicationTask.SCHEDULED, null)
                 .where(QueryBuilder.FILTERS.lt(MongoReplicationTask.EARLIEST_EXECUTION, LocalDateTime.now()))
                 .where(MongoReplicationTask.ID, task.getId())
                 .executeForOne(MongoReplicationTask.class);
        } catch (Exception e) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .error(e)
                      .withSystemErrorMessage(
                              "Layer 1/replication: Failed to mark MongoDB replication task %s as scheduled: %s (%s)",
                              task.getId())
                      .handle();
        }
    }

    @Override
    public void executeBatch(ObjectNode batch) {
        String txnId = batch.path(TRANSACTION_ID).asText();
        if (Strings.isFilled(txnId)) {
            MongoQuery<MongoReplicationTask> query = mango.select(MongoReplicationTask.class);
            query.eq(MongoReplicationTask.FAILED, false);
            query.eq(MongoReplicationTask.TRANSACTION_ID, txnId);
            query.streamBlockwise().forEach(this::executeTask);
        }
    }

    @Override
    public int countTotalNumberOfTasks() {
        return (int) mango.select(MongoReplicationTask.class).eq(MongoReplicationTask.FAILED, false).count();
    }

    @Override
    public int countNumberOfDelayedTasks() {
        return (int) mango.select(MongoReplicationTask.class)
                          .eq(MongoReplicationTask.FAILED, false)
                          .where(QueryBuilder.FILTERS.gte(MongoReplicationTask.EARLIEST_EXECUTION, LocalDateTime.now()))
                          .count();
    }

    @Override
    public int countNumberOfExecutableTasks() {
        return (int) mango.select(MongoReplicationTask.class)
                          .eq(MongoReplicationTask.FAILED, false)
                          .where(QueryBuilder.FILTERS.lt(MongoReplicationTask.EARLIEST_EXECUTION, LocalDateTime.now()))
                          .count();
    }

    private void executeTask(MongoReplicationTask task) {
        try {
            replicationManager.executeReplicationTask(task.getPrimarySpace(),
                                                      task.getObjectKey(),
                                                      task.getContentLength(),
                                                      task.isPerformDelete());
            mango.delete(task);
        } catch (Exception ex) {
            try {
                Updater updater = mongo.update()
                                       .where(MongoReplicationTask.ID, task.getId())
                                       .set(MongoReplicationTask.LAST_EXECUTION, LocalDateTime.now())
                                       .set(MongoReplicationTask.SCHEDULED, null)
                                       .set(MongoReplicationTask.TRANSACTION_ID, null)
                                       .set(MongoReplicationTask.EARLIEST_EXECUTION,
                                            LocalDateTime.now().plus(retryReplicationDelay))
                                       .inc(MongoReplicationTask.FAILURE_COUNTER, 1);
                if (!task.isPerformDelete() && fetchIsEquivalentDeletionTaskQueued(task)) {
                    updater.set(MongoReplicationTask.FAILED, true);

                    StorageUtils.LOG.WARN(
                            "Layer 1/replication: A storage replication task (%s) was marked as failed as a deletion task for the same object is already queued: Primary space: %s, object: %s",
                            task.getIdAsString(),
                            task.getPrimarySpace(),
                            task.getObjectKey());
                } else if (task.getFailureCounter() + 1 > maxReplicationAttempts) {
                    updater.set(MongoReplicationTask.FAILED, true);

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

                updater.executeForOne(MongoReplicationTask.class);
            } catch (Exception e) {
                Exceptions.handle(StorageUtils.LOG, e);
            }
        }
    }

    private boolean fetchIsEquivalentDeletionTaskQueued(MongoReplicationTask task) {
        return mango.select(MongoReplicationTask.class)
                    .eq(MongoReplicationTask.PRIMARY_SPACE, task.getPrimarySpace())
                    .eq(MongoReplicationTask.OBJECT_KEY, task.getObjectKey())
                    .eq(MongoReplicationTask.PERFORM_DELETE, true)
                    .exists();
    }
}
