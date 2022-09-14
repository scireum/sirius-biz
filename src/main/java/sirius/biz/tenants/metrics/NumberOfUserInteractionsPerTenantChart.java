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
import sirius.biz.analytics.charts.explorer.Granularity;
import sirius.biz.analytics.charts.explorer.TimeSeriesChartFactory;
import sirius.biz.analytics.charts.explorer.TimeSeriesComputer;
import sirius.biz.analytics.charts.explorer.TimeSeriesData;
import sirius.biz.analytics.events.EventRecorder;
import sirius.biz.jobs.StandardCategories;
import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.TenantUserManager;
import sirius.biz.tenants.Tenants;
import sirius.biz.tenants.UserAccountController;
import sirius.kernel.commons.Callback;
import sirius.kernel.commons.Limit;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.LocalDate;
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
    protected void computers(boolean hasComparisonPeriod,
                             boolean isComparisonPeriod,
                             Callback<TimeSeriesComputer<Tenant<?>>> executor) throws Exception {
        executor.invoke((object, timeSeries) -> {
            TimeSeriesData value = timeSeries.createDefaultData();
            eventRecorder.createQuery(//language=SQL
                                      """
                                              SELECT count(*) AS value,
                                                     YEAR(eventDate) as year,
                                                     MONTH(eventDate) AS month
                                                     [:daily , DAY(eventDate) AS day]
                                              FROM useractivityevent
                                              WHERE userData_tenantId = ${tenant}
                                                AND eventDate >= ${start}
                                                AND eventDate <= ${end}
                                              GROUP BY [:daily DAY(eventDate), ] MONTH(eventDate), YEAR(eventDate)
                                              """)
                         .set("daily", timeSeries.getGranularity() == Granularity.DAY)
                         .set("tenant", object.getIdAsString())
                         .set("start", timeSeries.getStart())
                         .set("end", timeSeries.getEnd())
                         .iterateAll(row -> {
                             LocalDate localDate = LocalDate.of(row.getValue("year").asInt(0),
                                                                row.getValue("month").asInt(0),
                                                                row.tryGetValue("day").asInt(1));
                             value.addValue(localDate, row.getValue("value").asDouble(0d));
                         }, Limit.UNLIMITED);
        });
    }

    @Nonnull
    @Override
    public String getName() {
        return "TenantNumberOfUserInteractions";
    }

    @Override
    public int getPriority() {
        return 905;
    }
}
