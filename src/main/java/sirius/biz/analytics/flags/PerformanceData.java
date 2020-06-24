/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.flags;

import sirius.biz.analytics.flags.jdbc.SQLPerformanceData;
import sirius.biz.analytics.flags.mongo.MongoPerformanceData;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Composite;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.Transient;

import java.util.stream.Stream;

/**
 * Can be embedded into an {@link BaseEntity entity} in order to record / toggle performance flags for it.
 * <p>
 * Note that this provides an abstract and database independent part. Use {@link SQLPerformanceData} or
 * {@link MongoPerformanceData} depending on the actual entity type.
 */
public abstract class PerformanceData extends Composite {

    /**
     * Contains the name of the field which stores the flags.
     */
    public static final Mapping FLAGS = Mapping.named("flags");

    @Transient
    protected BaseEntity<?> owner;

    protected PerformanceData(BaseEntity<?> owner) {
        this.owner = owner;
    }

    /**
     * Obtains a modifier which can be used to efficiently update the flags for the underlying entity.
     *
     * @return a modifier used to change the flags for the underlying entity
     */
    public abstract PerformanceFlagModifier modify();

    /**
     * Determines if a given performance flag is set.
     *
     * @param flag the flag  to check
     * @return <tt>true</tt> if the flag is toggled, <tt>false</tt> otherwise
     */
    public abstract boolean isSet(PerformanceFlag flag);

    /**
     * Returns all toggled / active flags.
     *
     * @return a stream of active flags for the underlying entity
     */
    @SuppressWarnings("unchecked")
    public Stream<PerformanceFlag> activeFlags() {
        return PerformanceFlag.flagsOfType(owner.getClass()).filter(this::isSet);
    }

    public BaseEntity<?> getOwner() {
        return owner;
    }
}
