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
import sirius.kernel.health.Log;
import sirius.kernel.timer.EveryDay;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
@Register(classes = {EveryDay.class, AnalyticalEngine.class})
public class AnalyticalEngine implements EveryDay {

    /**
     * Contains the log used by the analytical scheduler and all its related classes.
     */
    public static final Log LOG = Log.get("analytics");

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

    /**
     * Contains the implementation which stores which scheduler was executed when.
     * <p>
     * This is permitted to remain empty as long as the whole framework is unused. Otherwise we report a custom error.
     */
    @Part
    @Nullable
    private ExecutionFlags flags;

    @Parts(AnalyticsScheduler.class)
    private PartCollection<AnalyticsScheduler> schedulers;
    private List<AnalyticsScheduler> activeSchedulers;

    @Override
    public String getConfigKeyName() {
        return "analytical-engine";
    }

    @Override
    public void runTimer() throws Exception {
        if (!getActiveSchedulers().isEmpty()) {
            tasks.defaultExecutor().start(this::queueSchedulers);
        }
    }

    protected List<AnalyticsScheduler> getActiveSchedulers() {
        if (activeSchedulers == null) {
            initialize();
        }

        return Collections.unmodifiableList(activeSchedulers);
    }

    protected void initialize() {
        activeSchedulers =
                schedulers.getParts().stream().filter(AnalyticsScheduler::isActive).collect(Collectors.toList());

        if (flags == null && activeSchedulers.stream()
                                             .anyMatch(scheduler -> scheduler.getInterval()
                                                                    != ScheduleInterval.DAILY)) {
            LOG.WARN("The AnalyticalEngine is present but neither 'biz.analytics-execution-flags-jdbc' nor"
                     + " 'biz.analytics-execution-flags-mongo' is enabled! This will result in NullPointerExceptions");
        }
    }

    /**
     * Iterates over all known schedulers and, if scheduling is advised, queues an appropriate entry in the
     * underlying {@link AnalyticsScheduler#getExecutorForScheduling() scheduling queue}.
     */
    private void queueSchedulers() {
        Map<String, Boolean> queueCache = new HashMap<>();
        activeSchedulers.stream()
                        .filter(scheduler -> shouldSchedule(scheduler, queueCache))
                        .forEach(scheduler -> queueScheduler(scheduler, false, null));
    }

    /**
     * Determines if the scheduler either guarantees execution or if the underlying queue was empty as the start of the
     * scheduling run.
     *
     * @param scheduler  the scheduler to check
     * @param queueCache the cached state of the queues being used by the schedulers
     * @return <tt>true</tt> if the scheduler should run, <tt>false</tt> otherwise
     */
    protected boolean shouldSchedule(AnalyticsScheduler scheduler, Map<String, Boolean> queueCache) {
        if (!scheduler.useBestEffortScheduling()) {
            return true;
        }

        String queueName = cluster.getQueueName(scheduler.getExecutorForScheduling());
        boolean isQueueEmpty = queueCache.computeIfAbsent(queueName, this::checkIfQueueIsEmpty);

        if (!isQueueEmpty && LOG.isFINE()) {
            LOG.FINE("Skipping best-effort scheduler '%s' (%s) as its queue '%s' isn't empty...",
                     scheduler.getName(),
                     scheduler.getClass().getSimpleName(),
                     queueName);
        }

        return isQueueEmpty;
    }

    private Boolean checkIfQueueIsEmpty(String queueName) {
        return cluster.getQueueLength(queueName) == 0;
    }

    /**
     * Creates an entry which is processed by the {@link AnalyticsScheduler#getExecutorForScheduling() scheduling executor}
     * and will eventually invoke {@link AnalyticsScheduler#scheduleBatches(Consumer)} for the given scheduler.
     * <p>
     * This method also enforces the {@link AnalyticsScheduler#getInterval() scheduling interval} unless <tt>force</tt>
     * is set to <tt>true</tt>.
     *
     * @param scheduler   the scheduler to queue
     * @param force       determines if execution is forced
     * @param contextDate the context date to execute within - this can be used to execute a scheduler for a different
     *                    date than now, e.g. to manualy compute old / outdated metrics etc.
     */
    protected void queueScheduler(AnalyticsScheduler scheduler, boolean force, @Nullable LocalDate contextDate) {
        if (!force && contextDate == null && !shouldExecuteAgain(scheduler)) {
            if (LOG.isFINE()) {
                LOG.FINE("Skipping scheduler '%s' (%s) as it has already been executed recently...",
                         scheduler.getName(),
                         scheduler.getClass().getSimpleName());
            }
            return;
        }

        Class<? extends DistributedTaskExecutor> executor = scheduler.getExecutorForScheduling();
        if (LOG.isFINE()) {
            LOG.FINE("Scheduling '%s' (%s) via '%s'...",
                     scheduler.getName(),
                     scheduler.getClass().getSimpleName(),
                     executor.getSimpleName());
        }
        cluster.submitFIFOTask(executor,
                               new JSONObject().fluentPut(CONTEXT_SCHEDULER_NAME, scheduler.getName())
                                               .fluentPut(CONTEXT_DATE,
                                                          contextDate == null ? LocalDate.now() : contextDate));

        if (scheduler.getInterval() != ScheduleInterval.DAILY && (contextDate == null || LocalDate.now()
                                                                                                  .equals(contextDate))) {
            flags.storeExecutionFlag(computeExecutionFlagName(scheduler),
                                     EXECUTION_FLAG,
                                     LocalDateTime.now(),
                                     scheduler.getInterval() == ScheduleInterval.DAILY ?
                                     Period.ofDays(3) :
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

        LocalDateTime lastExecution = getLastExecution(scheduler).orElse(null);
        if (lastExecution == null) {
            return true;
        }

        return lastExecution.getMonthValue() != LocalDate.now().getMonthValue();
    }

    /**
     * Determines the last execution timestamp of the given scheduler.
     * <p>
     * Note that this only accounts for regular (planned) invocations of the scheduler as well as forced
     * executions for the current day. If an execution if forced for another day, this will not be recorded
     * as execution flag and therefore not be returned here.
     *
     * @param scheduler the scheduler to check the last execution for
     * @return the last execution timestamp or an empty optional
     */
    protected Optional<LocalDateTime> getLastExecution(AnalyticsScheduler scheduler) {
        return flags.readExecutionFlag(computeExecutionFlagName(scheduler), EXECUTION_FLAG);
    }
}
