/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.scheduler;

import com.alibaba.fastjson.JSONObject;
import sirius.biz.analytics.flags.ExecutionFlags;
import sirius.biz.cluster.work.DistributedTaskExecutor;
import sirius.biz.cluster.work.DistributedTasks;
import sirius.kernel.async.Tasks;
import sirius.kernel.di.PartCollection;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Parts;
import sirius.kernel.di.std.Register;
import sirius.kernel.timer.EveryDay;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Executes on a daily basis and invokes all {@link AnalyticsScheduler schedulers} (daily or once per month).
 * <p>
 * These schedulers can then create batches of tasks to be executed. Most commonly this will either
 * be a subclass of {@link MongoEntityBatchEmitter} or {@link SQLEntityBatchEmitter}. In most cases it is
 * advised to even subclass {@link MongoAnalyticalTaskScheduler} or {@link SQLAnalyticalTaskScheduler} for a specific
 * subclass of {@link AnalyticalTask}.
 * <p>
 * If a scheduler uses {@link AnalyticsScheduler#useBestEffortScheduling()} it is only executed if its
 * underlying queue is empty - to prevent the system from overloading itself.
 * <p>
 * Consult the <a href="README.md">framework description</a> for a complete overview of the inner workings.
 */
@Register
public class AnalyticalEngine implements EveryDay {

    /**
     * Specifies the field of the task context which contains the scheduler to execute.
     */
    public static final String CONTEXT_SCHEDULER_NAME = "scheduler";

    /**
     * Specifies the field of the task context which specifies the date for which the scheduler (and its tasks)
     * should be executed.
     */
    public static final String CONTEXT_DATE = "date";

    /**
     * Contains the prefix added to the scheduler name to use it as a reference in {@link ExecutionFlags}.
     */
    private static final String EXECUTION_FLAG_PREFIX = "_SCHEDULER-";

    /**
     * Contains the flag name used for schedulers in {@link ExecutionFlags}.
     */
    private static final String EXECUTION_FLAG = "executed";

    @Part
    private Tasks tasks;

    @Part
    private DistributedTasks cluster;

    @Part
    private ExecutionFlags flags;

    @Parts(AnalyticsScheduler.class)
    private PartCollection<AnalyticsScheduler> schedulers;

    @Override
    public String getConfigKeyName() {
        return "analytical-engine";
    }

    @Override
    public void runTimer() throws Exception {
        tasks.defaultExecutor().start(this::queueSchedulers);
    }

    /**
     * Iterates over all known schedulers and, if scheduling is advised, queues an appropriate entry in the
     * underlying {@link AnalyticsScheduler#getExecutorForScheduling() scheduling queue}.
     */
    private void queueSchedulers() {
        Map<String, Boolean> queueCache = new HashMap<>();
        schedulers.getParts()
                  .stream()
                  .filter(scheduler -> shouldSchedule(scheduler, queueCache))
                  .forEach(this::queueScheduler);
    }

    /**
     * Determines if the scheduler either guarantees execution or if the underlying queue was empty as the start of the
     * scheduling run.
     *
     * @param scheduler  the scheduler to check
     * @param queueCache the cached state of the queues being used by the schedulers
     * @return <tt>true</tt> if the scheduler should run, <tt>false</tt> otherwise
     */
    private boolean shouldSchedule(AnalyticsScheduler scheduler, Map<String, Boolean> queueCache) {
        if (!scheduler.useBestEffortScheduling()) {
            return true;
        }

        return queueCache.computeIfAbsent(cluster.getQueueName(scheduler.getExecutorForScheduling()),
                                          this::checkIfQueueIsEmpty);
    }

    private Boolean checkIfQueueIsEmpty(String queueName) {
        return cluster.getQueueLength(queueName) == 0;
    }

    /**
     * Creates an entry which is processed by the {@link AnalyticsScheduler#getExecutorForScheduling() scheduling executor}
     * and will eventually invoke {@link AnalyticsScheduler#scheduleBatches(Consumer)} for the given scheduler.
     * <p>
     * This method also enforces the {@link AnalyticsScheduler#getInterval() scheduling interval}.
     *
     * @param scheduler the scheduler to queue
     */
    private void queueScheduler(AnalyticsScheduler scheduler) {
        if (!shouldExecuteAgain(scheduler)) {
            return;
        }

        Class<? extends DistributedTaskExecutor> executor = scheduler.getExecutorForScheduling();
        cluster.submitFIFOTask(executor,
                               new JSONObject().fluentPut(CONTEXT_SCHEDULER_NAME, scheduler.getName())
                                               .fluentPut(CONTEXT_DATE, LocalDate.now()));

        if (scheduler.getInterval() != ScheduleInterval.DAILY) {
            flags.storeExecutionFlag(computeExecutionFlagName(scheduler),
                                     EXECUTION_FLAG,
                                     LocalDateTime.now(),
                                     Period.ofDays(35));
        }
    }

    /**
     * Computes the effective name to use as execution flag reference.
     *
     * @param scheduler the scheduler to compute the name for
     * @return an appropriate reference name to be used in {@link ExecutionFlags}
     */
    private String computeExecutionFlagName(AnalyticsScheduler scheduler) {
        return EXECUTION_FLAG_PREFIX + scheduler.getName();
    }

    /**
     * Determines if the given scheduler should be executed again.
     *
     * @param scheduler the scheduler to check
     * @return <tt>true</tt> if the interval sepcified by the scheduler has passed since its last execution,
     * <tt>false</tt> otherwise
     */
    private boolean shouldExecuteAgain(AnalyticsScheduler scheduler) {
        if (scheduler.getInterval() == ScheduleInterval.DAILY) {
            return true;
        }

        LocalDateTime lastExecution =
                flags.readExecutionFlag(computeExecutionFlagName(scheduler), EXECUTION_FLAG).orElse(null);
        if (lastExecution == null) {
            return true;
        }

        return lastExecution.getMonthValue() != LocalDate.now().getMonthValue();
    }
}
