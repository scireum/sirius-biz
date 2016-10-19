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

import java.time.LocalDate;

public class StatisticValue extends Entity {

    public static final Column EVENT = Column.named("event");
    @Length(50)
    private String event;

    public static final Column OBJECT_ID = Column.named("objectId");
    @Length(50)
    private String objectId;

    public static final Column TOD = Column.named("tod");
    private LocalDate tod;

    @Length(15)
    public static final Column LEVEL = Column.named("level");
    private AggregationLevel level;

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
