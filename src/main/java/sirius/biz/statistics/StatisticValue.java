/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.statistics;

import sirius.db.mixing.Column;
import sirius.db.mixing.Entity;
import sirius.db.mixing.annotations.Length;
import sirius.kernel.di.std.Framework;

import java.time.LocalDate;

/**
 * Represents a statictical value stored per event, timestamp and aggregation level.
 *
 * @see Statistics
 */
@Framework("statistics")
public class StatisticValue extends Entity {

    /**
     * Contains the event name of this statistic value.
     */
    public static final Column EVENT = Column.named("event");
    @Length(50)
    private String event;

    /**
     * Coontains the ID of the object for which the statistic value is recorded.
     */
    public static final Column OBJECT_ID = Column.named("objectId");
    @Length(50)
    private String objectId;

    /**
     * Contains the timestamp of the statistic value.
     */
    public static final Column TOD = Column.named("tod");
    private LocalDate tod;

    /**
     * Contains the aggregation level of the statistic value.
     */
    @Length(15)
    public static final Column LEVEL = Column.named("level");
    private AggregationLevel level;

    /**
     * Contains the actual value.
     */
    public static final Column STATISTIC_VALUE = Column.named("statisticValue");
    private long statisticValue = 0L;

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public String getObjectId() {
        return objectId;
    }

    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }

    public LocalDate getTod() {
        return tod;
    }

    public void setTod(LocalDate tod) {
        this.tod = tod;
    }

    public AggregationLevel getLevel() {
        return level;
    }

    public void setLevel(AggregationLevel level) {
        this.level = level;
    }

    public long getStatisticValue() {
        return statisticValue;
    }

    public void setStatisticValue(long statisticValue) {
        this.statisticValue = statisticValue;
    }
}
