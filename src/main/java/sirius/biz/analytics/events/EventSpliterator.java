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
import sirius.db.jdbc.constraints.SQLConstraint;
import sirius.db.mixing.Mapping;
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
/// Event deduplication may result in complex queries, which can potentially slow down performance or generate queries
/// that are too large to process. Therefore, if individual events are not needed, using
/// [metrics][sirius.kernel.health.metrics.Metric] may be a better choice for fetching aggregated data.
///
/// @param <E> the type of the events to fetch
public class EventSpliterator<E extends Event<E>> extends PullBasedSpliterator<E> {

    private static final int BLOCK_SIZE = 1000;

    private final SmartQuery<E> query;

    /// Contains the last events fetched.
    private final List<E> lastEvents = new ArrayList<>();

    /// Contains a consumer which is used to prevent fetching the same events multiple times.
    private final BiConsumer<SmartQuery<E>, List<E>> duplicatePreventer;

    /// Creates a new spliterator for the given query and duplicate preventer.
    ///
    /// The given query will be copied to allow re-use by the caller. Additionally, the given query does not need to
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

    /// Creates a new spliterator for the given query and considers the given fields as distinct fields to prevent
    /// duplicates.
    ///
    /// The given query will be copied to allow re-use by the caller. Additionally, the given query does not need to
    /// provide ordering or limits as this is handled by the spliterator itself.
    ///
    /// The given fields will be used to ignore events where the timestamp plus all distinct fields match those of one
    /// of the previously fetched events.
    ///
    /// The duplicate preventing portion of the SQL query will have the form:
    /// ```sql
    /// AND NOT (timestamp = last_timestamp -- Only constraint events with the same timestamp
    ///         AND ((field1 = event_1_field_1 AND field2 = event_1_field_2 AND ...) -- Ignore already fetched event 1
    ///             OR (field1 = event_2_field_1 AND field2 = event_2_field_2 AND ...) -- Ignore already fetched event 2
    ///             OR ...))
    ///```
    ///
    /// @param query          the query to use to fetch the events
    /// @param distinctFields the fields to consider when preventing duplicates
    /// @see EventSpliterator the class description for more information
    public EventSpliterator(SmartQuery<E> query, List<Mapping> distinctFields) {
        this(query, (effectiveQuery, events) -> {
            effectiveQuery.where(createDuplicatePreventerConstraint(events, distinctFields));
        });
    }

    private static <E extends Event<E>> SQLConstraint createDuplicatePreventerConstraint(List<E> events,
                                                                                         List<Mapping> distinctFields) {
        return OMA.FILTERS.not(OMA.FILTERS.and(OMA.FILTERS.eq(Event.EVENT_TIMESTAMP,
                                                              events.getLast().getEventTimestamp()),
                                               createEventsConstraint(events, distinctFields)));
    }

    private static <E extends Event<E>> SQLConstraint createEventsConstraint(List<E> events,
                                                                             List<Mapping> distinctFields) {
        return OMA.FILTERS.or(events.stream().map(event -> createFieldsConstraint(event, distinctFields)).toList());
    }

    private static <E extends Event<E>> SQLConstraint createFieldsConstraint(E event, List<Mapping> distinctFields) {
        return OMA.FILTERS.and(distinctFields.stream().map(field -> {
            return OMA.FILTERS.eq(field, event.getDescriptor().findProperty(field.getName()).getValue(event));
        }).toList());
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
