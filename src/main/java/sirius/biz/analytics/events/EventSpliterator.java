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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;

/// Provides a spliterator which fetches events blockwise.
///
/// The spliterator is pull-based and fetches the next block of events when the current block is exhausted. Because
/// events have no natural order or distinct fields by default and multiple events of the same type can be recorded at
/// the same time, the next block of events is fetched starting with the timestamp of the last event of the last block.
/// To prevent fetching the same events multiple times, a "duplicate preventer" is used to add additional constraints to
/// the query to prevent fetching the same events multiple times.
///
/// The duplicate preventer is a consumer which is supplied with the query and the last events of the previous block.
/// The given query must be modified by the preventer to prevent fetching the same events again based on fields
/// that are unique to the event type, e.g. [UserData#USER_ID] in case of [user events][UserEvent].
///
/// Note, that only the last events of the previous block (meaning every event that shares the same timestamp as the
/// last event of the block) are supplied to the preventer as all preceding events will not be fetched again due to the
/// next fetch starting with the timestamp of the last event of the block.
///
/// Implementations of the duplicate preventer should in most cases add an 'AND NOT (A=X AND B=Y AND ...)' constraint
/// to the query based on the supplied events. Here, A and B are fields specific to the event type while X and Y
/// are the values of the supplied event. The combination of the fields and values should take care of not fetching the
/// same events multiple times. See [EventRecorder#fetchUserEventsBlockwise(SmartQuery)] for an example where the fields
/// [UserData#USER_ID] and [Event#EVENT_TIMESTAMP] are used to prevent fetching the same event triggered by the
/// same user at the same time again.
///
/// @param <E> the type of the events to fetch
public class EventSpliterator<E extends Event<E>> extends PullBasedSpliterator<E> {

    private static final int BLOCK_SIZE = 1000;

    private final SmartQuery<E> query;

    /// Contains the last events fetched.
    private final ArrayList<E> lastEvents = new ArrayList<>();

    /// Contains a consumer which is used to prevent fetching the same events multiple times.
    private final BiConsumer<SmartQuery<E>, List<E>> duplicatePreventer;

    /// Creates a new spliterator for the given query and duplicate preventer.
    ///
    /// The given will be copied to allow re-use by the caller. Additionally, the given query does not need to
    /// provide ordering or limits as this is handled by the spliterator itself.
    ///
    /// The given duplicate preventer must add additional constraints to the query to prevent fetching the same events
    /// multiple times.
    ///
    /// @param query              the query to use to fetch the events
    /// @param duplicatePreventer a consumer which is used to prevent fetching the same events multiple times
    /// @see EventSpliterator the class description for more information
    public EventSpliterator(SmartQuery<E> query, BiConsumer<SmartQuery<E>, List<E>> duplicatePreventer) {
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

        List<E> events = resolveEffectiveQuery().queryList();
        if (events.isEmpty()) {
            return null;
        }

        if (!lastEvents.isEmpty() && !lastEvents.getLast()
                                                .getEventTimestamp()
                                                .equals(events.getLast().getEventTimestamp())) {
            lastEvents.clear();
        }

        lastEvents.addAll(events);

        return events.iterator();
    }

    @Override
    public int characteristics() {
        return NONNULL | IMMUTABLE | ORDERED;
    }

    private SmartQuery<E> resolveEffectiveQuery() {
        SmartQuery<E> effectiveQuery = query.copy();

        if (!lastEvents.isEmpty()) {
            effectiveQuery.where(OMA.FILTERS.gte(Event.EVENT_TIMESTAMP, lastEvents.getLast().getEventTimestamp()));
            duplicatePreventer.accept(effectiveQuery,
                                      lastEvents.reversed()
                                                .stream()
                                                .filter(event -> event.getEventTimestamp()
                                                                      .equals(lastEvents.getLast().getEventTimestamp()))
                                                .toList());
        }

        return effectiveQuery;
    }
}
