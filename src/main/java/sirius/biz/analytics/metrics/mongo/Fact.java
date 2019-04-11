/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.metrics.mongo;

import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.Index;
import sirius.db.mixing.annotations.Length;
import sirius.db.mongo.Mango;
import sirius.db.mongo.MongoEntity;
import sirius.kernel.di.std.Framework;

/**
 * Stores facts (timeless metrics) as a table.
 * <p>
 * Note that metrics should only be accessed via {@link sirius.biz.analytics.metrics.Metrics}.
 */
@Framework(MongoMetrics.FRAMEWORK_MONGO_METRICS)
@Index(name = "lookup",
        columns = {"targetType", "targetId", "name"},
        columnSettings = {Mango.INDEX_ASCENDING, Mango.INDEX_ASCENDING, Mango.INDEX_ASCENDING})
public class Fact extends MongoEntity {

    /**
     * Contains the type of object for which this metric is recorded.
     */
    public static final Mapping TARGET_TYPE = Mapping.named("targetType");
    @Length(50)
    private String targetType;

    /**
     * Contains the id of the object for which this metric is recorded.
     */
    public static final Mapping TARGET_ID = Mapping.named("targetId");
    @Length(50)
    private String targetId;

    /**
     * Contains the name of the metric.
     */
    public static final Mapping NAME = Mapping.named("name");
    @Length(50)
    private String name;

    /**
     * Contains the value (amount) of the metric for the given month.
     */
    public static final Mapping VALUE = Mapping.named("value");
    private int value;

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }
}
