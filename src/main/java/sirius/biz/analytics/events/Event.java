/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.events;

import sirius.db.jdbc.SQLEntity;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.Engine;
import sirius.db.mixing.annotations.Realm;
import sirius.db.mixing.annotations.Transient;
import sirius.kernel.async.CallContext;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Provides a base class for all events recorded by the {@link EventRecorder}.
 * <p>
 * Sub-classes will be persisted in the <tt>analytics</tt> database realm which is expected to
 * point to a <b>Clickhouse</b> database.
 */
@Realm("analytics")
@Engine("MergeTree() PARTITION BY toYYYYMM(eventDate) ORDER BY (eventDate, eventTimestamp)")
public abstract class Event extends SQLEntity {

    /**
     * Represents an internal in-memory flag which determines if storing the entry has already been retried.
     * <p>
     * As the database connection might be interrupted, we permit a single retry for each event, after that,
     * collected events are discarded.
     */
    @Transient
    protected volatile boolean retried;

    /**
     * Contains the date when the event occurred.
     * <p>
     * Note that this is redundant to {@link #eventTimestamp} and only required to be used as index by <b>Clickhouse</b>.
     */
    private LocalDate eventDate;

    /**
     * Contains the date and time when the event occurred.
     */
    private LocalDateTime eventTimestamp;

    /**
     * Contains the name of the node on which the event occurred.
     */
    private String node;

    /**
     * Fills basic fields just before the event is queued by the {@link EventRecorder}.
     */
    @BeforeSave
    protected void fill() {
        eventTimestamp = LocalDateTime.now();
        eventDate = eventTimestamp.toLocalDate();
        node = CallContext.getNodeName();
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public LocalDateTime getEventTimestamp() {
        return eventTimestamp;
    }

    public String getNode() {
        return node;
    }
}
