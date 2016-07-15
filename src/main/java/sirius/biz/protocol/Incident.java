/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.protocol;

import sirius.db.mixing.Column;
import sirius.db.mixing.Entity;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.Lob;
import sirius.kernel.async.CallContext;
import sirius.kernel.di.std.Framework;

import java.time.LocalDateTime;

/**
 * Stores a recorded error in the database.
 * <p>
 * Incidents at the same location are grouped together for a certain timespan so that heavily re-occuring problems
 * don't overload the system.
 */
@Framework(Protocols.FRAMEWORK_PROTOCOLS)
public class Incident extends Entity {

    /**
     * Contains the error message.
     */
    public static final Column MESSAGE = Column.named("message");
    @Lob
    private String message;

    /**
     * Contains the logger category which recorded the error.
     */
    public static final Column CATEGORY = Column.named("category");
    @Length(255)
    private String category;

    /**
     * Contains the name of the server/node on which the error occured.
     */
    public static final Column NODE = Column.named("node");
    @Length(255)
    private String node;

    /**
     * Contains the location where the error occured.
     * <p>
     * This is only used to de-duplicate similar incidents. The real location can be found the the recorded {@link
     * #stack}.
     */
    public static final Column LOCATION = Column.named("location");
    @Length(255)
    private String location;

    /**
     * Contains the recorded stacktrace of the error or exception.
     */
    public static final Column STACK = Column.named("stack");
    @Lob
    private String stack;

    /**
     * Contains the timestamp of the first occurrence.
     */
    public static final Column FIRST_OCCURRENCE = Column.named("firstOccurrence");
    private LocalDateTime firstOccurrence = LocalDateTime.now();

    /**
     * Contains the timestamp of the last occurrence.
     */
    public static final Column LAST_OCCURRENCE = Column.named("lastOccurrence");
    private LocalDateTime lastOccurrence = LocalDateTime.now();

    /**
     * Contains the number of occurrences within the given time span.
     */
    public static final Column NUMBER_OF_OCCURRENCES = Column.named("numberOfOccurrences");
    private int numberOfOccurrences = 0;

    /**
     * Contains the recorded mapped diagnostic context.
     *
     * @see CallContext#getMDC()
     */
    public static final Column MDC = Column.named("mdc");
    @Lob
    private String mdc;

    /**
     * Contains the name of the user which was present when the error occured.
     */
    public static final Column USER = Column.named("user");
    @Length(255)
    private String user;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getNode() {
        return node;
    }

    public void setNode(String node) {
        this.node = node;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getStack() {
        return stack;
    }

    public void setStack(String stack) {
        this.stack = stack;
    }

    public LocalDateTime getFirstOccurrence() {
        return firstOccurrence;
    }

    public void setFirstOccurrence(LocalDateTime firstOccurrence) {
        this.firstOccurrence = firstOccurrence;
    }

    public LocalDateTime getLastOccurrence() {
        return lastOccurrence;
    }

    public void setLastOccurrence(LocalDateTime lastOccurrence) {
        this.lastOccurrence = lastOccurrence;
    }

    public int getNumberOfOccurrences() {
        return numberOfOccurrences;
    }

    public void setNumberOfOccurrences(int numberOfOccurrences) {
        this.numberOfOccurrences = numberOfOccurrences;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getMdc() {
        return mdc;
    }

    public void setMdc(String mdc) {
        this.mdc = mdc;
    }
}
