/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.kb;

import sirius.biz.analytics.charts.explorer.ChartFactory;
import sirius.biz.analytics.charts.explorer.ChartObjectResolver;
import sirius.biz.analytics.charts.explorer.Granularity;
import sirius.biz.analytics.charts.explorer.TimeSeriesChartFactory;
import sirius.biz.analytics.charts.explorer.TimeSeriesComputer;
import sirius.biz.analytics.charts.explorer.TimeSeriesData;
import sirius.biz.analytics.events.EventRecorder;
import sirius.biz.jobs.StandardCategories;
import sirius.biz.tenants.TenantUserManager;
import sirius.kernel.commons.Callback;
import sirius.kernel.commons.Limit;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.function.Consumer;

/**
 * Shows the views of a {@link KnowledgeBaseEntry} based on recorded
 * {@link sirius.biz.analytics.events.PageImpressionEvent page impression events}.
 */
@Register(framework = KnowledgeBase.FRAMEWORK_KNOWLEDGE_BASE)
@Permission(TenantUserManager.PERMISSION_SYSTEM_TENANT_MEMBER)
public class KnowledgeBaseEntryViewsChart extends TimeSeriesChartFactory<KnowledgeBaseEntry> {

    @Part
    private EventRecorder eventRecorder;

    @Nullable
    @Override
    protected Class<? extends ChartObjectResolver<KnowledgeBaseEntry>> getResolver() {
        return KnowledgeBaseEntryResolver.class;
    }

    @Override
    public String getCategory() {
        return StandardCategories.MONITORING;
    }

    @Override
    protected void collectReferencedCharts(Consumer<Class<? extends ChartFactory<KnowledgeBaseEntry>>> referenceChartConsumer) {
        // There are no referenced charts...
    }

    @Override
    protected boolean stackValues(boolean hasComparisonPeriod) {
        return !hasComparisonPeriod;
    }

    @Override
    protected void computers(boolean hasComparisonPeriod,
                             boolean isComparisonPeriod,
                             Callback<TimeSeriesComputer<KnowledgeBaseEntry>> executor) throws Exception {
        executor.invoke((object, timeSeries) -> {
            TimeSeriesData all = hasComparisonPeriod ? timeSeries.createDefaultData() : null;
            TimeSeriesData loggedIn = hasComparisonPeriod ? null : timeSeries.createData("$TimeSeries.loggedIn");
            TimeSeriesData anonymous = hasComparisonPeriod ? null : timeSeries.createData("$TimeSeries.anonymous");
            eventRecorder.createQuery(//language=SQL
                                      """
                                              SELECT count(*) AS all,
                                                     countIf(userData_userId IS NOT NULL) AS loggedIn,
                                                     countIf(userData_userId IS NULL) AS anonymous,
                                                     YEAR(eventDate) as year,
                                                     MONTH(eventDate) AS month
                                                     [:daily , DAY(eventDate) AS day]
                                              FROM pageimpressionevent
                                              WHERE aggregationUri = '/kba'
                                                AND dataObject = ${entry}
                                                AND eventDate >= ${start}
                                                AND eventDate <= ${end}
                                              GROUP BY [:daily DAY(eventDate), ] MONTH(eventDate), YEAR(eventDate)
                                              """)
                         .set("daily", timeSeries.getGranularity() == Granularity.DAY)
                         .set("entry", object.getUniqueName())
                         .set("start", timeSeries.getStart())
                         .set("end", timeSeries.getEnd())
                         .iterateAll(row -> {
                             LocalDate localDate = LocalDate.of(row.getValue("year").asInt(0),
                                                                row.getValue("month").asInt(0),
                                                                row.tryGetValue("day").asInt(1));
                             if (all != null) {
                                 all.addValue(localDate, row.getValue("all").asDouble(0d));
                             }
                             if (loggedIn != null) {
                                 loggedIn.addValue(localDate, row.getValue("loggedIn").asDouble(0d));
                             }
                             if (anonymous != null) {
                                 anonymous.addValue(localDate, row.getValue("anonymous").asDouble(0d));
                             }
                         }, Limit.UNLIMITED);
        });
    }

    @Nonnull
    @Override
    public String getName() {
        return "KnowledgeBaseEntryViews";
    }

    @Override
    public int getPriority() {
        return 8210;
    }
}
