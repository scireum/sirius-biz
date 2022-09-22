/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho;

import sirius.biz.analytics.charts.explorer.ChartFactory;
import sirius.biz.analytics.charts.explorer.ChartObjectResolver;
import sirius.biz.analytics.charts.explorer.PlatformDistributionTimeSeriesComputer;
import sirius.biz.analytics.charts.explorer.TimeSeriesChartFactory;
import sirius.biz.analytics.charts.explorer.TimeSeriesComputer;
import sirius.biz.analytics.events.PageImpressionEvent;
import sirius.biz.jobs.StandardCategories;
import sirius.biz.tenants.TenantUserManager;
import sirius.kernel.commons.Callback;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Implements a time series chart for the dashboard, showing the platform distribution.
 * <p>
 * The chart visualizes the platforms which have been used in order to access the dashboard by extracting the required data from the user agents dataset.
 */
@Register
@Permission(TenantUserManager.PERMISSION_SYSTEM_TENANT_MEMBER)
public class DashboardPlatformDistributionChart extends TimeSeriesChartFactory<Object> {
    @Nullable
    @Override
    protected Class<? extends ChartObjectResolver<Object>> getResolver() {
        return null;
    }

    @Override
    public String getCategory() {
        return StandardCategories.MISC;
    }

    @Override
    protected void collectReferencedCharts(Consumer<Class<? extends ChartFactory<Object>>> referenceChartConsumer) {
        // intentionally empty -> there are no referenced charts
    }

    @Override
    protected void computers(boolean hasComparisonPeriod,
                             boolean isComparisonPeriod,
                             Callback<TimeSeriesComputer<Object>> executor) throws Exception {
        if (isComparisonPeriod) {
            return;
        }

        executor.invoke(new PlatformDistributionTimeSeriesComputer<>(PageImpressionEvent.class,
                                                                     (ignored, query) -> query.eq(PageImpressionEvent.AGGREGATION_URI,
                                                                                                  "/system/dashboard")));
    }

    @Override
    protected boolean stackValues(boolean hasComparisonPeriod) {
        return true;
    }

    @Nonnull
    @Override
    public String getName() {
        return "GlobalDashboardPlatformDistributionChart";
    }

    @Override
    public int getPriority() {
        return 9020;
    }
}
