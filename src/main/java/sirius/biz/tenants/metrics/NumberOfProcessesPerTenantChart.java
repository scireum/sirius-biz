/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.metrics;

import sirius.biz.analytics.explorer.ChartFactory;
import sirius.biz.analytics.explorer.ChartObjectResolver;
import sirius.biz.analytics.explorer.MetricTimeSeriesComputer;
import sirius.biz.analytics.explorer.TimeSeriesChartFactory;
import sirius.biz.analytics.explorer.TimeSeriesComputer;
import sirius.biz.jobs.StandardCategories;
import sirius.biz.process.ProcessController;
import sirius.biz.process.Processes;
import sirius.biz.tenants.Tenant;
import sirius.kernel.commons.Callback;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Provides a chart showing the number of {@link TenantMetricComputer#METRIC_NUM_PROCESSES}.
 */
@Register(framework = Processes.FRAMEWORK_PROCESSES)
@Permission(ProcessController.PERMISSION_MANAGE_PROCESSES)
public class NumberOfProcessesPerTenantChart extends TimeSeriesChartFactory<Tenant<?>> {

    @Override
    protected void collectReferencedCharts(Consumer<Class<? extends ChartFactory<Tenant<?>>>> referenceChartConsumer) {
        referenceChartConsumer.accept(ProcessDurationPerTenantChart.class);
    }

    @Override
    protected void computers(boolean hasComparisonPeriod,
                             boolean isComparisonPeriod,
                             Callback<TimeSeriesComputer<Tenant<?>>> executor) throws Exception {
        executor.invoke(new MetricTimeSeriesComputer<>(TenantMetricComputer.METRIC_NUM_PROCESSES));
    }

    @Nonnull
    @Override
    public String getName() {
        return "TenantNumberOfProcesses";
    }

    @Override
    public int getPriority() {
        return 8020;
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
