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
import sirius.db.mixing.annotations.Lob;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Created by aha on 18.02.16.
 */
public class StoredIncident extends Entity {

    @Lob
    private String message;
    public static final Column MESSAGE = Column.named("message");

    private String category;
    public static final Column CATEGORY = Column.named("category");

    private String node;
    public static final Column NODE = Column.named("node");

    private String location;
    public static final Column LOCATION = Column.named("location");

    @Lob
    private String stack;
    public static final Column STACK = Column.named("stack");

    private LocalDateTime firstOccurrence = LocalDateTime.now();
    public static final Column FIRST_OCCURRENCE = Column.named("firstOccurrence");

    private LocalDateTime lastOccurrence = LocalDateTime.now();
    public static final Column LAST_OCCURRENCE = Column.named("lastOccurrence");

    private int numberOfOccurrences = 0;
    public static final Column NUMBER_OF_OCCURRENCES = Column.named("numberOfOccurrences");

    @Lob
    private String mdc;
    public static final Column MDC = Column.named("mdc");

    private String user;
    public static final Column USER = Column.named("user");

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
