/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.charts.explorer;

import sirius.biz.analytics.events.Event;
import sirius.kernel.commons.Callback;

/**
 * Lays the foundation of browser distribution based time series charts, by providing the base sql which extracts the
 * browser distribution from the user agent data based on a Clickhouse event and additional conditions.
 */
public abstract class BrowserDistributionTimeSeriesChartFactory<E extends Event>
        extends UserAgentsTimeSeriesChartFactory<E> {
    @Override
    protected void computers(boolean hasComparisonPeriod,
                             boolean isComparisonPeriod,
                             Callback<TimeSeriesComputer<Object>> executor) throws Exception {
        if (isComparisonPeriod) {
            return;
        }

        executor.invoke(new BrowserDistributionTimeSeriesComputer<>(getEvent(),
                                                                    (ignored, query) -> modifyQuery(query)));
    }
}
