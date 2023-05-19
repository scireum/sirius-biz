/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster.work;

import com.fasterxml.jackson.databind.node.ObjectNode;
import sirius.db.redis.Redis;
import sirius.kernel.commons.Json;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Provides a FIFO queue which supports distributed concurrent access using Redis.
 */
class RedisFifoQueue implements FifoQueue {

    private final Redis redis;
    private final String queueName;

    RedisFifoQueue(Redis redis, String queueName) {
        this.redis = redis;
        this.queueName = queueName;
    }

    private String getRedisQueueName() {
        return "distributed_fifo_" + queueName;
    }

    @Override
    public void offer(@Nonnull ObjectNode task) {
        redis.pushToQueue(getRedisQueueName(), Json.write(task));
    }

    @Nullable
    @Override
    public ObjectNode poll() {
        String data = redis.pollQueue(getRedisQueueName());

        if (data != null) {
            return Json.parseObject(data);
        }

        return null;
    }

    @Override
    public int size() {
        return redis.query(() -> "Determine length of " + queueName, db -> {
            Long result = db.llen(getRedisQueueName());
            return result == null ? 0 : (int) (long) result;
        });
    }
}
