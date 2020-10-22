/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.metrics.mongo;

import sirius.biz.analytics.metrics.MetricsBestEffortBatchExecutor;
import sirius.biz.analytics.metrics.MetricsBestEffortSchedulerExecutor;
import sirius.biz.analytics.metrics.MonthlyMetricComputer;
import sirius.biz.analytics.scheduler.AnalyticalTask;
import sirius.biz.analytics.scheduler.AnalyticsBatchExecutor;
import sirius.biz.analytics.scheduler.AnalyticsSchedulerExecutor;
import sirius.biz.analytics.scheduler.MongoAnalyticalTaskScheduler;
import sirius.biz.analytics.scheduler.ScheduleInterval;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;

/**
 * Provides the executor which is responsible for scheduling {@link MonthlyMetricComputer} instances which refer
 * to {@link sirius.db.mongo.MongoEntity mongo entities} on a daily basis using the best effort principle.
 */
@Register(framework = MongoMetrics.FRAMEWORK_MONGO_METRICS)
public class MongoBestEffortDailyScheduler extends MongoAnalyticalTaskScheduler {

    @Override
    protected Class<?> getAnalyticalTaskType() {
        return MonthlyMetricComputer.class;
    }

    @Override
    public Class<? extends AnalyticsSchedulerExecutor> getExecutorForScheduling() {
        return MetricsBestEffortSchedulerExecutor.class;
    }

    @Override
    public Class<? extends AnalyticsBatchExecutor> getExecutorForTasks() {
        return MetricsBestEffortBatchExecutor.class;
    }

    @Override
    public boolean useBestEffortScheduling() {
        return true;
    }

    @Override
    protected boolean isMatchingEntityType(AnalyticalTask<?> task) {
        if (((MonthlyMetricComputer<?>) task).suppressBestEffortScheduling()) {
            return false;
        }

        return super.isMatchingEntityType(task);
    }

    @Override
    public ScheduleInterval getInterval() {
        return ScheduleInterval.DAILY;
    }

    @Nonnull
    @Override
    public String getName() {
        return "mongo-metrics-best-effort-daily";
    }
}
