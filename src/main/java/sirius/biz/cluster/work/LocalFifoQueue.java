/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster.work;

import com.alibaba.fastjson.JSONObject;
import sirius.kernel.commons.Explain;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Provides a local (single machine) implementation for a FIFO.
 */
class LocalFifoQueue implements FifoQueue {

    private final ConcurrentLinkedQueue<JSONObject> queue = new ConcurrentLinkedQueue<>();

    @Override
    public void offer(@Nonnull JSONObject task) {
        queue.offer(task);
    }

    @Nullable
    @Override
    public JSONObject poll() {
        return queue.poll();
    }

    @Override
    @SuppressWarnings("squid:S2250")
    @Explain("This performance hotspot is acceptable as this is only a monitoring API")
    public int size() {
        return queue.size();
    }
}
