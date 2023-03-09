/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.explorer;

import sirius.biz.analytics.events.Event;
import sirius.db.jdbc.SmartQuery;
import sirius.kernel.commons.Strings;

import javax.annotation.Nullable;
import java.util.function.BiConsumer;

/**
 * Helps computing the platform distribution based on the user-agent data for a given Clickhouse event.
 *
 * @param <O> the type of entities expected by this computer
 * @param <E> the type of events being queried
 * @see sirius.biz.tycho.DashboardPlatformDistributionChart
 */
public class PlatformDistributionTimeSeriesComputer<O, E extends Event> extends EventTimeSeriesComputer<O, E> {
    /**
     * Retrieves the number of occurrences of the iOS platform within the user agent data.
     */
    public static final String AGGREGATION_EXPRESSION_IOS =
            "countIf(position('Mac' in webData_userAgent) > 0 and position('Mobile' in webData_userAgent) > 0 or position('EdgiOS' in webData_userAgent) > 0)";

    /**
     * Retrieves the number of occurrences of the Android platform within the user agent data.
     */
    public static final String AGGREGATION_EXPRESSION_ANDROID =
            "countIf(position('Android' in webData_userAgent) > 0 or position('EdgA' in webData_userAgent) > 0)";

    /**
     * Retrieves the number of occurrences of the Macintosh desktop platform within the user agent data.
     */
    public static final String AGGREGATION_EXPRESSION_MACINTOSH =
            "countIf(position('Mac' in webData_userAgent) > 0 and position('Mobile' in webData_userAgent) == 0)";

    /**
     * Retrieves the number of occurrences of the Windows desktop platform within the user agent data.
     */
    public static final String AGGREGATION_EXPRESSION_WINDOWS =
            "countIf(position('Win' in webData_userAgent) > 0 and position('Mobile' in webData_userAgent) == 0)";

    /**
     * Retrieves the number of occurrences of the Linux desktop platform within the user agent data.
     */
    public static final String AGGREGATION_EXPRESSION_LINUX =
            "countIf(position('Linux' in webData_userAgent) > 0 and position('Mobile' in webData_userAgent) == 0)";

    /**
     * Retrieves the number of occurrences of the Unix desktop platform within the user agent data.
     */
    public static final String AGGREGATION_EXPRESSION_UNIX =
            "countIf(position('X11' in webData_userAgent) > 0 and position('Mobile' in webData_userAgent) == 0)";

    /**
     * Retrieves the number of occurrences of desktop platforms within the user agent data.
     */
    public static final String AGGREGATION_EXPRESSION_DESKTOP = Strings.apply("plus(%s, plus(%s, plus(%s, %s)))",
                                                                              AGGREGATION_EXPRESSION_MACINTOSH,
                                                                              AGGREGATION_EXPRESSION_WINDOWS,
                                                                              AGGREGATION_EXPRESSION_LINUX,
                                                                              AGGREGATION_EXPRESSION_UNIX);

    /**
     * Creates a new computer for the given event type which customizes the query based on a given object.
     *
     * @param eventType       the events to query
     * @param queryCustomizer an optional customizer which adapts the query based on the selected data object
     */
    public PlatformDistributionTimeSeriesComputer(Class<E> eventType,
                                                  @Nullable BiConsumer<O, SmartQuery<E>> queryCustomizer) {
        super(eventType, queryCustomizer);
        addAggregation(AGGREGATION_EXPRESSION_IOS, "iOS");
        addAggregation(AGGREGATION_EXPRESSION_ANDROID, "Android");
        addAggregation(AGGREGATION_EXPRESSION_DESKTOP, "Desktop");
    }
}
