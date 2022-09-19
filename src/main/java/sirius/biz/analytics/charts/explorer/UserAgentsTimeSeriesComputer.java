/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.charts.explorer;

import sirius.biz.analytics.events.Event;
import sirius.db.jdbc.SmartQuery;

import javax.annotation.Nullable;
import java.util.function.BiConsumer;

/**
 * Helps computing the user-agent distribution for a given Clickhouse event.
 *
 * @param <O> the type of entities expected by this computer
 * @param <E> the type of events being queried
 * @see sirius.biz.tycho.DashboardUserAgentsChart
 */
public class UserAgentsTimeSeriesComputer<O, E extends Event> extends EventTimeSeriesComputer<O, E> {

    /**
     * Retrieves the number of occurrences of the Firefox browser within the user agent data.
     */
    private static final String AGGREGATION_EXPRESSION_FIREFOX =
            "countIf(position('Firefox' in webData_userAgent) > 0)";

    /**
     * Retrieves the number of occurrences of the Chrome browser within the user agent data.
     */
    private static final String AGGREGATION_EXPRESSION_CHROME =
            "countIf(position('Chrome' in webData_userAgent) > 0 and position('Edg' in webData_userAgent) == 0)";

    /**
     * Retrieves the number of occurrences of the Internet Explorer browser within the user agent data.
     */
    private static final String AGGREGATION_EXPRESSION_INTERNET_EXPLORER =
            "countIf(position('MSIE' in webData_userAgent) > 0 or position('Trident' in webData_userAgent) > 0)";

    /**
     * Retrieves the number of occurrences of the Safari browser within the user agent data.
     */
    private static final String AGGREGATION_EXPRESSION_SAFARI =
            "countIf(position('Safari' in webData_userAgent) > 0 and position('Chrome' in webData_userAgent) == 0 and position('Edg' in webData_userAgent) == 0)";

    /**
     * Retrieves the number of occurrences of the Edge browser within the user agent data.
     */
    private static final String AGGREGATION_EXPRESSION_EDGE =
            "countIf(position('Edg' in webData_userAgent) > 0)";

    /**
     * Creates a new computer for the given event type which customizes the query based on a given object.
     *
     * @param eventType       the events to query
     * @param queryCustomizer an optional customizer which adapts the query based on the selected data object
     */
    public UserAgentsTimeSeriesComputer(Class<E> eventType, @Nullable BiConsumer<O, SmartQuery<E>> queryCustomizer) {
        super(eventType, queryCustomizer);
        addAggregation(AGGREGATION_EXPRESSION_FIREFOX, "Firefox");
        addAggregation(AGGREGATION_EXPRESSION_CHROME, "Chrome");
        addAggregation(AGGREGATION_EXPRESSION_INTERNET_EXPLORER, "Internet Explorer");
        addAggregation(AGGREGATION_EXPRESSION_SAFARI, "Safari");
        addAggregation(AGGREGATION_EXPRESSION_EDGE, "Edge");
    }
}
