/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster.work;

import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Represents a prioritized queue for work items.
 * <p>
 * As {@link DistributedTasks} uses timestamps add penalty addons as priority, this queue sorts ascendingly. Therefore
 * the task with the lowest priority value will be returned next.
 */
interface PrioritizedQueue {

    /**
     * Adds a work item with the given priority.
     *
     * @param priority the priority of this work item
     * @param task     the task description of the work item
     */
    void offer(long priority, @Nonnull ObjectNode task);

    /**
     * Pulls the next work item, which is the one with the lowest priority.
     *
     * @return the work item to process or <tt>null</tt> to indicate that the queue is empty
     */
    @Nullable
    ObjectNode poll();

    /**
     * Returns the number of elements in the queue.
     *
     * @return the number of elements in the queue
     */
    int size();
}
