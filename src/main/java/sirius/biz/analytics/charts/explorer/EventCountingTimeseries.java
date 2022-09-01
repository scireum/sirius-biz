/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.charts.explorer;

import sirius.biz.analytics.events.Event;

/**
 * Provides a simple {@link TimeseriesComputer} which just counts the occurrences of an {@link Event} in the given
 * time slice.
 *
 * @param <O> the type of entities being referenced by the chart
 */
public class EventCountingTimeseries<O> extends EventTimeseries<O> {

    /**
     * Creates a new counting series for the given entity type
     *
     * @param eventType the type of events to query
     */
    public EventCountingTimeseries(Class<? extends Event> eventType) {
        super(eventType);

        withSelect("count(*) as value", timeseries -> {
            TimeseriesData data = timeseries.createDefaultData();
            return (date, row) -> data.addValue(date, row.apply("value"));
        });
    }
}
