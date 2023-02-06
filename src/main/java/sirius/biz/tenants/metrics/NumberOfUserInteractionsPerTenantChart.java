/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.metrics;

import sirius.biz.analytics.events.EventRecorder;
import sirius.biz.analytics.events.UserActivityEvent;
import sirius.biz.analytics.events.UserData;
import sirius.biz.analytics.explorer.ChartFactory;
import sirius.biz.analytics.explorer.ChartObjectResolver;
import sirius.biz.analytics.explorer.EventTimeSeriesComputer;
import sirius.biz.analytics.explorer.TimeSeriesChartFactory;
import sirius.biz.analytics.explorer.TimeSeriesComputer;
import sirius.biz.jobs.StandardCategories;
import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.TenantUserManager;
import sirius.biz.tenants.Tenants;
import sirius.biz.tenants.UserAccountController;
import sirius.kernel.commons.Callback;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Uses the {@link sirius.biz.analytics.events.UserActivityEvent} to compute the exact number of active users for a
 * selected period and tenant.
 */
@Register(framework = Tenants.FRAMEWORK_TENANTS)
public class NumberOfUserInteractionsPerTenantChart extends TimeSeriesChartFactory<Tenant<?>> {

    @Part
    private EventRecorder eventRecorder;

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
        referenceChartConsumer.accept(NumberOfActiveUsersPerTenantChart.class);
    }

    @Override
    protected void computers(Tenant<?> ignoredTenant,
                             boolean hasComparisonPeriod,
                             boolean isComparisonPeriod,
                             Callback<TimeSeriesComputer<Tenant<?>>> executor) throws Exception {
        EventTimeSeriesComputer<Tenant<?>, UserActivityEvent> computer =
                new EventTimeSeriesComputer<>(UserActivityEvent.class,
                                              (tenant, query) -> query.eq(UserActivityEvent.USER_DATA.inner(UserData.TENANT_ID),
                                                                          tenant.getIdAsString()));

        computer.addAggregation(NumberOfUserInteractionsChart.AGGREGATION_EXPRESSION_USERS, null);

        executor.invoke(computer);
    }

    @Nonnull
    @Override
    public String getName() {
        return "TenantNumberOfUserInteractions";
    }

    @Override
    public int getPriority() {
        return 9050;
    }
}
