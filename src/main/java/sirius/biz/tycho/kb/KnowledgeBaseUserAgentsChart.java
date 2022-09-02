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
import sirius.biz.analytics.charts.explorer.TimeSeriesChartFactory;
import sirius.biz.analytics.charts.explorer.TimeSeriesComputer;
import sirius.biz.analytics.charts.explorer.UserAgentsTimeSeriesComputer;
import sirius.biz.jobs.StandardCategories;
import sirius.biz.tenants.TenantUserManager;
import sirius.kernel.commons.Callback;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Implements a time series chart for the knowledge base.
 *
 * The chart visualizes the browser which have been used in order to access the knowledge base by extracting the required data from the user agents dataset.
 */
@Register(framework = KnowledgeBase.FRAMEWORK_KNOWLEDGE_BASE)
@Permission(TenantUserManager.PERMISSION_SYSTEM_TENANT_MEMBER)
public class KnowledgeBaseUserAgentsChart extends TimeSeriesChartFactory<Object> {

    private static final String SQL_QUERY = Strings.apply(// language=SQL
                                                          """
                                                                  SELECT %s, %s, %s, %s, %s, YEAR(eventDate) as year, MONTH(eventDate) AS month [:daily , DAY(eventDate) AS day]
                                                                  FROM pageimpressionevent
                                                                  WHERE aggregationUri = '/kba' AND eventDate >= ${start} AND eventDate <= ${end}
                                                                  GROUP BY [:daily DAY(eventDate), ] MONTH(eventDate), YEAR(eventDate)                                                     
                                                                  """,
                                                          UserAgentsTimeSeriesComputer.COUNT_FIREFOX,
                                                          UserAgentsTimeSeriesComputer.COUNT_CHROME,
                                                          UserAgentsTimeSeriesComputer.COUNT_INTERNET_EXPLORER,
                                                          UserAgentsTimeSeriesComputer.COUNT_SAFARI,
                                                          UserAgentsTimeSeriesComputer.COUNT_EDGE);

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

        executor.invoke(new UserAgentsTimeSeriesComputer<>(ignored -> SQL_QUERY));
    }

    @Nonnull
    @Override
    public String getName() {
        return "GlobalKnowledgeBaseUserAgentsChart";
    }

    @Override
    public int getPriority() {
        return 900;
    }

    @Override
    protected boolean stackValues() {
        return true;
    }
}
