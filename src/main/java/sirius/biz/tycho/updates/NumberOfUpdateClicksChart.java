/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.updates;

import sirius.biz.analytics.charts.explorer.ChartFactory;
import sirius.biz.analytics.charts.explorer.ChartObjectResolver;
import sirius.biz.analytics.charts.explorer.EventTimeSeriesComputer;
import sirius.biz.analytics.charts.explorer.TimeSeriesChartFactory;
import sirius.biz.analytics.charts.explorer.TimeSeriesComputer;
import sirius.biz.analytics.events.EventRecorder;
import sirius.biz.jobs.StandardCategories;
import sirius.biz.tenants.TenantUserManager;
import sirius.kernel.commons.Callback;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Provides a chart which shows the number of {@link UpdateClickEvent UpdateClickEvents} which have been recorded.
 */
@Register
@Permission(TenantUserManager.PERMISSION_SYSTEM_TENANT_MEMBER)
public class NumberOfUpdateClicksChart extends TimeSeriesChartFactory<Object> {

    @Part
    private EventRecorder eventRecorder;

    @Part
    private UpdateManager updateManager;

    @Override
    public boolean isAccessibleToCurrentUser() {
        return super.isAccessibleToCurrentUser() && updateManager.hasFeedUrl();
    }

    @Nullable
    @Override
    protected Class<? extends ChartObjectResolver<Object>> getResolver() {
        return null;
    }

    @Override
    public String getCategory() {
        return StandardCategories.MONITORING;
    }

    @Override
    protected void collectReferencedCharts(Consumer<Class<? extends ChartFactory<Object>>> referenceChartConsumer) {
        // There are no referenced charts...
    }

    @Override
    protected boolean stackValues(boolean hasComparisonPeriod) {
        return !hasComparisonPeriod;
    }

    @Override
    protected void computers(boolean hasComparisonPeriod,
                             boolean isComparisonPeriod,
                             Callback<TimeSeriesComputer<Object>> executor) throws Exception {
        EventTimeSeriesComputer<Object, UpdateClickEvent> computer =
                new EventTimeSeriesComputer<>(UpdateClickEvent.class);
        if (hasComparisonPeriod) {
            computer.addAggregation(EventTimeSeriesComputer.AGGREGATION_EXPRESSION_COUNT, null);
        } else {
            computer.addAggregation(EventTimeSeriesComputer.AGGREGATION_EXPRESSION_COUNT_LOGGED_IN,
                                    EventTimeSeriesComputer.LABEL_LOGGED_IN);
            computer.addAggregation(EventTimeSeriesComputer.AGGREGATION_EXPRESSION_COUNT_ANONYMOUS,
                                    EventTimeSeriesComputer.LABEL_ANONYMOUS);
        }

        executor.invoke(computer);
    }

    @Nonnull
    @Override
    public String getName() {
        return "GlobalNumberOfUpdateClicks";
    }

    @Override
    public int getPriority() {
        return 8500;
    }
}
