/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.scheduler;

import com.alibaba.fastjson.JSONObject;
import sirius.kernel.di.std.Named;

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
 * is forwarded to {@link #collectBatch(JSONObject, Consumer)} which collects and emits all entities of the specified
 * batch to the effective executor of the analyical tasks.
 *
 * @param <E> the type of entities being scheduled
 */
public interface AnalyticsScheduler<E> extends Named {

    /**
     * Determines if <b>best effort</b> scheduling is used for the tasks which are associated with this scheduler.
     * <p>
     * Best effort schedulers are only invoked (and thus their tasks scheduled) if the appropriate task queue
     * ({@link AnalyticalEngine#QUEUE_ANALYTICS_BEST_EFFORT}) is empty. Therefore, if the system is overloaded,
     * executions are simply skipped instead of adding work on top.
     *
     * @return <tt>true</tt> if best effort execution is to be used, <tt>false</tt> if the execution must be
     * guaranteed
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
     * {@link #collectBatch(JSONObject, Consumer)}.
     * <p>
     * Note that the emitted JSON should be small - i.e. not contain a list or IDs but rather a start and end filter.
     *
     * @param batchConsumer the consumer used to process the emitted batches
     */
    void scheduleBatches(Consumer<JSONObject> batchConsumer);

    /**
     * Resolves a batch emitted by {@link #scheduleBatches(Consumer)} and emits all entities within this batch into
     * the given consumer.
     *
     * @param batchDescription the description which specifies which entities are in the batch
     * @param entityConsumer   the consumer used to process each entity in the batch
     */
    void collectBatch(JSONObject batchDescription, Consumer<E> entityConsumer);
}
