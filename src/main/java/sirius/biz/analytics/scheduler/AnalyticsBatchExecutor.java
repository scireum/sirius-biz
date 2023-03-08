/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.scheduler;

import com.alibaba.fastjson.JSONObject;
import sirius.biz.cluster.work.DistributedTaskExecutor;
import sirius.biz.cluster.work.DistributedTasks;
import sirius.biz.cluster.work.NamedRegions;
import sirius.kernel.async.TaskContext;
import sirius.kernel.commons.Timeout;
import sirius.kernel.commons.Value;
import sirius.kernel.commons.Wait;
import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.std.Part;
import sirius.kernel.nls.NLS;

import java.time.Duration;
import java.time.LocalDate;

/**
 * Provides a base implementation for executing batches of tasks generated by an {@link AnalyticsScheduler}.
 * <p>
 * This reads the scheduler name and date from each given task description and instructs the appropriate scheduler via
 * {@link AnalyticsScheduler#executeBatch(JSONObject, LocalDate, int)} to execute the specified batch.
 */
public abstract class AnalyticsBatchExecutor implements DistributedTaskExecutor {

    @Part
    protected GlobalContext globalContext;

    @Part
    protected AnalyticalEngine analyticalEngine;

    @Part
    protected NamedRegions namedRegions;

    @Part
    protected DistributedTasks distributedTasks;

    @Override
    public void executeWork(JSONObject context) throws Exception {
        String schedulerName = context.getString(AnalyticalEngine.CONTEXT_SCHEDULER_NAME);
        LocalDate date =
                Value.of(NLS.parseMachineString(LocalDate.class, context.getString(AnalyticalEngine.CONTEXT_DATE)))
                     .asLocalDate(LocalDate.now());
        int level = context.getIntValue(AnalyticalEngine.CONTEXT_LEVEL);

        AnalyticsScheduler scheduler = globalContext.findPart(schedulerName, AnalyticsScheduler.class);
        namedRegions.inNamedRegion(determineRegionName(schedulerName), () -> {
            scheduler.executeBatch(context, date, level);
        });

        if (context.getBooleanValue(AnalyticalEngine.CONTEXT_LAST) && level < scheduler.getMaxLevel()) {
            scheduleNextLevel(date, level + 1, scheduler);
        }
    }

    protected String determineRegionName(String schedulerName) {
        return "analytics-batch-" + schedulerName;
    }

    /**
     * Schedules the next level of tasks for the given scheduler.
     * <p>
     * Note that we first check, that no other analytics batch for this scheduler is currently running, as the last
     * batch might have been started simultaneously with preceding batches, which might still be running. Therefore,
     * the use a named region and permit waiting up to 30 minutes for the other blocks to finish. Note that we don't
     * have to check the queue length, as we know that our batch was the last in the queue.
     *
     * @param date      the date for which the computation was scheduled
     * @param nextLevel the level to schedule
     * @param scheduler the scheduler to plan an execution for
     */
    private void scheduleNextLevel(LocalDate date, int nextLevel, AnalyticsScheduler scheduler) {
        AnalyticalEngine.LOG.FINE("Scheduling level %s for %s (%s)...", nextLevel, scheduler.getName(), date);
        Timeout timeout = new Timeout(Duration.ofMinutes(30));
        boolean otherAnalyticsTasksRunning = !namedRegions.isNamedRegionFree(determineRegionName(scheduler.getName()));
        while (otherAnalyticsTasksRunning && !timeout.isReached() && TaskContext.get().isActive()) {
            AnalyticalEngine.LOG.FINE(
                    "Concurrent tasks are still running - waiting 15s before scheduling level  %s for %s (%s).",
                    nextLevel,
                    scheduler.getName(),
                    date);
            Wait.seconds(15);
            otherAnalyticsTasksRunning = !namedRegions.isNamedRegionFree(determineRegionName(scheduler.getName()));
        }

        if (!otherAnalyticsTasksRunning) {
            AnalyticalEngine.LOG.FINE("Successfully scheduled level %s for %s (%s).",
                                      nextLevel,
                                      scheduler.getName(),
                                      date);
        } else if (timeout.isReached()) {
            AnalyticalEngine.LOG.WARN(
                    "Scheduled level %s for %s (%s) even though some concurrent tasks seem to be running for named region %s (Timeout of 30m reached).",
                    nextLevel,
                    scheduler.getName(),
                    date,
                    determineRegionName(scheduler.getName()));
        } else {
            AnalyticalEngine.LOG.WARN(
                    "Scheduled level %s for %s (%s) even though some concurrent tasks seem to be running for named region %s (System shutdown anticipated).",
                    nextLevel,
                    scheduler.getName(),
                    date,
                    determineRegionName(scheduler.getName()));
        }

        analyticalEngine.queueScheduler(scheduler, date, nextLevel);
    }
}
