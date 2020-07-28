/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.metrics.jdbc;

import com.alibaba.fastjson.JSONObject;
import sirius.biz.analytics.metrics.DailyMetricComputer;
import sirius.biz.analytics.metrics.MetricsGuaranteedBatchExecutor;
import sirius.biz.analytics.metrics.MetricsGuaranteedSchedulerExecutor;
import sirius.biz.analytics.scheduler.AnalyticsBatchExecutor;
import sirius.biz.analytics.scheduler.AnalyticsSchedulerExecutor;
import sirius.biz.analytics.scheduler.SQLAnalyticalTaskScheduler;
import sirius.biz.analytics.scheduler.ScheduleInterval;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;
import java.time.LocalDate;

/**
 * Provides the executor which is responsible for scheduling {@link DailyMetricComputer} instances which refer
 * to {@link sirius.db.jdbc.SQLEntity sql entities} on a daily basis.
 */
@Register(framework = SQLMetrics.FRAMEWORK_JDBC_METRICS)
public class SQLGuaranteedDailyMetricScheduler extends SQLAnalyticalTaskScheduler {

    @Override
    protected Class<?> getAnalyticalTaskType() {
        return DailyMetricComputer.class;
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

    @Nonnull
    @Override
    public String getName() {
        return "sql-metrics-daily";
    }

    @Override
    public void executeBatch(JSONObject batchDescription, LocalDate date) {
        super.executeBatch(batchDescription, date.minusDays(1));
    }
}
