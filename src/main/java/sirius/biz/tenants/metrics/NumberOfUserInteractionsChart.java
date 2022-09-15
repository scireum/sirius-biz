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
import sirius.biz.tenants.TenantUserManager;
import sirius.biz.tenants.Tenants;
import sirius.biz.tenants.UserAccountController;
import sirius.kernel.commons.Callback;
import sirius.kernel.commons.Limit;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.function.Consumer;

/**
 * Uses the {@link sirius.biz.analytics.events.UserActivityEvent} to compute the exact number of active users for a
 * selected period.
 */
@Register(framework = Tenants.FRAMEWORK_TENANTS)
@Permission(TenantUserManager.PERMISSION_SYSTEM_TENANT_MEMBER)
public class NumberOfUserInteractionsChart extends TimeSeriesChartFactory<Object> {

    @Part
    private EventRecorder eventRecorder;

    @Override
    public boolean isAccessibleToCurrentUser() {
        UserInfo currentUser = UserContext.getCurrentUser();
        return currentUser.hasPermission(UserAccountController.getUserManagementPermission());
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
    protected void computers(boolean hasComparisonPeriod,
                             boolean isComparisonPeriod,
                             Callback<TimeSeriesComputer<Object>> executor) throws Exception {
        executor.invoke((object, timeSeries) -> {
            TimeSeriesData users = hasComparisonPeriod ?
                                   timeSeries.createDefaultData() :
                                   timeSeries.createData("$NumberOfUserInteractionsChart.users");
            TimeSeriesData tenants =
                    hasComparisonPeriod ? null : timeSeries.createData("NumberOfUserInteractionsChart.tenants");

            eventRecorder.createQuery(//language=SQL
                                      """
                                              SELECT count(DISTINCT userData_userId) AS users,
                                                     YEAR(eventDate) as year,
                                                     MONTH(eventDate) AS month
                                                     [:daily , DAY(eventDate) AS day]
                                                     [:tenants , count(DISTINCT userData_tenantId) AS tenants]
                                              FROM useractivityevent
                                              WHERE eventDate >= ${start}
                                                AND eventDate <= ${end}
                                              GROUP BY [:daily DAY(eventDate), ] MONTH(eventDate), YEAR(eventDate)
                                              """)
                         .set("daily", timeSeries.getGranularity() == Granularity.DAY)
                         .set("tenants", tenants != null)
                         .set("start", timeSeries.getStart())
                         .set("end", timeSeries.getEnd())
                         .iterateAll(row -> {
                             LocalDate localDate = LocalDate.of(row.getValue("year").asInt(0),
                                                                row.getValue("month").asInt(0),
                                                                row.tryGetValue("day").asInt(1));
                             users.addValue(localDate, row.getValue("users").asDouble(0d));
                             if (tenants != null) {
                                 tenants.addValue(localDate, row.getValue("tenants").asDouble(0d));
                             }
                         }, Limit.UNLIMITED);
        });
    }

    @Nonnull
    @Override
    public String getName() {
        return "GlobalNumberOfUserInteractions";
    }

    @Override
    public int getPriority() {
        return 9030;
    }
}
