/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.events;

import sirius.db.jdbc.OMA;
import sirius.db.jdbc.SmartQuery;
import sirius.kernel.async.TaskContext;
import sirius.kernel.commons.PullBasedSpliterator;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Provides a spliterator which fetches events blockwise.
 *
 * @param <E> the type of events to fetch
 */
public class EventSpliterator<E extends Event<E>> extends PullBasedSpliterator<E> {

    private static final int BLOCK_SIZE = 1000;

    private final SmartQuery<E> query;

    /**
     * Contains the last events fetched (all sharing the same {@linkplain Event#getEventTimestamp() timestamp}) to
     * prevent fetching the same events multiple times.
     */
    private List<E> lastEvents;

    /**
     * Contains a consumer which is used to prevent fetching the same events multiple times.
     */
    private final BiConsumer<SmartQuery<E>, E> duplicatePreventer;

    /**
     * Creates a new spliterator for the given query and duplicate preventer.
     * <p>
     * The duplicate preventer is supposed to add additional constraints to the query to prevent fetching the same
     * events multiple times.
     *
     * @param query              the query to use to fetch the events
     * @param duplicatePreventer a consumer which is used to prevent fetching the same events multiple times
     */
    public EventSpliterator(SmartQuery<E> query, BiConsumer<SmartQuery<E>, E> duplicatePreventer) {
        super();
        this.query = query.copy().orderAsc(Event.EVENT_TIMESTAMP).limit(BLOCK_SIZE);
        this.duplicatePreventer = duplicatePreventer;
    }

    @Nullable
    @Override
    protected Iterator<E> pullNextBlock() {
        if (!TaskContext.get().isActive()) {
            return null;
        }

        if (lastEvents != null && lastEvents.size() < BLOCK_SIZE) {
            return null;
        }

        List<E> events = resolveEffectiveQuery().queryList();
        if (events.isEmpty()) {
            return null;
        }

        lastEvents = events.reversed()
                           .stream()
                           .filter(event -> event.getEventTimestamp().equals(events.getLast().getEventTimestamp()))
                           .toList();

        return events.iterator();
    }

    @Override
    public int characteristics() {
        return NONNULL | IMMUTABLE | ORDERED;
    }

    private SmartQuery<E> resolveEffectiveQuery() {
        SmartQuery<E> effectiveQuery = query.copy();

        if (lastEvents != null) {
            effectiveQuery.where(OMA.FILTERS.gte(Event.EVENT_TIMESTAMP, lastEvents.getFirst().getEventTimestamp()));
            lastEvents.forEach(lastEvent -> duplicatePreventer.accept(effectiveQuery, lastEvent));
        }

        return effectiveQuery;
    }
}
