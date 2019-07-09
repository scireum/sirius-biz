/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer1.replication;

import com.alibaba.fastjson.JSONObject;
import sirius.biz.cluster.work.DistributedTaskExecutor;
import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.di.std.Framework;
import sirius.kernel.di.std.Part;

/**
 * Responsible to transfering batches of replication tasks back to
 * {@link ReplicationTaskStorage#executeBatch(JSONObject)}.
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
    private ReplicationTaskStorage taskStorage;

    @Override
    public String queueName() {
        return REPLICATION_TASK_QUEUE;
    }

    @Override
    public void executeWork(JSONObject context) throws Exception {
        taskStorage.executeBatch(context);
    }
}
