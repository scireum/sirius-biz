/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.charts.explorer;

import sirius.biz.analytics.events.EventRecorder;
import sirius.kernel.di.std.Part;

import java.util.function.Function;

/**
 * Helps computing the user-agent distribution for a given Clickhouse event.
 * <p>
 * Note that the query has to be built manually, but helpful constants are provided in each child class.
 * <p>
 * Also note, that building SQL queries from multiple strings can be dangerous. Great care should be taken to
 * only add constant strings to the query. Everything else must be passed in as parameter via
 * {@code  SQLQuery.set("key", value)}
 *
 * @param <O> the type of entities expected by this computer
 */
public abstract class UserAgentsTimeSeriesComputer<O> implements TimeSeriesComputer<O> {
    @Part
    protected static EventRecorder eventRecorder;

    protected final Function<O, String> queryBuilder;

    /**
     * Constructor of the class.
     *
     * @param queryBuilder a function which returns a sql query. The sql query will be used to query the time series
     *                     for each browser. Note: Always use safe mechanisms when building the query. Especially when
     *                     user input has to be included it is highly advised to use {{@link sirius.db.jdbc.SQLQuery#set(String, Object)}}.
     */
    protected UserAgentsTimeSeriesComputer(Function<O, String> queryBuilder) {
        this.queryBuilder = queryBuilder;
    }
}
