/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.metrics.mongo;

import sirius.biz.analytics.metrics.MetricsGuaranteedBatchExecutor;
import sirius.biz.analytics.metrics.MetricsGuaranteedSchedulerExecutor;
import sirius.biz.analytics.metrics.MonthlyLargeMetricComputer;
import sirius.biz.analytics.scheduler.AnalyticsBatchExecutor;
import sirius.biz.analytics.scheduler.AnalyticsSchedulerExecutor;
import sirius.biz.analytics.scheduler.MongoAnalyticalTaskScheduler;
import sirius.biz.analytics.scheduler.ScheduleInterval;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;

/**
 * Provides the executor which is responsible for scheduling {@link MonthlyLargeMetricComputer} instances which refer
 * to {@link sirius.db.mongo.MongoEntity mongo entities} on a monthly basis.
 */
@Register
public class MongoMonthlyLargeMetricsScheduler extends MongoAnalyticalTaskScheduler {

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
    public boolean useBestEffortScheduling() {
        return false;
    }

    @Override
    public ScheduleInterval getInterval() {
        return ScheduleInterval.MONTHLY;
    }

    @Override
    protected int getBatchSize() {
        return 5;
    }

    @Nonnull
    @Override
    public String getName() {
        return "mongo-large-metrics-monthly";
    }
}
