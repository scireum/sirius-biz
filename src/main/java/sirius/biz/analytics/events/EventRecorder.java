/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.events;

import sirius.db.jdbc.Database;
import sirius.db.jdbc.OMA;
import sirius.db.jdbc.SQLQuery;
import sirius.db.jdbc.SmartQuery;
import sirius.db.jdbc.batch.BatchContext;
import sirius.db.jdbc.batch.InsertQuery;
import sirius.db.jdbc.schema.Schema;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.Realm;
import sirius.kernel.Sirius;
import sirius.kernel.Startable;
import sirius.kernel.Stoppable;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.health.Log;
import sirius.kernel.health.metrics.Metric;
import sirius.kernel.health.metrics.MetricProvider;
import sirius.kernel.health.metrics.MetricsCollector;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Responsible for collecting and storing {@link Event events} for analytical and statistical purposes.
 * <p>
 * To minimize the impact on the running application and to maximize the performance, events are collected and queued.
 * This queue is batch processed in regular intervals (which greatly increases the performance of Clickhouse).
 * <p>
 * In case of a missing data store or a system overload condition (more events are generated than persisted), events
 * will be dropped as we favor system stability over perfect metrics.
 */
@Register(classes = {EventRecorder.class, Startable.class, Stoppable.class, MetricProvider.class})
public class EventRecorder implements Startable, Stoppable, MetricProvider {

    /**
     * Determines the max number of events to keep in the queue.
     */
    public static final int MAX_BUFFER_SIZE = 16 * 1024;

    /**
     * Determines the min number of events before an insertion run is performed.
     */
    private static final int MIN_BUFFER_SIZE = 1024;

    /**
     * Determines the max period until an insertion run is forced (independent of the buffer size).
     */
    private static final Duration MAX_BUFFER_AGE = Duration.ofMinutes(5);

    /**
     * Determines the max period to be used in development system.
     */
    private static final Duration MAX_BUFFER_AGE_DEV = Duration.ofSeconds(10);

    /**
     * Determines the max number of events to process in one insertion run.
     */
    private static final int MAX_EVENTS_PER_PROCESS = 16 * 1024;

    private static final String AGGREGATION_COUNTER = "counter";
    private static final String AGGREGATION_SUM = "summation";
    private static final String AGGREGATION_DISTINCT_COUNT = "distinctCount";

    private LocalDateTime lastProcessed;
    private final AtomicInteger bufferedEvents = new AtomicInteger();
    private final Queue<Event<?>> buffer = new ConcurrentLinkedQueue<>();

    @Part
    private Schema schema;

    @Part
    private OMA oma;

    private volatile boolean configured;
    private Database database;

    @Override
    public int getPriority() {
        return 900;
    }

    @Override
    public void started() {
        schema.getReadyFuture().onSuccess(() -> {
            String realm = Event.class.getAnnotation(Realm.class).value();
            configured = schema.isConfigured(realm);
            if (configured) {
                database = schema.getDatabase(realm);
            }
        });
    }

    @Override
    public void stopped() {
        if (configured) {
            process();
            configured = false;
        }
    }

    @Override
    public void gather(MetricsCollector metricsCollector) {
        metricsCollector.metric("events_buffer_usage",
                                "events_buffer_usage",
                                "Event Buffer Usage",
                                100 * bufferedEvents.doubleValue() / MAX_BUFFER_SIZE,
                                Metric.UNIT_PERCENT);
    }

    /**
     * Determines if a valid database configuration is present.
     *
     * @return <tt>true</tt> if a valid configuration is present, <tt>false</tt> otherwise
     */
    public boolean isConfigured() {
        return configured;
    }

    /**
     * Returns the database which is used to store events in.
     *
     * @return the database used to talk to the <b>Clickhouse</b> server which stores the events. Might be
     * <tt>null</tt> if no database is configured. Therefore {@link #isConfigured()} should most probably
     * be checked before using it
     */
    @Nullable
    public Database getDatabase() {
        return database;
    }

    /**
     * Directly creates a new query on the datastore used for events.
     * <p>
     * This is a Clickhouse database and the given query is automatically marked as potentially long running to
     * suppress warnings if the query takes longer than a usual SQL query (which is kind of expected for large
     * data warehouses).
     *
     * @param sql the SQL to execute. Note that this should be a constant string as parameters can later be passed in
     *            using {@link SQLQuery#set(String, Object)}
     * @return the given SQL as query based on the given SQL. Might be <tt>null</tt> if no database is configured.
     * Therefore {@link #isConfigured()} should most probably be checked before using it
     */
    @Nullable
    public SQLQuery createQuery(String sql) {
        if (database == null) {
            return null;
        }

        return getDatabase().createQuery(sql).markAsLongRunning();
    }

    /**
     * Counts the number of events which have occurred based on the given <tt>queryTuner</tt>.
     * <p>
     * This automatically marks the query as long-running.
     *
     * @param eventType  the type of events to query
     * @param queryTuner the actual filter to apply. Note that {@link Event#EVENT_DATE} should be filtered, as otherwise
     *                   the performance will be catastrophic.
     * @param <E>        the generic types of the entities to query
     * @return the number of events matching the given filter. Note that we return an <tt>int</tt> here to better match
     * the API of {@link sirius.kernel.health.metrics.Metrics}.
     * @throws SQLException in case of a database error
     * @see #countEventsInRange(Class, LocalDateTime, LocalDateTime, Consumer)
     */
    public <E extends Event<E>> int countEvents(Class<E> eventType, @Nullable Consumer<SmartQuery<E>> queryTuner)
            throws SQLException {
        SmartQuery<E> query = oma.select(eventType).aggregationField("count(*) AS " + AGGREGATION_COUNTER);
        if (queryTuner != null) {
            queryTuner.accept(query);
        }

        return query.asSQLQuery()
                    .markAsLongRunning()
                    .first()
                    .flatMap(row -> row.getValue(AGGREGATION_COUNTER).asOptionalInt())
                    .orElse(0);
    }

    /**
     * Counts the number of events which have occurred based on the given <tt>queryTuner</tt> and time range.
     * <p>
     * This automatically marks the query as long-running.
     *
     * @param eventType  the type of events to query
     * @param startDate  the start date of the range
     * @param endDate    the end date of the range
     * @param queryTuner the actual filter to apply
     * @param <E>        the generic types of the entities to query
     * @return the number of events matching the given filter. Note that we return an <tt>int</tt> here to better match
     * the API of {@link sirius.kernel.health.metrics.Metrics}.
     * @throws SQLException in case of a database error
     * @see #countEvents(Class, Consumer)
     */
    public <E extends Event<E>> int countEventsInRange(Class<E> eventType,
                                                       LocalDateTime startDate,
                                                       LocalDateTime endDate,
                                                       @Nullable Consumer<SmartQuery<E>> queryTuner)
            throws SQLException {
        return countEvents(eventType, query -> {
            query.where(OMA.FILTERS.gte(Event.EVENT_DATE, startDate.toLocalDate()));
            query.where(OMA.FILTERS.lte(Event.EVENT_DATE, endDate.toLocalDate()));
            if (queryTuner != null) {
                queryTuner.accept(query);
            }
        });
    }

    /**
     * Counts the number of events which have occurred in the given calendar month based on the given <tt>queryTuner</tt>.
     * <p>
     * This method counts the number of events which have occurred in the given calendar month. Therefore, the start
     * date is the first day of the given month and the end date is the last day of the given month.
     *
     * @param eventType     the type of events to query
     * @param calendarMonth the calendar month to query
     * @param queryTuner    the actual filter to apply
     * @param <E>           the generic types of the entities to query
     * @return the number of events matching the given filter. Note that we return an <tt>int</tt> here to better match
     * the API of {@link sirius.kernel.health.metrics.Metrics}.
     * @throws SQLException in case of a database error
     */
    public <E extends Event<E>> int countEventsInCalendarMonth(Class<E> eventType,
                                                               YearMonth calendarMonth,
                                                               @Nullable Consumer<SmartQuery<E>> queryTuner)
            throws SQLException {
        LocalDateTime startDate = calendarMonth.atDay(1).atStartOfDay();
        LocalDateTime endDate = calendarMonth.atEndOfMonth().atTime(LocalTime.MAX);
        return countEventsInRange(eventType, startDate, endDate, queryTuner);
    }

    /**
     * Counts the number of events which have occurred in the last month based on the given <tt>queryTuner</tt>.
     * <p>
     * This method counts the number of events which have occurred in the last calendar month. Therefore, the
     * start date is the first day of the last month and the end date is the last day of the last month.
     * <p>
     * This automatically marks the query as long-running.
     *
     * @param eventType  the type of events to query
     * @param queryTuner the actual filter to apply
     * @param <E>        the generic types of the entities to query
     * @return the number of events matching the given filter in the last month. Note that we return an <tt>int</tt>
     * here to better match the API of {@link sirius.kernel.health.metrics.Metrics}.
     * @throws SQLException in case of a database error
     */
    public <E extends Event<E>> int countEventsInLastMonth(Class<E> eventType,
                                                           @Nullable Consumer<SmartQuery<E>> queryTuner)
            throws SQLException {
        return countEventsInCalendarMonth(eventType, YearMonth.now().minusMonths(1), queryTuner);
    }

    /**
     * Counts the number of events which have occurred in the last year based on the given <tt>queryTuner</tt>.
     * <p>
     * This method counts the number of events which have occurred in the last calendar year. Therefore, the
     * start date is the first day of the last year and the end date is the last day of the last year. In order to get
     * the past twelve months, use {@link #countEventsInLast12Months(Class, Consumer)}.
     * <p>
     * This automatically marks the query as long-running.
     *
     * @param eventType  the type of events to query
     * @param queryTuner the actual filter to apply
     * @param <E>        the generic types of the entities to query
     * @return the number of events matching the given filter in the last year. Note that we return an <tt>int</tt>
     * here to better match the API of {@link sirius.kernel.health.metrics.Metrics}.
     * @throws SQLException in case of a database error
     */
    public <E extends Event<E>> int countEventsInLastYear(Class<E> eventType,
                                                          @Nullable Consumer<SmartQuery<E>> queryTuner)
            throws SQLException {
        YearMonth lastYearMonth = YearMonth.now().minusYears(1);
        LocalDateTime startDate = lastYearMonth.withMonth(1).atDay(1).atStartOfDay();
        LocalDateTime endDate = lastYearMonth.withMonth(12).atEndOfMonth().atTime(LocalTime.MAX);
        return countEventsInRange(eventType, startDate, endDate, queryTuner);
    }

    /**
     * Counts the number of events which have occurred in the last 12 months based on the given <tt>queryTuner</tt>.
     * <p>
     * This automatically marks the query as long-running.
     *
     * @param eventType  the type of events to query
     * @param queryTuner the actual filter to apply
     * @param <E>        the generic types of the entities to query
     * @return the number of events matching the given filter in the last 12 months. Note that we return an <tt>int</tt>
     * here to better match the API of {@link sirius.kernel.health.metrics.Metrics}.
     * @throws SQLException in case of a database error
     */
    public <E extends Event<E>> int countEventsInLast12Months(Class<E> eventType,
                                                              @Nullable Consumer<SmartQuery<E>> queryTuner)
            throws SQLException {
        LocalDateTime startDate = LocalDateTime.now().minusYears(1).plusMonths(1);
        LocalDateTime endDate = LocalDateTime.now();
        return countEventsInRange(eventType, startDate, endDate, queryTuner);
    }

    /**
     * Sums the values for the provided column of events which have occurred based on the given <tt>queryTuner</tt>.
     * <p>
     * This automatically marks the query as long-running.
     *
     * @param eventType    the type of events to query
     * @param mappingToSum the mapping to sum values for
     * @param queryTuner   the actual filter to apply. Note that {@link Event#EVENT_DATE} should be filtered, as otherwise
     *                     the performance will be catastrophic.
     * @param <E>          the generic types of the entities to query
     * @return the number of events matching the given filter. Note that we return an <tt>int</tt> here to better match
     * the API of {@link sirius.kernel.health.metrics.Metrics}.
     * @throws SQLException in case of a database error
     * @see #countEventsInRange(Class, LocalDateTime, LocalDateTime, Consumer)
     */
    public <E extends Event<E>> int sumEvents(Class<E> eventType,
                                              Mapping mappingToSum,
                                              @Nullable Consumer<SmartQuery<E>> queryTuner) throws SQLException {
        SmartQuery<E> query = oma.select(eventType)
                                 .aggregationField(Strings.apply("sum(%s) AS %s",
                                                                 mappingToSum.getName(),
                                                                 AGGREGATION_SUM));
        if (queryTuner != null) {
            queryTuner.accept(query);
        }

        return query.asSQLQuery()
                    .markAsLongRunning()
                    .first()
                    .flatMap(row -> row.getValue(AGGREGATION_SUM).asOptionalInt())
                    .orElse(0);
    }

    /**
     * Sums the values for the provided column of events which have occurred based on the given <tt>queryTuner</tt>.
     * <p>
     * This automatically marks the query as long-running.
     *
     * @param eventType    the type of events to query
     * @param mappingToSum the mapping to sum values for
     * @param startDate    the start date of the range
     * @param endDate      the end date of the range
     * @param queryTuner   the actual filter to apply
     * @param <E>          the generic types of the entities to query
     * @return the number of events matching the given filter. Note that we return an <tt>int</tt> here to better match
     * the API of {@link sirius.kernel.health.metrics.Metrics}.
     * @throws SQLException in case of a database error
     * @see #countEvents(Class, Consumer)
     */
    public <E extends Event<E>> int sumEventsInRange(Class<E> eventType,
                                                     Mapping mappingToSum,
                                                     LocalDateTime startDate,
                                                     LocalDateTime endDate,
                                                     @Nullable Consumer<SmartQuery<E>> queryTuner) throws SQLException {
        return sumEvents(eventType, mappingToSum, query -> {
            query.where(OMA.FILTERS.gte(Event.EVENT_DATE, startDate.toLocalDate()));
            query.where(OMA.FILTERS.lte(Event.EVENT_DATE, endDate.toLocalDate()));
            if (queryTuner != null) {
                queryTuner.accept(query);
            }
        });
    }

    /**
     * Counts the number of distinct values in the given mapping of events which have occurred based on the given <tt>queryTuner</tt>.
     * <p>
     * This automatically marks the query as long-running.
     *
     * @param eventType  the type of events to query
     * @param mapping    the mapping to count distinct values for
     * @param queryTuner the actual filter to apply. Note that {@link Event#EVENT_DATE} should be filtered, as otherwise
     *                   the performance will be catastrophic.
     * @param <E>        the generic types of the entities to query
     * @return the number of events matching the given filter. Note that we return an <tt>int</tt> here to better match
     * the API of {@link sirius.kernel.health.metrics.Metrics}.
     * @throws SQLException in case of a database error
     */
    public <E extends Event<E>> int countDistinctValuesInEvents(Class<E> eventType,
                                                                Mapping mapping,
                                                                @Nullable Consumer<SmartQuery<E>> queryTuner)
            throws SQLException {
        SmartQuery<E> query = oma.select(eventType)
                                 .aggregationField(Strings.apply("COUNT(DISTINCT %s) as %s",
                                                                 mapping.getName(),
                                                                 AGGREGATION_DISTINCT_COUNT));
        if (queryTuner != null) {
            queryTuner.accept(query);
        }

        return query.asSQLQuery()
                    .markAsLongRunning()
                    .first()
                    .flatMap(row -> row.getValue(AGGREGATION_DISTINCT_COUNT).asOptionalInt())
                    .orElse(0);
    }

    /**
     * Records an event to be stored in the event database within the next insertion run.
     * <p>
     * Note that the event might be dropped if no database is configured or if the internal buffer is overloaded.
     * <p>
     * Also note that all {@link sirius.db.mixing.annotations.BeforeSave before save handlers} are invoked
     * in this thread so that the event is properly populated.
     *
     * @param event the event to record
     */
    public void record(@Nonnull Event<?> event) {
        if (!configured) {
            return;
        }

        if (bufferedEvents.get() >= MAX_BUFFER_SIZE) {
            return;
        }

        try {
            event.getDescriptor().beforeSave(event);
            buffer.offer(event);
            bufferedEvents.incrementAndGet();
        } catch (HandledException exception) {
            Log.BACKGROUND.WARN("An event was not recorded due to a before-save warning. Event: %s (%s): %s",
                                event.toString(),
                                event.getClass().getSimpleName(),
                                exception.getMessage());
        } catch (Exception exception) {
            Exceptions.handle(Log.BACKGROUND, exception);
        }
    }

    /**
     * Invoked periodically by the {@link EventProcessorLoop} to process events if necessary.
     * <p>
     * An insertion run will be started if there are enough events in the buffer (more than {@link #MIN_BUFFER_SIZE})
     * or if enough time elapsed since the last insertion run (more than {@link #MAX_BUFFER_AGE} or
     * {@link #MAX_BUFFER_AGE_DEV} in development systems).
     *
     * @return the number of inserted events
     */
    protected int processIfBufferIsFilled() {
        if (bufferedEvents.get() > MIN_BUFFER_SIZE
            || lastProcessed == null
            || Duration.between(lastProcessed, LocalDateTime.now()).compareTo(getEffectiveMaxAge()) > 0) {
            return process();
        } else {
            return 0;
        }
    }

    private Duration getEffectiveMaxAge() {
        return Sirius.isDev() ? MAX_BUFFER_AGE_DEV : MAX_BUFFER_AGE;
    }

    /**
     * Processes the queued events by creating a {@link BatchContext} and batch-inserting all events.
     *
     * @return the number of inserted events
     */
    protected int process() {
        lastProcessed = LocalDateTime.now();
        int processedEvents = 0;
        try (BatchContext ctx = new BatchContext(() -> "Process recorded events.", Duration.ofMinutes(1))) {
            Map<Class<? extends Event<?>>, InsertQuery<Event<?>>> queries = new HashMap<>();
            Event<?> nextEvent = fetchBufferedEvent();
            while (nextEvent != null) {
                processEvent(ctx, queries, nextEvent);
                if (++processedEvents >= MAX_EVENTS_PER_PROCESS) {
                    return processedEvents;
                }

                nextEvent = fetchBufferedEvent();
            }
        } catch (HandledException exception) {
            Exceptions.ignore(exception);
        } catch (Exception exception) {
            Exceptions.handle(Log.BACKGROUND, exception);
        }

        return processedEvents;
    }

    /**
     * Inserts a single event by either creating a new {@link InsertQuery} or by appending a batch-insert for an
     * existing one (there is one per event type in <tt>queries</tt>).
     *
     * @param ctx     the batch context
     * @param queries the map of prepared insert queries per event type
     * @param event   the event to insert
     */
    @SuppressWarnings("unchecked")
    private void processEvent(BatchContext ctx,
                              Map<Class<? extends Event<?>>, InsertQuery<Event<?>>> queries,
                              Event<?> event) {
        try {
            InsertQuery<Event<?>> query = queries.computeIfAbsent((Class<Event<?>>) event.getClass(),
                                                                  type -> (InsertQuery<Event<?>>) ctx.insertQuery(type,
                                                                                                                  false));
            query.insert(event, false, true);
        } catch (Exception exception) {
            if (!event.retried) {
                event.retried = true;
                record(event);
            }
            throw exception;
        }
    }

    /**
     * Fetches the next event to process.
     *
     * @return the next event to process or <tt>null</tt> to indicate that the buffer queue is empty.
     */
    @Nullable
    protected Event<?> fetchBufferedEvent() {
        Event<?> result = buffer.poll();
        if (result != null) {
            bufferedEvents.decrementAndGet();
        }

        return result;
    }

    /// Fetches all user events which match the given query assuming that users can only trigger one event (of the type
    /// in question) at the same time.
    ///
    /// @param query the query to execute
    /// @param <E>   the type of the events to fetch
    /// @return a stream of events which match the given query
    public <E extends Event<E> & UserEvent> Stream<E> fetchUserEventsBlockwise(SmartQuery<E> query) {
        return fetchEventsBlockwise(query,
                                    List.of(UserEvent.USER_DATA.inner(UserData.SCOPE_ID),
                                            UserEvent.USER_DATA.inner(UserData.TENANT_ID),
                                            UserEvent.USER_DATA.inner(UserData.USER_ID)));
    }

    /// Fetches all events which match the given query considering the given duplicate preventer.
    ///
    /// @param query              the query to execute
    /// @param duplicatePreventer the duplicate preventer to apply to prevent fetching the same events multiple times
    /// @param <E>                the type of the events to fetch
    /// @return a stream of events which match the given query
    /// @see EventSpliterator for a detailed explanation of the duplicate preventer
    public <E extends Event<E>> Stream<E> fetchEventsBlockwise(SmartQuery<E> query,
                                                               BiConsumer<SmartQuery<E>, List<E>> duplicatePreventer) {
        return StreamSupport.stream(new EventSpliterator<>(query, duplicatePreventer), false);
    }

    /// Fetches all events which match the given query considering the given distinct fields to prevent duplicates.
    ///
    /// @param query          the query to execute
    /// @param distinctFields the fields to consider when preventing duplicates
    /// @param <E>            the type of the events to fetch
    /// @return a stream of events which match the given query
    /// @see EventSpliterator#EventSpliterator(SmartQuery, List)  for a detailed explanation of the distinct fields
    public <E extends Event<E>> Stream<E> fetchEventsBlockwise(SmartQuery<E> query, List<Mapping> distinctFields) {
        return StreamSupport.stream(new EventSpliterator<E>(query, distinctFields), false);
    }
}
