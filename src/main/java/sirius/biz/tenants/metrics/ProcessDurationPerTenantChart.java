/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.metrics;

import sirius.biz.analytics.charts.explorer.ChartFactory;
import sirius.biz.analytics.charts.explorer.ChartObjectResolver;
import sirius.biz.analytics.charts.explorer.MetricTimeSeriesComputer;
import sirius.biz.analytics.charts.explorer.TimeSeriesChartFactory;
import sirius.biz.analytics.charts.explorer.TimeSeriesComputer;
import sirius.biz.jobs.StandardCategories;
import sirius.biz.process.ProcessController;
import sirius.biz.process.Processes;
import sirius.biz.process.ProcessesMonthlyMetrics;
import sirius.biz.tenants.Tenant;
import sirius.kernel.commons.Callback;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Provides a chart showing the number of {@link TenantMetricComputer#METRIC_PROCESS_DURATION} as well as
 * {@link ProcessesMonthlyMetrics#METRIC_NUM_PROCESSES}.
 */
@Register(framework = Processes.FRAMEWORK_PROCESSES)
@Permission(ProcessController.PERMISSION_MANAGE_PROCESSES)
public class ProcessDurationPerTenantChart extends TimeSeriesChartFactory<Tenant<?>> {

    @Override
    protected void collectReferencedCharts(Consumer<Class<? extends ChartFactory<Tenant<?>>>> referenceChartConsumer) {
        referenceChartConsumer.accept(NumberOfProcessesPerTenantChart.class);
    }

    @Override
    protected void computers(Tenant<?> ignoredTenant,
                             boolean hasComparisonPeriod,
                             boolean isComparisonPeriod,
                             Callback<TimeSeriesComputer<Tenant<?>>> executor) throws Exception {
        executor.invoke(new MetricTimeSeriesComputer<>(TenantMetricComputer.METRIC_PROCESS_DURATION));
    }

    @Nonnull
    @Override
    public String getName() {
        return "TenantProcessDuration";
    }

    @Override
    public int getPriority() {
        return 8030;
    }

    @Nullable
    @Override
    protected Class<? extends ChartObjectResolver<Tenant<?>>> getResolver() {
        return TenantResolver.class;
    }

    @Override
    public String getCategory() {
        return StandardCategories.MONITORING;
    }
}
