/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer1.replication.jdbc;

import sirius.db.jdbc.SQLEntity;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.Index;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.kernel.di.std.Framework;

import java.time.LocalDateTime;

/**
 * Represents a replication task as SQL entity.
 */
@Framework(SQLReplicationTaskStorage.FRAMEWORK_JDBC_REPLICATION)
@Index(name = "lookup_index", columns = {"primarySpace", "objectKey", "earliestExecution", "scheduled", "failed"})
public class SQLReplicationTask extends SQLEntity {

    /**
     * Contains the space in which the primary object has been stored.
     */
    public static final Mapping PRIMARY_SPACE = Mapping.named("primarySpace");
    @Length(50)
    private String primarySpace;

    /**
     * Contains the key of the object to replicate.
     */
    public static final Mapping OBJECT_KEY = Mapping.named("objectKey");
    @Length(50)
    private String objectKey;

    /**
     * Contains the expected content length in case of an update.
     * <p>
     * This will be set to 0 if the length is unknown (if a transformer is used).
     */
    public static final Mapping CONTENT_LENGTH = Mapping.named("contentLength");
    private long contentLength = 0;

    /**
     * Determines the earliest expected execution of this task.
     */
    public static final Mapping EARLIEST_EXECUTION = Mapping.named("earliestExecution");
    private LocalDateTime earliestExecution;

    /**
     * Counts how often this task failed already.
     */
    public static final Mapping FAILURE_COUNTER = Mapping.named("failureCounter");
    private int failureCounter;

    /**
     * Contains the timestamp of the last execution attempt.
     */
    public static final Mapping LAST_EXECUTION = Mapping.named("lastExecution");
    @NullAllowed
    private LocalDateTime lastExecution;

    /**
     * Contains when the task was scheduled for execution.
     */
    public static final Mapping SCHEDULED = Mapping.named("scheduled");
    @NullAllowed
    private LocalDateTime scheduled;

    /**
     * Contains the batch or transaction identifier used to schedule this task.
     */
    public static final Mapping TXN_IN = Mapping.named("txnId");
    @Length(50)
    @NullAllowed
    private String txnId;

    /**
     * Determines if a delete or an update should be performed.
     */
    public static final Mapping PERFORM_DELETE = Mapping.named("performDelete");
    private boolean performDelete;

    /**
     * Stores if this task has ultimatively failed.
     */
    public static final Mapping FAILED = Mapping.named("failed");
    private boolean failed;

    public String getPrimarySpace() {
        return primarySpace;
    }

    public void setPrimarySpace(String primarySpace) {
        this.primarySpace = primarySpace;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public LocalDateTime getEarliestExecution() {
        return earliestExecution;
    }

    public void setEarliestExecution(LocalDateTime earliestExecution) {
        this.earliestExecution = earliestExecution;
    }

    public int getFailureCounter() {
        return failureCounter;
    }

    public void setFailureCounter(int failureCounter) {
        this.failureCounter = failureCounter;
    }

    public LocalDateTime getScheduled() {
        return scheduled;
    }

    public void setScheduled(LocalDateTime scheduled) {
        this.scheduled = scheduled;
    }

    public LocalDateTime getLastExecution() {
        return lastExecution;
    }

    public void setLastExecution(LocalDateTime lastExecution) {
        this.lastExecution = lastExecution;
    }

    public boolean isPerformDelete() {
        return performDelete;
    }

    public void setPerformDelete(boolean performDelete) {
        this.performDelete = performDelete;
    }

    public boolean isFailed() {
        return failed;
    }

    public void setFailed(boolean failed) {
        this.failed = failed;
    }

    public long getContentLength() {
        return contentLength;
    }

    public void setContentLength(long contentLength) {
        this.contentLength = contentLength;
    }

    public String getTxnId() {
        return txnId;
    }

    public void setTxnId(String txnId) {
        this.txnId = txnId;
    }
}
