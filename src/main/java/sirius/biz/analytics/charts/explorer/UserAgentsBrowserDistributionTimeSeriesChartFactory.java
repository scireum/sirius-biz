/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.charts.explorer;

import sirius.kernel.commons.Callback;
import sirius.kernel.commons.Strings;

/**
 * Lays the foundation of browser distribution based time series charts, by providing the base sql which extracts the
 * browser distribution from the user agent data based on a Clickhouse event and additional conditions.
 */
public abstract class UserAgentsBrowserDistributionTimeSeriesChartFactory extends UserAgentsTimeSeriesChartFactory {
    @Override
    protected void computers(boolean hasComparisonPeriod,
                             boolean isComparisonPeriod,
                             Callback<TimeSeriesComputer<Object>> executor) throws Exception {
        if (isComparisonPeriod) {
            return;
        }

        executor.invoke(new UserAgentsBrowserDistributionTimeSeriesComputer<>(ignored -> getSqlQuery()));
    }

    @Override
    protected final String getSqlQuery() {
        return Strings.apply(
                // language=SQL
                """
                        SELECT %s, %s, %s, %s, %s, YEAR(eventDate) as year, MONTH(eventDate) AS month [:daily , DAY(eventDate) AS day]
                        FROM %s
                        WHERE %s AND eventDate >= ${start} AND eventDate <= ${end}
                        GROUP BY [:daily DAY(eventDate), ] MONTH(eventDate), YEAR(eventDate)""",
                UserAgentsBrowserDistributionTimeSeriesComputer.COUNT_FIREFOX,
                UserAgentsBrowserDistributionTimeSeriesComputer.COUNT_CHROME,
                UserAgentsBrowserDistributionTimeSeriesComputer.COUNT_INTERNET_EXPLORER,
                UserAgentsBrowserDistributionTimeSeriesComputer.COUNT_SAFARI,
                UserAgentsBrowserDistributionTimeSeriesComputer.COUNT_EDGE,
                getEventName(),
                getConditions());
    }
}
