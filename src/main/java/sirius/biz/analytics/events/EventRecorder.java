/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.events;

import sirius.db.jdbc.Database;
import sirius.db.jdbc.SQLQuery;
import sirius.db.jdbc.batch.BatchContext;
import sirius.db.jdbc.batch.InsertQuery;
import sirius.db.jdbc.schema.Schema;
import sirius.db.mixing.annotations.Realm;
import sirius.kernel.Sirius;
import sirius.kernel.Startable;
import sirius.kernel.Stoppable;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.health.Log;
import sirius.kernel.health.metrics.MetricProvider;
import sirius.kernel.health.metrics.MetricsCollector;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

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
    private static final int MAX_BUFFER_SIZE = 16 * 1024;

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

    private LocalDateTime lastProcessed;
    private final AtomicInteger bufferedEvents = new AtomicInteger();
    private final Queue<Event> buffer = new ConcurrentLinkedQueue<>();

    @Part
    private Schema schema;

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
                                "%");
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
     * Records an event to be stored in the event database within the next insertion run.
     * <p>
     * Note that the event might be dropped if no database is configured or if the internal buffer is overloaded.
     * <p>
     * Also note that all {@link sirius.db.mixing.annotations.BeforeSave before save handlers} are invoked
     * in this thread so that the event is properly populated.
     *
     * @param event the event to record
     */
    public void record(@Nonnull Event event) {
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
        } catch (Exception e) {
            Exceptions.handle(Log.BACKGROUND, e);
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
            Map<Class<? extends Event>, InsertQuery<Event>> queries = new HashMap<>();
            Event nextEvent = fetchBufferedEvent();
            while (nextEvent != null) {
                processEvent(ctx, queries, nextEvent);
                if (++processedEvents >= MAX_EVENTS_PER_PROCESS) {
                    return processedEvents;
                }

                nextEvent = fetchBufferedEvent();
            }
        } catch (HandledException e) {
            Exceptions.ignore(e);
        } catch (Exception e) {
            Exceptions.handle(Log.BACKGROUND, e);
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
    private void processEvent(BatchContext ctx, Map<Class<? extends Event>, InsertQuery<Event>> queries, Event event) {
        try {
            InsertQuery<Event> qry = queries.computeIfAbsent(event.getClass(),
                                                             type -> (InsertQuery<Event>) ctx.insertQuery(type, false));
            qry.insert(event, false, true);
        } catch (Exception e) {
            if (!event.retried) {
                event.retried = true;
                record(event);
            }
            throw e;
        }
    }

    /**
     * Fetches the next event to process.
     *
     * @return the next event to process or <tt>null</tt> to indicate that the buffer queue is empty.
     */
    @Nullable
    protected Event fetchBufferedEvent() {
        Event result = buffer.poll();
        if (result != null) {
            bufferedEvents.decrementAndGet();
        }

        return result;
    }
}
