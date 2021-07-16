/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster.work;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import redis.clients.jedis.Jedis;
import sirius.db.KeyGenerator;
import sirius.db.redis.Redis;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;

/**
 * Provides a prioritized queue which supports distributed concurrent access using Redis.
 */
class RedisPrioritizedQueue implements PrioritizedQueue {

    private static final int REDIS_RESPONSE_SUCCESS = 1;
    private static final int MAX_ATTEMPTS_TO_GENERATE_UNIQUE_TASK_ID = 3;
    private static final int MAX_ATTEMPTS_TO_POLL = 5;

    private final Redis redis;
    private final String queueName;

    @Part
    private static KeyGenerator keyGen;

    RedisPrioritizedQueue(Redis redis, String queueName) {
        this.redis = redis;
        this.queueName = queueName;
    }

    @Override
    public void offer(long priority, @Nonnull JSONObject task) {
        redis.exec(() -> Strings.apply("Add to prioritized queue %s", queueName), db -> {
            String taskId = storeTask(db, task);
            db.zadd(getRedisQueueName(), priority, taskId);
        });
    }

    private String getRedisTaskMapKeyName() {
        return "distributed_prioritized_queue_map_" + queueName;
    }

    private String getRedisQueueName() {
        return "distributed_prioritized_queue_" + queueName;
    }

    private String storeTask(Jedis db, JSONObject task) {
        int retries = MAX_ATTEMPTS_TO_GENERATE_UNIQUE_TASK_ID;
        String taskMap = getRedisTaskMapKeyName();
        String taskData = task.toJSONString();
        while (retries-- > 0) {
            String id = keyGen.generateId();
            if (db.hsetnx(taskMap, id, taskData) == REDIS_RESPONSE_SUCCESS) {
                return id;
            }
        }

        throw Exceptions.handle()
                        .to(Log.BACKGROUND)
                        .withSystemErrorMessage("Failed to generate a unique task id for queue %s", queueName)
                        .handle();
    }

    @Nullable
    @Override
    public JSONObject poll() {
        String taskData = pollFromRedis();

        if (taskData != null) {
            return JSON.parseObject(taskData);
        } else {
            return null;
        }
    }

    private String pollFromRedis() {
        return redis.query(() -> Strings.apply("Poll prioritized queue %s", queueName), db -> {
            String taskMap = getRedisTaskMapKeyName();
            String queue = getRedisQueueName();

            int retries = MAX_ATTEMPTS_TO_POLL;
            while (retries-- > 0) {
                Set<String> possibleTasks = db.zrange(queue, 0, 0);
                if (possibleTasks.isEmpty()) {
                    return null;
                }

                String task = possibleTasks.iterator().next();
                if (db.zrem(queue, task) == REDIS_RESPONSE_SUCCESS) {
                    String taskData = db.hget(taskMap, task);
                    if (taskData != null) {
                        db.hdel(taskMap, task);
                        return taskData;
                    }
                }
            }

            return null;
        });
    }

    @Override
    public int size() {
        return redis.query(() -> "Determine length of " + queueName, db -> {
            Long result = db.zcard(getRedisQueueName());
            return result == null ? 0 : (int) (long) result;
        });
    }
}
