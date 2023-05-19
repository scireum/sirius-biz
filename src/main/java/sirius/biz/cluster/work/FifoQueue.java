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
 * Represents a FIFO (first in first out) queue for work items
 */
interface FifoQueue {

    /**
     * Adds a work item to the queue
     *
     * @param task the task description to add to the queue
     */
    void offer(@Nonnull ObjectNode task);

    /**
     * Polls the "oldest" work item from the queue.
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
