/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.metrics.jdbc;

import sirius.biz.analytics.metrics.MetricsGuaranteedBatchExecutor;
import sirius.biz.analytics.metrics.MetricsGuaranteedSchedulerExecutor;
import sirius.biz.analytics.metrics.MonthlyLargeMetricComputer;
import sirius.biz.analytics.scheduler.AnalyticsBatchExecutor;
import sirius.biz.analytics.scheduler.AnalyticsSchedulerExecutor;
import sirius.biz.analytics.scheduler.SQLAnalyticalTaskScheduler;
import sirius.biz.analytics.scheduler.ScheduleInterval;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;

/**
 * Provides the executor which is responsible for scheduling {@link MonthlyLargeMetricComputer} instances which refer
 * to {@link sirius.db.jdbc.SQLEntity sql entities} on a monthly basis.
 */
@Register
public class SQLMonthlyLargeMetricsScheduler extends SQLAnalyticalTaskScheduler {

    @Override
    protected Class<?> getAnalyticalTaskType() {
        return MonthlyLargeMetricComputer.class;
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
    protected int getBatchSize() {
        return 5;
    }

    @Override
    public boolean useBestEffortScheduling() {
        return false;
    }

    @Override
    public ScheduleInterval getInterval() {
        return ScheduleInterval.MONTHLY;
    }

    @Nonnull
    @Override
    public String getName() {
        return "sql-large-metrics-monthly";
    }
}
