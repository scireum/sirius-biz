/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.scheduler;

import com.alibaba.fastjson.JSONObject;
import sirius.kernel.di.std.AutoRegister;
import sirius.kernel.di.std.Named;

import java.time.LocalDate;
import java.util.function.Consumer;

/**
 * In charge of computing batches of entities which are then used to execute {@link AnalyticalTask analytical tasks} on
 * them.
 * <p>
 * As most {@link AnalyticalTask tasks} are execute rather quickly, scheduling each single one of them provides
 * quite an overhead for the distributed tasks framework. Therefore each scheduler computes batches of entities which
 * are then scheduled and executed at once.
 * <p>
 * This is a two stage process, {@link #scheduleBatches(Consumer)} emits a list of JSON specification which can
 * be used as descriptions in {@link sirius.biz.cluster.work.DistributedTasks}. Once being executed, the description
 * is forwarded to {@link #executeBatch(JSONObject, LocalDate)} which collects all entities of the specified
 * batch. In most implementations the scheduler will then pick all matching {@link AnalyticalTask analytical tasks} and
 * executes them on the entity.
 */
@AutoRegister
public interface AnalyticsScheduler extends Named {

    /**
     * Determines the executor to be used to invoke {@link #scheduleBatches(Consumer)}.
     *
     * @return the executor which is in charge of scheduling new batches
     */
    Class<? extends AnalyticsSchedulerExecutor> getExecutorForScheduling();

    /**
     * Determines the executor to be used to invoke {@link #executeBatch(JSONObject, LocalDate)}.
     *
     * @return the executor which is in charge of executing the scheduled batches
     */
    Class<? extends AnalyticsBatchExecutor> getExecutorForTasks();

    /**
     * Determines if this scheduler uses a "best effort" approach.
     * <p>
     * <b>Best effort</b> schedulers will only schedule new batches of tasks if the underlying queue is empty. To
     * ensure proper behaviour, the {@link #getExecutorForScheduling() scheduler executor} and the
     * {@link #getExecutorForTasks() task executor} should use the same queue.
     *
     * @return <tt>true</tt> to enable "best effort" scheduling, <tt>false</tt> for guaranteed execution
     */
    boolean useBestEffortScheduling();

    /**
     * Determines the execution interval of this scheduler.
     *
     * @return the interval in which this scheduler is executed
     */
    ScheduleInterval getInterval();

    /**
     * Emits several JSON objects which each describe a batch of appropriate size to be resolved by
     * {@link #executeBatch(JSONObject, LocalDate))}.
     * <p>
     * Note that the emitted JSON should be small - i.e. not contain a list or IDs but rather a start and end filter.
     *
     * @param batchConsumer the consumer used to process the emitted batches
     */
    void scheduleBatches(Consumer<JSONObject> batchConsumer);

    /**
     * Resolves a batch emitted by {@link #scheduleBatches(Consumer)} and executes all appropriate work for all
     * entities within this batch.
     *
     * @param batchDescription the description which specifies which entities are in the batch
     * @param date             the date for which the execution was scheduled
     */
    void executeBatch(JSONObject batchDescription, LocalDate date);

    /**
     * Determines if this scheduler is active.
     * <p>
     * There is no point in executing a scheduler for which no active {@link AnalyticalTask tasks} are present.
     *
     * @return <tt>true</tt> if the scheduler should be actively scheduled or <tt>false</tt> if it can be ignored
     */
    boolean isActive();
}
