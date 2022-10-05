/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.events;

import sirius.db.jdbc.SQLEntity;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.Engine;
import sirius.db.mixing.annotations.Realm;
import sirius.db.mixing.annotations.Transient;
import sirius.kernel.async.CallContext;
import sirius.kernel.commons.Strings;

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
    public static final Mapping EVENT_DATE = Mapping.named("eventDate");
    private LocalDate eventDate;

    /**
     * Contains the date and time when the event occurred.
     */
    public static final Mapping EVENT_TIMESTAMP = Mapping.named("eventTimestamp");
    private LocalDateTime eventTimestamp;

    /**
     * Contains the name of the node on which the event occurred.
     */
    public static final Mapping NODE = Mapping.named("node");
    private String node;

    /**
     * Fills basic fields just before the event is queued by the {@link EventRecorder}.
     */
    @BeforeSave
    protected void fill() {
        if (eventTimestamp == null) {
            eventTimestamp = LocalDateTime.now();
        }
        eventDate = eventTimestamp.toLocalDate();

        if (Strings.isEmpty(node)) {
            node = CallContext.getNodeName();
        }
    }

    /**
     * Sets a custom event timestamp.
     * <p>
     * In most cases this method shouldn't be called manually as the event will initialize this field with <tt>now</tt>.
     *
     * @param eventTimestamp the {@link LocalDateTime} the event occurred.
     * @return convenience reference to <tt>this</tt> for fluent method calls
     */
    public Event withCustomEventTimestamp(LocalDateTime eventTimestamp) {
        this.eventTimestamp = eventTimestamp;
        return this;
    }

    /**
     * Sets a custom event timestamp.
     * <p>
     * In most cases this method shouldn't be called manually as the event will initialize this field with <tt>now</tt>.
     *
     * @param eventTimestamp the {@link LocalDateTime} the event occurred.
     * @deprecated Use {@link #withCustomEventTimestamp(LocalDateTime)} instead.
     */
    @Deprecated(since = "2022/10/05")
    public void setCustomEventTimestamp(LocalDateTime eventTimestamp) {
        this.eventTimestamp = eventTimestamp;
    }

    /**
     * Sets a custom node on which this event was recorded.
     * <p>
     * In most cases this method shouldn't be called manually as the event will initialize this field with
     * the name of the current node.
     *
     * @param node the node name to use
     * @return convenience reference to <tt>this</tt> for fluent method calls
     */
    public Event withCustomNode(String node) {
        this.node = node;
        return this;
    }

    /**
     * Sets a custom node on which this event was recorded.
     * <p>
     * In most cases this method shouldn't be called manually as the event will initialize this field with
     * the name of the current node.
     *
     * @param node the node name to use
     * @deprecated Use {@link #withCustomNode(String)} instead.
     */
    @Deprecated(since = "2022/10/05")
    public void setCustomNode(String node) {
        this.node = node;
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
