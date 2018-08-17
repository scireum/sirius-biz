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
import sirius.biz.cluster.NeighborhoodWatch;
import sirius.db.redis.Redis;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

@Register(classes = DistributedTasks.class)
public class DistributedTasks {

    private Map<String, Semaphore> concurrencyTokens = new ConcurrentHashMap<>();

    @Part
    private NeighborhoodWatch orchestration;

    @Part
    private Redis redis;

    @Part
    private GlobalContext ctx;

    private List<String> taskQueues;
    private Map<Class<? extends DistributedTaskExecutor>, String> queuePerExecutor;
    private Map<String, String> tokenPerQueue;
    private Map<String, Queue<JSONObject>> localQueues = new ConcurrentHashMap<>();
    private volatile int index = 0;

    public void submitTask(Class<? extends DistributedTaskExecutor> executor, JSONObject data) {
        String queueName = getQueueName(executor);
        data.put("_executor", executor.getName());

        if (!redis.isConfigured()) {
            Queue<JSONObject> queue = getLocalQueue(queueName);
            queue.add(data);
        } else {
            redis.pushToQueue(queueName, data.toJSONString());
        }
    }

    private Queue<JSONObject> getLocalQueue(String queueName) {
        return localQueues.computeIfAbsent(queueName, name -> new ConcurrentLinkedQueue<>());
    }

    protected String getQueueName(Class<? extends DistributedTaskExecutor> executor) {
        if (queuePerExecutor == null) {
            queuePerExecutor = ctx.getParts(DistributedTaskExecutor.class)
                                  .stream()
                                  .collect(Collectors.toMap(DistributedTaskExecutor::getClass,
                                                            DistributedTaskExecutor::queueName));
        }

        return queuePerExecutor.get(executor);
    }

    public synchronized Runnable fetchWork() {
        List<String> queues = getQueues();

        if (queues.isEmpty()) {
            return null;
        }

        int initialIndex = index;
        while (true) {
            String queue = queues.get(index++);
            if (index >= queues.size()) {
                index = 0;
            }
            if (orchestration.isDistributedTaskQueueEnabled(queue)) {
                Runnable task = tryToPullWork(queue);
                if (task != null) {
                    return task;
                }
            }

            if (initialIndex == index) {
                return null;
            }
        }
    }

    private Runnable tryToPullWork(String queue) {
        String concurrencyToken = determineConcurrencyTokenForQueue(queue);
        if (!aquireConcurrencyToken(concurrencyToken)) {
            return null;
        }

        try {
            Runnable work = pullWork(queue);
            if (work != null) {
                return work;
            }
        } catch (Exception e) {
            Exceptions.handle(e);
        }

        releaseConcurrencyToken(concurrencyToken);
        return null;
    }

    public List<String> getQueues() {
        if (taskQueues == null) {
            taskQueues = ctx.getParts(DistributedTaskExecutor.class)
                            .stream()
                            .map(DistributedTaskExecutor::queueName)
                            .distinct()
                            .collect(Collectors.toList());
        }

        return taskQueues;
    }

    private String determineConcurrencyTokenForQueue(String queueName) {
        return null;
    }

    private void releaseConcurrencyToken(String concurrencyToken) {
        if (Strings.isEmpty(concurrencyToken)) {
            return;
        }

        getSemaphore(concurrencyToken).release();
    }

    private boolean aquireConcurrencyToken(String concurrencyToken) {
        if (Strings.isEmpty(concurrencyToken)) {
            return true;
        }

        return getSemaphore(concurrencyToken).tryAcquire();
    }

    private Semaphore getSemaphore(String name) {
        return concurrencyTokens.computeIfAbsent(name, this::createSemaphore);
    }

    private Semaphore createSemaphore(String concurrencyToken) {
        return new Semaphore(1);
    }

    private Runnable pullWork(String queue) {
        JSONObject task = fetchTask(queue);

        if (task == null) {
            return null;
        }

        return () -> {
            try {
                DistributedTaskExecutor exec = ctx.getPart(task.getString("_executor"), DistributedTaskExecutor.class);
                try {
                    exec.executeWork(task);
                } finally {
                    releaseConcurrencyToken(determineConcurrencyTokenForQueue(exec.queueName()));
                }
            } catch (Exception e) {
                //TODO
            }
        };
    }

    @Nullable
    private JSONObject fetchTask(String queue) {
        if (!redis.isConfigured()) {
            return getLocalQueue(queue).poll();
        } else {
            String data = redis.pollQueue(queue);
            if (data != null) {
                return JSON.parseObject(data);
            }
            return null;
        }
    }
}
