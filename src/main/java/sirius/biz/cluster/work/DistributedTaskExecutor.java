/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster.work;

import com.alibaba.fastjson.JSONObject;

/**
 * Represents an executor which performs tasks which have been queued via {@link DistributedTasks}.
 * <p>
 * Note that sublasses are automatically discovered via {@link DistributedTaskExecutorLoadAction}, therefore
 * no {@link sirius.kernel.di.std.Register} annotation is required.
 */
public abstract class DistributedTaskExecutor {

    /**
     * Determines the queue in which tasks for this executor are managed and distributed.
     *
     * @return the name of the queue used by this executor
     */
    public abstract String queueName();

    /**
     * Executes the given task.
     *
     * @param context the description of the task as JSON
     * @throws Exception in case of an error while processing the task. Note that no retry will be performed for a
     * failed task.
     */
    public abstract void executeWork(JSONObject context) throws Exception;
}
