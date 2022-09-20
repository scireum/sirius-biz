/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho;

import sirius.biz.analytics.charts.explorer.PlatformDistributionTimeSeriesChartFactory;
import sirius.biz.analytics.events.PageImpressionEvent;
import sirius.biz.tenants.TenantUserManager;
import sirius.db.jdbc.SmartQuery;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;

/**
 * Implements a time series chart for the dashboard, showing the platform distribution.
 * <p>
 * The chart visualizes the platforms which have been used in order to access the dashboard by extracting the required data from the user agents dataset.
 */
@Register
@Permission(TenantUserManager.PERMISSION_SYSTEM_TENANT_MEMBER)
public class DashboardPlatformDistributionChart
        extends PlatformDistributionTimeSeriesChartFactory<PageImpressionEvent> {
    @Nonnull
    @Override
    public String getName() {
        return "GlobalDashboardPlatformDistributionChart";
    }

    @Override
    public int getPriority() {
        return 902;
    }

    @Override
    protected Class<PageImpressionEvent> getEvent() {
        return PageImpressionEvent.class;
    }

    @Override
    protected void modifyQuery(SmartQuery<PageImpressionEvent> query) {
        query.eq(PageImpressionEvent.AGGREGATION_URI, "/system/dashboard");
    }
}
