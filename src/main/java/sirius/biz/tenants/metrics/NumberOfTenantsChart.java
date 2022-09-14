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
import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.TenantUserManager;
import sirius.biz.tenants.Tenants;
import sirius.kernel.commons.Callback;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Provides a chart showing the number of {@link GlobalTenantMetricComputer#METRIC_NUM_TENANTS}.
 */
@Register(framework = Tenants.FRAMEWORK_TENANTS)
@Permission(TenantUserManager.PERMISSION_SYSTEM_TENANT_MEMBER)
public class NumberOfTenantsChart extends TimeSeriesChartFactory<Object> {

    @Override
    protected boolean isMatchingChart(String uri, Object targetObject) {
        return targetObject instanceof Class<?> type && Tenant.class.isAssignableFrom(type);
    }

    @Override
    protected void collectReferencedCharts(Consumer<Class<? extends ChartFactory<Object>>> referenceChartConsumer) {
        referenceChartConsumer.accept(NumberOfActiveTenantsChart.class);
    }

    @Override
    protected void computers(boolean hasComparisonPeriod,
                             boolean isComparisonPeriod,
                             Callback<TimeSeriesComputer<Object>> executor) throws Exception {
        executor.invoke(new MetricTimeSeriesComputer<>(GlobalTenantMetricComputer.METRIC_NUM_TENANTS));
    }

    @Nonnull
    @Override
    public String getName() {
        return "GlobalNumberOfTenants";
    }

    @Override
    public int getPriority() {
        return 9000;
    }

    @Nullable
    @Override
    protected Class<? extends ChartObjectResolver<Object>> getResolver() {
        return null;
    }

    @Override
    public String getCategory() {
        return StandardCategories.USERS_AND_TENANTS;
    }
}
