/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho;

import sirius.biz.analytics.charts.explorer.UserAgentsPlatformDistributionTimeSeriesChartFactory;
import sirius.biz.tenants.TenantUserManager;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;

/**
 * Implements a time series chart for the dashboard, showing the platform distribution.
 * <p></p>
 * The chart visualizes the platforms which have been used in order to access the dashboard by extracting the required data from the user agents dataset.
 */
@Register
@Permission(TenantUserManager.PERMISSION_SYSTEM_TENANT_MEMBER)
public class DashboardUserAgentsPlatformDistributionChart extends UserAgentsPlatformDistributionTimeSeriesChartFactory {
    @Nonnull
    @Override
    public String getName() {
        return "GlobalDashboardUserAgentsPlatformDistributionChart";
    }

    @Override
    public int getPriority() {
        return 900;
    }

    @Override
    protected String getEventName() {
        return "pageimpressionevent";
    }

    @Override
    protected String getConditions() {
        return "aggregationUri = '/system/dashboard'";
    }
}
