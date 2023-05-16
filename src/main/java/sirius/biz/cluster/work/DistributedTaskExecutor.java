/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster.work;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Represents an executor which performs tasks which have been queued via {@link DistributedTasks}.
 * <p>
 * Note that sublasses are automatically discovered via {@link DistributedTaskExecutorLoadAction}, therefore
 * no {@link sirius.kernel.di.std.Register} annotation is required.
 */
public interface DistributedTaskExecutor {

    /**
     * Determines the queue in which tasks for this executor are managed and distributed.
     *
     * @return the name of the queue used by this executor
     */
    String queueName();

    /**
     * Executes the given task.
     *
     * @param context the description of the task as JSON
     * @throws Exception in case of an error while processing the task. Note that no retry will be performed for a
     *                   failed task.
     */
    void executeWork(ObjectNode context) throws Exception;
}
