/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster.work;

import javax.annotation.Nullable;
import java.time.Duration;

/**
 * Describes the configuration of a queue used by {@link DistributedTasks}.
 */
public class DistributedQueueInfo {

    private final String name;
    private final String concurrencyToken;
    private final Duration penaltyTime;

    protected DistributedQueueInfo(String queueName, String concurrencyToken, Duration penaltyTime) {
        this.name = queueName;
        this.concurrencyToken = concurrencyToken;
        this.penaltyTime = penaltyTime;
    }

    /**
     * Returns the name of the queue.
     *
     * @return the name of the queue
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the concurrency token used by the queue to limit the local parallelism on each node.
     * <p>
     * Note that a token can be shared by multiple queues.
     *
     * @return the token used by this queue
     */
    @Nullable
    public String getConcurrencyToken() {
        return concurrencyToken;
    }

    /**
     * Determines if this queue is prioritized or a FIFO.
     *
     * @return <tt>true</tt> if this queue is prioritized or <tt>false</tt> if the queue is a FIFO
     */
    public boolean isPrioritized() {
        return penaltyTime != null;
    }

    /**
     * Returns the penalty time in seconds.
     *
     * @return the penalty time in seconds (estimated execution time of a single task) or 0 for a FIFO queue
     */
    public long getPenaltyTimeSeconds() {
        return penaltyTime == null ? 0 : penaltyTime.getSeconds();
    }
}
