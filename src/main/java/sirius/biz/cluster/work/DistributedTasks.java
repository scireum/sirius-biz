/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster.work;

import com.fasterxml.jackson.databind.node.ObjectNode;
import sirius.biz.cluster.NeighborhoodWatch;
import sirius.db.redis.Redis;
import sirius.kernel.Sirius;
import sirius.kernel.async.AsyncExecutor;
import sirius.kernel.async.Tasks;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Json;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.kernel.health.metrics.MetricProvider;
import sirius.kernel.health.metrics.MetricsCollector;
import sirius.kernel.settings.Extension;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Main helper class for the <tt>Distributed Tasks Framework</tt>.
 * <p>
 * This framework permits to enqueue and execute tasks in a distributed manner. Therefore
 * {@link DistributedTaskExecutor executors} are implemented and automatically discovered by the system
 * (via {@link DistributedTaskExecutorLoadAction}). When and where those tasks are executed is controlled
 * via the system configuration in <tt>async.distributed</tt>. See <tt>component-biz.conf</tt> for examples.
 * <p>
 * Also, a queue can be globally or locally disabled via {@link NeighborhoodWatch} and its configuration.
 * <p>
 * This framework supports two type of queues. Simple FIFOs which execute tasks in the order they are
 * scheduled. Additionally, prioritized queues are supported. For these queues a <tt>penalty token</tt>
 * per task is supplied (e.g. a user or tenant id). The system then counts the number of already queued
 * tasks for this token and computes a penalty time. Now if now other tasks are queued, a task with a
 * penalty time applied will still be immediatelly executed. However, as soon as other tasks are scheduled
 * a task might be delayed up until its penalty time is over. Currently, the penalty time is a static
 * value set in the system configuration and multiplied by the number of already queued tasks. Therefore, this
 * should be roughly equal to the estimated execution time.
 * <p>
 * Use this helper via an {@link Part} annotation.
 * <p>
 * Note that this implementation will use <tt>Redis</tt> for distributed environments but provides a local fallback
 * if no redis config is present.
 */
@Register(classes = {DistributedTasks.class, MetricProvider.class})
public class DistributedTasks implements MetricProvider {

    private static final String KEY_EXECUTOR = "_executor";
    private static final String KEY_PENALTY_TOKEN = "_penalty_token";

    /**
     * Contains the logger used to report everything related to distributed tasks.
     */
    public static final Log LOG = Log.get("distributed-tasks");

    @Part
    private NeighborhoodWatch orchestration;

    @Part
    private Redis redis;

    @Part
    private Tasks tasks;

    @Part
    private static GlobalContext ctx;

    /**
     * Contains a list of all known queues loaded from the system config.
     */
    private List<DistributedQueueInfo> sortedTaskQueues;

    /**
     * Contains all known queues by their name.
     */
    private Map<String, DistributedQueueInfo> taskQueues;

    /**
     * Remembers which executor runs in which queue.
     */
    private Map<Class<? extends DistributedTaskExecutor>, String> queuePerExecutor;

    /**
     * Contains the concurrency tokens which limit the parallelism of one or more queues.
     */
    private final Map<String, Semaphore> concurrencyTokens = new ConcurrentHashMap<>();

    /**
     * Contains the index of the queue in {@link #sortedTaskQueues} to pull work from.
     * <p>
     * Work is pulled in a round-robin fasion and this index contains the next queue to try to poll.
     */
    private final AtomicInteger nextQueueToFetchWorkFrom = new AtomicInteger(0);

    /**
     * Contains all FIFO queues by their name.
     */
    private final Map<String, FifoQueue> fifos = new ConcurrentHashMap<>();

    /**
     * Contains all prioritized queues by their name.
     */
    private final Map<String, PrioritizedQueue> prioritizedQueues = new ConcurrentHashMap<>();

    /**
     * Contains the counters for each penalty token used by prioritized queues.
     */
    private NamedCounters penaltyTokens;

    public int getNumberOfActiveTasks() {
        return getLocalExecutor().getActiveCount();
    }

    protected AsyncExecutor getLocalExecutor() {
        return tasks.executorService("distributed-tasks");
    }

    /**
     * Represents an executable task.
     * <p>
     * This is basically the call to the appropriate {@link DistributedTaskExecutor} along with some bookkeeping.
     */
    protected class DistributedTask {

        private final DistributedQueueInfo queue;
        private final ObjectNode task;

        protected DistributedTask(DistributedQueueInfo queue, ObjectNode task) {
            this.queue = queue;
            this.task = task;
        }

        protected void execute() {
            try {
                DistributedTaskExecutor exec =
                        ctx.getPart(task.required(KEY_EXECUTOR).asText(), DistributedTaskExecutor.class);
                tryExecute(exec);
            } catch (Exception exception) {
                Exceptions.handle()
                          .to(Log.BACKGROUND)
                          .error(exception)
                          .withSystemErrorMessage("The system failed to execute the DistributedTask '%s' with: %s (%s)",
                                                  Json.write(task));
            }
        }

        private void tryExecute(DistributedTaskExecutor exec) {
            try {
                exec.executeWork(task);
            } catch (Exception exception) {
                Exceptions.handle()
                          .to(Log.BACKGROUND)
                          .error(exception)
                          .withSystemErrorMessage(
                                  "The DistributedTaskExecutor '%s' failed with: %s (%s) for the task '%s'",
                                  exec.getClass().getName(),
                                  Json.write(task))
                          .handle();
            } finally {
                releasePenaltyToken(queue.getName(), task.path(KEY_PENALTY_TOKEN).asText(null));
                releaseConcurrencyToken(queue.getConcurrencyToken());
            }
        }

        /**
         * Releases the penalty token if one was acquired.
         *
         * @param queue        the queue to release the token for
         * @param penaltyToken the token to release
         */
        private void releasePenaltyToken(@Nonnull String queue, @Nullable String penaltyToken) {
            try {
                if (Strings.isFilled(penaltyToken) && penaltyTokens != null) {
                    penaltyTokens.decrementAndGet(queue + "-" + penaltyToken);
                }
            } catch (Exception exception) {
                Exceptions.handle(Log.BACKGROUND, exception);
            }
        }
    }

    /**
     * Enumerates all known queues and their configuration.
     *
     * @return a list of all known queues.
     */
    @SuppressWarnings("java:S3958")
    @Explain("toList is a terminal operation")
    public List<DistributedQueueInfo> getQueues() {
        if (sortedTaskQueues == null) {
            sortedTaskQueues = ctx.getParts(DistributedTaskExecutor.class)
                                  .stream()
                                  .map(DistributedTaskExecutor::queueName)
                                  .distinct()
                                  .map(this::loadQueueInfo)
                                  .toList();
            taskQueues = sortedTaskQueues.stream()
                                         .collect(Collectors.toMap(DistributedQueueInfo::getName, Function.identity()));
        }

        return Collections.unmodifiableList(sortedTaskQueues);
    }

    /**
     * Loads the configuration of the given queue by reading <tt>async.distributed.queues.[queueName]</tt>
     *
     * @param queueName the name of the queue to load the config for
     * @return the configuration for the given queue
     */
    private DistributedQueueInfo loadQueueInfo(String queueName) {
        Extension config = Sirius.getSettings().getExtension("async.distributed.queues", queueName);
        if (config == null || config.isDefault()) {
            LOG.WARN("Missing configuration for queue: %s", queueName);
            return new DistributedQueueInfo(queueName, null, null);
        }

        return new DistributedQueueInfo(queueName,
                                        config.get("concurrencyToken").asString(),
                                        config.get("prioritized").asBoolean() ?
                                        Duration.ofMillis(config.getMilliseconds("penaltyTime")) :
                                        null);
    }

    /**
     * Submits a task for the given executor by expecting the associated queue to be a FIFO queue.
     * <p>
     * Note that the supplied <tt>data</tt> will be serialized and therefore should be considerably small.
     *
     * @param executor the executor which will eventually execute the work item.
     * @param data     the data to supply to the executor
     */
    public void submitFIFOTask(Class<? extends DistributedTaskExecutor> executor, ObjectNode data) {
        String queueName = getQueueName(executor);

        DistributedQueueInfo info = getQueueInfo(queueName);

        if (info.isPrioritized()) {
            throw Exceptions.handle()
                            .to(Log.BACKGROUND)
                            .withSystemErrorMessage("The queue '%s' is prioritized and not a FIFO queue!", queueName)
                            .handle();
        }

        data.put(KEY_EXECUTOR, executor.getName());

        getFifo(queueName).offer(data);
    }

    protected FifoQueue getFifo(String queueName) {
        return fifos.computeIfAbsent(queueName, this::createFifo);
    }

    /**
     * Determines the queue to used for the given executor.
     *
     * @param executor the executor to determine the queue for
     * @return the queue used by this executor
     */
    public String getQueueName(Class<? extends DistributedTaskExecutor> executor) {
        if (queuePerExecutor == null) {
            queuePerExecutor = ctx.getParts(DistributedTaskExecutor.class)
                                  .stream()
                                  .collect(Collectors.toMap(DistributedTaskExecutor::getClass,
                                                            DistributedTaskExecutor::queueName));
        }

        return queuePerExecutor.get(executor);
    }

    /**
     * Feteches the config for the given queue.
     *
     * @param queueName the name of the queue to fetch the config for
     * @return the config of the given queue
     * @throws sirius.kernel.health.HandledException if the queue is unknown
     */
    public DistributedQueueInfo getQueueInfo(@Nonnull String queueName) {
        if (taskQueues == null) {
            getQueues();
        }

        DistributedQueueInfo queueInfo = taskQueues.get(queueName);
        if (queueInfo == null) {
            throw Exceptions.handle()
                            .to(Log.BACKGROUND)
                            .withSystemErrorMessage("Unknown queue: %s", queueName)
                            .handle();
        }

        return queueInfo;
    }

    /**
     * Returns the number of tasks in the given queue.
     *
     * @param queueName the name of the queue to return the number of waiting tasks for
     * @return the number of tasks in the given queue
     * @throws sirius.kernel.health.HandledException if the queue is unknown
     */
    public int getQueueLength(@Nonnull String queueName) {
        DistributedQueueInfo queueInfo = getQueueInfo(queueName);

        if (queueInfo.isPrioritized()) {
            return getPrioritizedQueue(queueName).size();
        } else {
            return getFifo(queueName).size();
        }
    }

    /**
     * Creates a new FIFO queue.
     *
     * @param queue the queue to create
     * @return a <tt>Redis</tt> based implementation or a local one if no configuration for redis is present
     */
    private FifoQueue createFifo(String queue) {
        if (redis.isConfigured()) {
            return new RedisFifoQueue(redis, queue);
        } else {
            return new LocalFifoQueue();
        }
    }

    /**
     * Submits a task for the given executor by expecting the associated queue to be a prioritized queue.
     * <p>
     * Note that the supplied <tt>data</tt> will be serialized and therefore should be considerably small.
     *
     * @param executor     the executor which will eventually execute the work item
     * @param penaltyToken the penalty token which is used to compute the penalty time for the task which is
     *                     {@code NumberOfQueuedTasks * PenaltyTimeOfQueue}
     * @param data         the data to supply to the executor
     */
    public void submitPrioritizedTask(Class<? extends DistributedTaskExecutor> executor,
                                      String penaltyToken,
                                      ObjectNode data) {
        String queueName = getQueueName(executor);
        DistributedQueueInfo info = getQueueInfo(queueName);

        if (!info.isPrioritized()) {
            throw Exceptions.handle()
                            .to(Log.BACKGROUND)
                            .withSystemErrorMessage("The queue '%s' is a FIFO and not a prioritized queue!", queueName)
                            .handle();
        }

        long priority = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
        long penalty = acquirePenaltyToken(queueName, penaltyToken);

        priority += penalty * info.getPenaltyTimeSeconds();

        data.put(KEY_EXECUTOR, executor.getName());
        data.put(KEY_PENALTY_TOKEN, penaltyToken);

        getPrioritizedQueue(queueName).offer(priority, data);
    }

    protected PrioritizedQueue getPrioritizedQueue(String queueName) {
        return prioritizedQueues.computeIfAbsent(queueName, this::createPrioritizedQueue);
    }

    /**
     * Acquires a penalty token.
     * <p>
     * This essentially increments the counter for the given token and queue and returns the new counter value.
     * This is used to compute the penalty time for a prioritized task.
     *
     * @param queue        the queue to acquire the token for
     * @param penaltyToken the token to acquire
     * @return the new counter value (current active acquisitions for this token)
     */
    private long acquirePenaltyToken(@Nonnull String queue, @Nonnull String penaltyToken) {
        if (penaltyTokens == null) {
            initializePenaltyTokens();
        }
        return penaltyTokens.incrementAndGet(queue + "-" + penaltyToken);
    }

    private synchronized void initializePenaltyTokens() {
        if (penaltyTokens != null) {
            return;
        }

        if (redis.isConfigured()) {
            penaltyTokens = new RedisNamedCounters("distributed_tasks_penalty_tokens", redis);
        } else {
            penaltyTokens = new LocalNamedCounters();
        }
    }

    /**
     * Creates a new prioritized queue.
     *
     * @param queueName the queue to create
     * @return a <tt>Redis</tt> based implementation or a local one if no configuration for redis is present
     */
    private PrioritizedQueue createPrioritizedQueue(String queueName) {
        if (redis.isConfigured()) {
            return new RedisPrioritizedQueue(redis, queueName);
        } else {
            return new LocalPrioritizedQueue(queueName);
        }
    }

    /**
     * Fetches a work item form any available queue.
     * <p>
     * Iterates over {@link #getQueues()} using a round-robin approach via {@link #fetchAndMoveNextQueueIndex()}
     * and tries to poll each queue up until an executable work item is found.
     * <p>
     * Queues which require a <tt>concurrencyToken</tt> which is already exhausted are skipped.
     *
     * @return a work item wrapped as optional or an empty optional to indicate that currently no executable work was
     * found.
     */
    protected Optional<DistributedTask> fetchWork() {
        List<DistributedQueueInfo> queues = getQueues();

        if (queues.isEmpty()) {
            return Optional.empty();
        }

        int initialIndex = fetchNextQueueIndex();
        while (true) {
            DistributedQueueInfo queue = queues.get(fetchAndMoveNextQueueIndex());
            if (orchestration.isDistributedTaskQueueEnabled(queue.getName())) {
                DistributedTask task = tryToPullWork(queue);
                if (task != null) {
                    return Optional.of(task);
                }
            }

            if (initialIndex == fetchNextQueueIndex()) {
                return Optional.empty();
            }
        }
    }

    /**
     * Reads the round-robin index for pulling work from {@link #sortedTaskQueues}.
     *
     * @return the current index to poll for work
     */
    private int fetchNextQueueIndex() {
        return nextQueueToFetchWorkFrom.get();
    }

    /**
     * Reads and increments the index for pulling work.
     * <p>
     * This will also wrap the index back to 0 if it reached the number of queues.
     *
     * @return the current index to poll work from
     */
    private int fetchAndMoveNextQueueIndex() {
        int result = nextQueueToFetchWorkFrom.getAndIncrement();
        if (nextQueueToFetchWorkFrom.get() >= getQueues().size()) {
            nextQueueToFetchWorkFrom.set(0);
        }

        return result;
    }

    /**
     * Tries to pull a work item from the given queue.
     *
     * @param queue the queue to pull a work item from
     * @return a work item or <tt>null</tt> to indicate that either there is no work, or that the required
     * <tt>concurrencyToken</tt> is exhausted on this machine.
     */
    @Nullable
    private DistributedTask tryToPullWork(DistributedQueueInfo queue) {
        if (!acquireConcurrencyToken(queue.getConcurrencyToken())) {
            return null;
        }

        try {
            ObjectNode task = fetchTask(queue);

            if (task != null) {
                return new DistributedTask(queue, task);
            }
        } catch (Exception exception) {
            Exceptions.handle(LOG, exception);
        }

        // Release the concurrency token acquired above, as we didn't yield any task...
        releaseConcurrencyToken(queue.getConcurrencyToken());
        return null;
    }

    /**
     * Acquires a slot for the given concurrency token.
     * <p>
     * This limits local concurrency for one or more queues to a certain value.
     *
     * @param concurrencyToken the concurrency token to acquire
     * @return <tt>true</tt> if the token has been acquired, <tt>false</tt> otherwise
     */
    private boolean acquireConcurrencyToken(@Nullable String concurrencyToken) {
        if (Strings.isEmpty(concurrencyToken)) {
            return true;
        }

        return getSemaphore(concurrencyToken).tryAcquire();
    }

    /**
     * Releases a slot for the given concurrency token.
     *
     * @param concurrencyToken the concurrency token to release
     */
    private void releaseConcurrencyToken(@Nullable String concurrencyToken) {
        if (Strings.isEmpty(concurrencyToken)) {
            return;
        }

        getSemaphore(concurrencyToken).release();
    }

    /**
     * Returns the semaphore which represents the given concurrency token.
     *
     * @param name the name of the concurrency token
     * @return the semaphore representing the state and configuration of the given concurrency token.
     */
    private Semaphore getSemaphore(String name) {
        return concurrencyTokens.computeIfAbsent(name, this::createSemaphore);
    }

    /**
     * Creates a new semaphore for the given token.
     *
     * @param concurrencyToken the token to create a semaphore for
     * @return a semaphore representing the configuration of the given concurrency token
     */
    private Semaphore createSemaphore(String concurrencyToken) {
        int maxConcurrency = Sirius.getSettings().get("async.distributed.concurrency." + concurrencyToken).asInt(1);
        return new Semaphore(maxConcurrency);
    }

    /**
     * Tries to fetch a description of a work item from the given queue.
     * <p>
     * This method assumes that a <tt>concurrencyToken</tt> (if required by the queue) has already been acquired.
     *
     * @param queue the queue to fetch a task description from
     * @return a JSON object representing a description of an executable task or <tt>null</tt> to indicate that there
     * is no work available
     */
    @Nullable
    private ObjectNode fetchTask(DistributedQueueInfo queue) {
        if (queue.isPrioritized()) {
            PrioritizedQueue prioritizedQueue = prioritizedQueues.get(queue.getName());
            if (prioritizedQueue == null) {
                return null;
            } else {
                return prioritizedQueue.poll();
            }
        } else {
            FifoQueue fifoQueue = fifos.get(queue.getName());
            if (fifoQueue == null) {
                return null;
            } else {
                return fifoQueue.poll();
            }
        }
    }

    @Override
    public void gather(MetricsCollector metricsCollector) {
        metricsCollector.metric("active-distributed-tasks",
                                "active-distributed-tasks",
                                "Active Distributed Tasks",
                                getLocalExecutor().getActiveCount(),
                                null);
    }
}
