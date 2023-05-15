/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster.work;

import com.fasterxml.jackson.databind.node.ObjectNode;
import sirius.kernel.commons.ComparableTuple;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Provides a local (single machine) implementation for a prioritized queue.
 */
class LocalPrioritizedQueue implements PrioritizedQueue {

    private final String queueName;
    private final PriorityBlockingQueue<ComparableTuple<Long, ObjectNode>> queue = new PriorityBlockingQueue<>();

    LocalPrioritizedQueue(String queueName) {
        this.queueName = queueName;
    }

    @Override
    public void offer(long priority, @Nonnull ObjectNode task) {
        if (!queue.offer(ComparableTuple.create(priority, task))) {
            throw Exceptions.handle()
                            .to(Log.BACKGROUND)
                            .withSystemErrorMessage("The queue '%s' refused to accept '%s' as task", queueName, task)
                            .handle();
        }
    }

    @Nullable
    @Override
    public ObjectNode poll() {
        ComparableTuple<Long, ObjectNode> head = queue.poll();
        if (head != null) {
            return head.getSecond();
        } else {
            return null;
        }
    }

    @Override
    public int size() {
        return queue.size();
    }
}
