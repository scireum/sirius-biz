/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.metrics;

import com.alibaba.fastjson.JSONObject;
import sirius.biz.analytics.scheduler.AnalyticalEngine;
import sirius.biz.analytics.scheduler.AnalyticalTask;
import sirius.biz.analytics.scheduler.AnalyticsBatchExecutor;
import sirius.biz.analytics.scheduler.AnalyticsScheduler;
import sirius.biz.analytics.scheduler.AnalyticsSchedulerExecutor;
import sirius.biz.analytics.scheduler.ScheduleInterval;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.PartCollection;
import sirius.kernel.di.std.Parts;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Microtiming;

import javax.annotation.Nonnull;
import java.time.LocalDate;
import java.util.function.Consumer;

/**
 * Provides the executor which is responsible for scheduling {@link DailyGlobalMetricComputer} instances on a daily
 * basis.
 */
@Register
public class GlobalGuaranteedDailyMetricScheduler implements AnalyticsScheduler {

    @Parts(DailyGlobalMetricComputer.class)
    private PartCollection<DailyGlobalMetricComputer> computers;

    private Integer maxLevel;

    @Override
    public int getMaxLevel() {
        if (maxLevel == null) {
            maxLevel = computers.getParts()
                                .stream()
                                .mapToInt(DailyGlobalMetricComputer::getLevel)
                                .max()
                                .orElse(AnalyticalTask.DEFAULT_LEVEL);
        }

        return maxLevel;
    }

    @Override
    public Class<? extends AnalyticsSchedulerExecutor> getExecutorForScheduling() {
        return MetricsGuaranteedSchedulerExecutor.class;
    }

    @Override
    public Class<? extends AnalyticsBatchExecutor> getExecutorForTasks() {
        return MetricsGuaranteedBatchExecutor.class;
    }

    @Override
    public boolean useBestEffortScheduling() {
        return false;
    }

    @Override
    public ScheduleInterval getInterval() {
        return ScheduleInterval.DAILY;
    }

    @Override
    public void scheduleBatches(Consumer<JSONObject> batchConsumer) {
        batchConsumer.accept(new JSONObject());
    }

    @Override
    public void executeBatch(JSONObject batchDescription, LocalDate date, int level) {
        LocalDate effectiveDate = date.minusDays(1);
        for (DailyGlobalMetricComputer computer : computers) {
            if (computer.getLevel() == level) {
                executeComputer(effectiveDate, computer);
            }
        }
    }

    private void executeComputer(LocalDate date, DailyGlobalMetricComputer computer) {
        Watch watch = Watch.start();
        try {
            computer.compute(date);
        } catch (Exception ex) {
            Exceptions.handle()
                      .to(AnalyticalEngine.LOG)
                      .error(ex)
                      .withSystemErrorMessage("The global daily computer %s failed: %s (%s)",
                                              computer.getClass().getName())
                      .handle();
        }
        if (AnalyticalEngine.LOG.isFINE()) {
            AnalyticalEngine.LOG.FINE("Executed global daily computer '%s' in '%s' took: %s",
                                      computer.getClass().getSimpleName(),
                                      getName(),
                                      watch.duration());
        }
        if (Microtiming.isEnabled()) {
            watch.submitMicroTiming(AnalyticalEngine.MICROTIMING_KEY_ANALYTICS,
                                    Strings.apply("Executed global daily computer '%s'",
                                                  computer.getClass().getName()));
        }
    }

    @Override
    public boolean isActive() {
        return !computers.getParts().isEmpty();
    }

    @Nonnull
    @Override
    public String getName() {
        return "global-metrics-daily";
    }
}
