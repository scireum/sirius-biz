/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.statistics;

import com.google.common.collect.Sets;

import java.util.Set;

/**
 * Identifies an event for which statistics can be recorded.
 * <p>
 * The event also determines the lowest (finest) {@link AggregationLevel} for which data is recorded. Higher levels are
 * automatically computed by aggregation.
 */
public class StatisticalEvent {

    private final String eventName;
    private final AggregationLevel finestAggregationLevel;

    private static final Set<String> usedIds = Sets.newTreeSet();

    private StatisticalEvent(String eventName, AggregationLevel finestAggregationLevel) {
        this.eventName = eventName;
        this.finestAggregationLevel = finestAggregationLevel;
    }

    /**
     * Creates a new event which can be used to increment statistics.
     * <p>
     * The event is used to determine the lowest {@link AggregationLevel} and can be passed to increment statistics.
     *
     * @param name                   the unique name of the event stored in the database
     * @param finestAggregationLevel the lowes aggregation level used by this event. Higher levels will be computed
     *                               automatically.
     * @return the created event
     * @see Statistics#addStatistic(StatisticalEvent, String, int)
     * @see Statistics#incrementStatistic(StatisticalEvent, String)
     */
    public static StatisticalEvent create(String name, AggregationLevel finestAggregationLevel) {
        if (usedIds.contains(name)) {
            throw new IllegalArgumentException("Statistical ID already in use: " + name);
        }
        usedIds.add(name);
        return new StatisticalEvent(name, finestAggregationLevel);
    }

    /**
     * Returns the unique event name.
     *
     * @return the name of the event in the database
     */
    public String getEventName() {
        return eventName;
    }

    /**
     * Returns the finest aggregation level used by this event.
     *
     * @return the finest (lowest) aggregation level stored for this event
     */
    public AggregationLevel getFinestAggregationLevel() {
        return finestAggregationLevel;
    }
}
