/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.charts.explorer;

import sirius.biz.analytics.events.EventRecorder;
import sirius.biz.tycho.DashboardUserAgentsBrowserDistributionChart;
import sirius.kernel.commons.Limit;
import sirius.kernel.di.std.Part;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.function.Function;

/**
 * Helps computing the user-agent distribution for a given Clickhouse event.
 * <p>
 * Note that the query has to be built manually, but helpful constants are provided below. Note that the query
 * has to yield the expected fields: <tt>year</tt>, <tt>month</tt>, <tt>day</tt>, and the values as created by
 * expressions as provided below.
 * <p>
 * Also note, that building SQL queries from multiple strings can be dangerous. Great care should be taken to
 * only add constant strings to the query. Everything else must be passed in as parameter via {@code  SQLQuery.set("key", value)}
 *
 * @see DashboardUserAgentsBrowserDistributionChart
 *
 * @param <O> the type of entities expected by this computer
 */
public class UserAgentsTimeSeriesComputer<O> implements TimeSeriesComputer<O> {
    /**
     * Could be used within a sql query in order to retrieve the number of occurrences of the Firefox browser within the user agent data.
     */
    public static final String COUNT_FIREFOX = "countIf(position('Firefox' in webData_userAgent) > 0) as firefox";

    /**
     * Could be used within a sql query in order to retrieve the number of occurrences of the Chrome browser within the user agent data.
     */
    public static final String COUNT_CHROME =
            "countIf(position('Chrome' in webData_userAgent) > 0 and position('Edg' in webData_userAgent) == 0) as chrome";

    /**
     * Could be used within a sql query in order to retrieve the number of occurrences of the Internet Explorer browser within the user agent data.
     */
    public static final String COUNT_INTERNET_EXPLORER =
            "countIf(position('MSIE' in webData_userAgent) > 0 or position('Trident' in webData_userAgent) > 0) as internetExplorer";

    /**
     * Could be used within a sql query in order to retrieve the number of occurrences of the Safari browser within the user agent data.
     */
    public static final String COUNT_SAFARI =
            "countIf(position('Safari' in webData_userAgent) > 0 and position('Chrome' in webData_userAgent) == 0 and position('Edg' in webData_userAgent) == 0) as safari";

    /**
     * Could be used within a sql query in order to retrieve the number of occurrences of the Edge browser within the user agent data.
     */
    public static final String COUNT_EDGE = "countIf(position('Edg' in webData_userAgent) > 0) as edge";

    @Part
    private static EventRecorder eventRecorder;

    private final Function<O, String> queryBuilder;

    /**
     * Constructor of the class.
     *
     * @param queryBuilder a function which returns a sql query. The sql query will be used to query the time series
     *                     for each browser. Note: Always use safe mechanisms when building the query. Especially when
     *                     user input has to be included it is highly advised to use {{@link sirius.db.jdbc.SQLQuery#set(String, Object)}}.
     */
    public UserAgentsTimeSeriesComputer(Function<O, String> queryBuilder) {
        this.queryBuilder = queryBuilder;
    }

    @Override
    public void compute(@Nullable O object, TimeSeries timeseries) throws Exception {
        TimeSeriesData firefox = timeseries.createData("Firefox");
        TimeSeriesData chrome = timeseries.createData("Chrome");
        TimeSeriesData internetExplorer = timeseries.createData("Internet Explorer");
        TimeSeriesData safari = timeseries.createData("Safari");
        TimeSeriesData edge = timeseries.createData("Edge");

        eventRecorder.createQuery(queryBuilder.apply(object))
                     .set("daily", timeseries.getGranularity() == Granularity.DAY)
                     .set("start", timeseries.getStart())
                     .set("end", timeseries.getEnd())
                     .iterateAll(row -> {
                         LocalDate localDate = LocalDate.of(row.getValue("year").asInt(0),
                                                            row.getValue("month").asInt(0),
                                                            row.tryGetValue("day").asInt(1));
                         firefox.addValue(localDate, row.getValue("firefox").asDouble(0));
                         chrome.addValue(localDate, row.getValue("chrome").asDouble(0));
                         internetExplorer.addValue(localDate, row.getValue("internetExplorer").asDouble(0));
                         safari.addValue(localDate, row.getValue("safari").asDouble(0));
                         edge.addValue(localDate, row.getValue("edge").asDouble(0));
                     }, Limit.UNLIMITED);
    }
}
