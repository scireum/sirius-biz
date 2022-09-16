/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.charts.explorer;

import sirius.biz.tycho.DashboardUserAgentsBrowserDistributionChart;
import sirius.db.jdbc.SQLQuery;
import sirius.kernel.commons.Limit;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.function.Function;

/**
 * Helps computing the platform distribution from the user agent data for a given Clickhouse event.
 * <p>
 * Note that the query has to be built manually, but helpful constants are provided below. Note that the query
 * has to yield the expected fields: <tt>year</tt>, <tt>month</tt>, <tt>day</tt>, and the values as created by
 * expressions as provided below.
 * <p>
 * Also note, that building SQL queries from multiple strings can be dangerous. Great care should be taken to
 * only add constant strings to the query. Everything else must be passed in as parameter via {@code  SQLQuery.set("key", value)}
 *
 * @param <O> the type of entities expected by this computer
 * @see DashboardUserAgentsBrowserDistributionChart
 */
public class UserAgentsPlatformDistributionTimeSeriesComputer<O> extends UserAgentsTimeSeriesComputer<O> {
    /**
     * Could be used within a sql query in order to retrieve the number of occurrences of the iOS platform within the user agent data.
     */
    public static final String COUNT_IOS =
            "countIf(position('Mac' in webData_userAgent) > 0 and position('Mobile' in webData_userAgent) > 0 or position('EdgiOS' in webData_userAgent) > 0) as ios";

    /**
     * Could be used within a sql query in order to retrieve the number of occurrences of the Android platform within the user agent data.
     */
    public static final String COUNT_ANDROID =
            "countIf(position('Android' in webData_userAgent) > 0 or position('EdgA' in webData_userAgent) > 0) as android";

    /**
     * Could be used within a sql query in order to retrieve the number of occurrences of the Macintosh desktop platform within the user agent data.
     */
    public static final String COUNT_MACINTOSH =
            "countIf(position('Mac' in webData_userAgent) > 0 and position('Mobile' in webData_userAgent) == 0) as macintosh";

    /**
     * Could be used within a sql query in order to retrieve the number of occurrences of the Windows desktop platform within the user agent data.
     */
    public static final String COUNT_WINDOWS =
            "countIf(position('Win' in webData_userAgent) > 0 and position('Mobile' in webData_userAgent) == 0) as windows";

    /**
     * Could be used within a sql query in order to retrieve the number of occurrences of the Linux desktop platform within the user agent data.
     */
    public static final String COUNT_LINUX =
            "countIf(position('Linux' in webData_userAgent) > 0 and position('Mobile' in webData_userAgent) == 0) as linux";

    /**
     * Could be used within a sql query in order to retrieve the number of occurrences of the Unix desktop platform within the user agent data.
     */
    public static final String COUNT_UNIX =
            "countIf(position('X11' in webData_userAgent) > 0 and position('Mobile' in webData_userAgent) == 0) as unix";

    /**
     * Constructor of the class.
     *
     * @param queryBuilder a function which returns a sql query. The sql query will be used to query the time series
     *                     for each browser. Note: Always use safe mechanisms when building the query. Especially when
     *                     user input has to be included it is highly advised to use {{@link SQLQuery#set(String, Object)}}.
     */
    public UserAgentsPlatformDistributionTimeSeriesComputer(Function<O, String> queryBuilder) {
        super(queryBuilder);
    }

    @Override
    public void compute(@Nullable O object, TimeSeries timeseries) throws Exception {
        TimeSeriesData desktop = timeseries.createData("Desktop");
        TimeSeriesData android = timeseries.createData("Android");
        TimeSeriesData ios = timeseries.createData("iOS");

        eventRecorder.createQuery(queryBuilder.apply(object))
                     .set("daily", timeseries.getGranularity() == Granularity.DAY)
                     .set("start", timeseries.getStart())
                     .set("end", timeseries.getEnd())
                     .iterateAll(row -> {
                         LocalDate localDate = LocalDate.of(row.getValue("year").asInt(0),
                                                            row.getValue("month").asInt(0),
                                                            row.tryGetValue("day").asInt(1));
                         desktop.addValue(localDate, row.getValue("desktop").asDouble(0));
                         android.addValue(localDate, row.getValue("android").asDouble(0));
                         ios.addValue(localDate, row.getValue("ios").asDouble(0));
                     }, Limit.UNLIMITED);
    }
}
