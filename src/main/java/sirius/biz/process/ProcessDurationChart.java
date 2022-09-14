/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process;

import sirius.biz.analytics.charts.explorer.ChartFactory;
import sirius.biz.analytics.charts.explorer.ChartObjectResolver;
import sirius.biz.analytics.charts.explorer.MetricTimeSeriesComputer;
import sirius.biz.analytics.charts.explorer.TimeSeriesChartFactory;
import sirius.biz.analytics.charts.explorer.TimeSeriesComputer;
import sirius.biz.jobs.StandardCategories;
import sirius.kernel.commons.Callback;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Provides a chart showing the number of {@link ProcessesDailyMetrics#METRIC_PROCESS_DURATION} as well as
 * {@link ProcessesMonthlyMetrics#METRIC_PROCESS_DURATION}.
 */
@Register(framework = Processes.FRAMEWORK_PROCESSES)
@Permission(ProcessController.PERMISSION_MANAGE_ALL_PROCESSES)
public class ProcessDurationChart extends TimeSeriesChartFactory<Object> {

    @Override
    protected boolean isMatchingChart(String uri, Object targetObject) {
        return targetObject instanceof Class<?> type && Process.class.isAssignableFrom(type);
    }

    @Override
    protected void collectReferencedCharts(Consumer<Class<? extends ChartFactory<Object>>> referenceChartConsumer) {
        referenceChartConsumer.accept(NumberOfProcessesChart.class);
    }

    @Override
    protected void computers(boolean hasComparisonPeriod,
                             boolean isComparisonPeriod,
                             Callback<TimeSeriesComputer<Object>> executor) throws Exception {
        executor.invoke(new MetricTimeSeriesComputer<>(ProcessesMonthlyMetrics.METRIC_PROCESS_DURATION).withDailyMetricName(
                ProcessesDailyMetrics.METRIC_PROCESS_DURATION));
    }

    @Nonnull
    @Override
    public String getName() {
        return "GlobalProcessDuration";
    }

    @Override
    public int getPriority() {
        return 801;
    }

    @Nullable
    @Override
    protected Class<? extends ChartObjectResolver<Object>> getResolver() {
        return null;
    }

    @Override
    public String getCategory() {
        return StandardCategories.MONITORING;
    }
}
