/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer1.replication;

import com.fasterxml.jackson.databind.node.ObjectNode;
import sirius.biz.cluster.work.DistributedTaskExecutor;
import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.di.std.Framework;
import sirius.kernel.di.std.Part;

import javax.annotation.Nullable;

/**
 * Responsible to transfering batches of replication tasks back to
 * {@link ReplicationTaskStorage#executeBatch(ObjectNode)}.
 *
 * @see ReplicationTaskStorage
 */
@Framework(StorageUtils.FRAMEWORK_STORAGE)
public class ReplicationTaskExecutor implements DistributedTaskExecutor {

    /**
     * Contains the name of the queue used for replication tasks.
     */
    public static final String REPLICATION_TASK_QUEUE = "storage-layer1-replication";

    @Part
    @Nullable
    private ReplicationTaskStorage taskStorage;

    @Override
    public String queueName() {
        return REPLICATION_TASK_QUEUE;
    }

    @Override
    public void executeWork(ObjectNode context) throws Exception {
        taskStorage.executeBatch(context);
    }
}
