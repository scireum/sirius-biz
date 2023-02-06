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
import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.TenantUserManager;
import sirius.biz.tenants.Tenants;
import sirius.biz.tenants.UserAccountController;
import sirius.kernel.commons.Callback;
import sirius.kernel.di.std.Register;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Provides a chart showing the number of {@link TenantMetricComputer#METRIC_AVG_ACTIVITY} for a tenant.
 */
@Register(framework = Tenants.FRAMEWORK_TENANTS)
public class AverageActivityPerTenantChart extends TimeSeriesChartFactory<Tenant<?>> {

    @Override
    public boolean isAccessibleToCurrentUser() {
        UserInfo currentUser = UserContext.getCurrentUser();
        return currentUser.hasPermission(TenantUserManager.PERMISSION_SYSTEM_TENANT_MEMBER)
               || currentUser.hasPermission(UserAccountController.PERMISSION_MANAGE_USER_ACCOUNTS);
    }

    @Nullable
    @Override
    protected Class<? extends ChartObjectResolver<Tenant<?>>> getResolver() {
        return TenantResolver.class;
    }

    @Override
    public String getCategory() {
        return StandardCategories.USERS_AND_TENANTS;
    }

    @Override
    protected void collectReferencedCharts(Consumer<Class<? extends ChartFactory<Tenant<?>>>> referenceChartConsumer) {
        referenceChartConsumer.accept(AverageActivityPerTenantChart.class);
    }

    @Override
    protected void computers(Tenant<?> ignoredTenant,
                             boolean hasComparisonPeriod,
                             boolean isComparisonPeriod,
                             Callback<TimeSeriesComputer<Tenant<?>>> executor) throws Exception {
        executor.invoke(new MetricTimeSeriesComputer<>(TenantMetricComputer.METRIC_AVG_ACTIVITY));
    }

    @Nonnull
    @Override
    public String getName() {
        return "TenantAvgUserActivity";
    }

    @Override
    public int getPriority() {
        return 9060;
    }
}
