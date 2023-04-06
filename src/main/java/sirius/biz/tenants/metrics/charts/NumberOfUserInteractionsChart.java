/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.metrics.charts;

import sirius.biz.analytics.events.UserActivityEvent;
import sirius.biz.analytics.explorer.ChartFactory;
import sirius.biz.analytics.explorer.ChartObjectResolver;
import sirius.biz.analytics.explorer.EventTimeSeriesComputer;
import sirius.biz.analytics.explorer.TimeSeriesChartFactory;
import sirius.biz.analytics.explorer.TimeSeriesComputer;
import sirius.biz.jobs.StandardCategories;
import sirius.biz.tenants.TenantUserManager;
import sirius.biz.tenants.Tenants;
import sirius.biz.tenants.UserAccountController;
import sirius.kernel.commons.Callback;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Uses the {@link sirius.biz.analytics.events.UserActivityEvent} to compute the exact number of active users for a
 * selected period.
 */
@Register(framework = Tenants.FRAMEWORK_TENANTS)
@Permission(TenantUserManager.PERMISSION_SYSTEM_TENANT_MEMBER)
public class NumberOfUserInteractionsChart extends TimeSeriesChartFactory<Object> {

    protected static final String AGGREGATION_EXPRESSION_USERS = "count(DISTINCT userData_userId)";
    protected static final String LABEL_USERS = "$NumberOfUserInteractionsChart.users";
    protected static final String AGGREGATION_EXPRESSION_TENANTS = "count(DISTINCT userData_tenantId)";
    protected static final String LABEL_TENANTS = "$NumberOfUserInteractionsChart.tenants";

    @Override
    public boolean isAccessibleToCurrentUser() {
        UserInfo currentUser = UserContext.getCurrentUser();
        return super.isAccessibleToCurrentUser() && currentUser.hasPermission(UserAccountController.getUserManagementPermission());
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

    @Override
    protected void collectReferencedCharts(Consumer<Class<? extends ChartFactory<Object>>> referenceChartConsumer) {
        referenceChartConsumer.accept(NumberOfActiveUsersChart.class);
    }

    @Override
    protected void computers(Object ignoredObject,
                             boolean hasComparisonPeriod,
                             boolean isComparisonPeriod,
                             Callback<TimeSeriesComputer<Object>> executor) throws Exception {
        EventTimeSeriesComputer<Object, UserActivityEvent> computer =
                new EventTimeSeriesComputer<>(UserActivityEvent.class);

        if (hasComparisonPeriod) {
            computer.addAggregation(AGGREGATION_EXPRESSION_USERS, null);
        } else {
            computer.addAggregation(AGGREGATION_EXPRESSION_USERS, LABEL_USERS);
            computer.addAggregation(AGGREGATION_EXPRESSION_TENANTS, LABEL_TENANTS);
        }

        executor.invoke(computer);
    }

    @Nonnull
    @Override
    public String getName() {
        return "GlobalNumberOfUserInteractions";
    }

    @Override
    public int getPriority() {
        return 9040;
    }
}
