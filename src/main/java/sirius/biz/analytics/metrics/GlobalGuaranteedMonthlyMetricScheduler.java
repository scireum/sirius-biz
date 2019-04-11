/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.metrics;

import com.alibaba.fastjson.JSONObject;
import sirius.biz.analytics.scheduler.AnalyticsBatchExecutor;
import sirius.biz.analytics.scheduler.AnalyticsScheduler;
import sirius.biz.analytics.scheduler.AnalyticsSchedulerExecutor;
import sirius.biz.analytics.scheduler.ScheduleInterval;
import sirius.kernel.di.PartCollection;
import sirius.kernel.di.std.Parts;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;
import java.time.LocalDate;
import java.util.function.Consumer;

/**
 * Provides the executor which is responsible for scheduling {@link MonthlyGlobalMetricComputer} instances on a monthly
 * basis.
 */
@Register
public class GlobalGuaranteedMonthlyMetricScheduler implements AnalyticsScheduler {

    @Parts(MonthlyGlobalMetricComputer.class)
    private PartCollection<MonthlyGlobalMetricComputer> computers;

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
    public void executeBatch(JSONObject batchDescription, LocalDate date) {
        LocalDate effectiveDate = date.minusMonths(1);
        for (MonthlyGlobalMetricComputer computer : computers) {
            computer.compute(effectiveDate);
        }
    }

    @Nonnull
    @Override
    public String getName() {
        return "global-metrics-monthly";
    }
}
