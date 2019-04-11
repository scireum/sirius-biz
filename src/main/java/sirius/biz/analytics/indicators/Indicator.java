/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.indicators;

import sirius.db.mixing.BaseEntity;

/**
 * Provides an indicator which is executed per entity.
 * <p>
 * If the execution yields <tt>true</tt> the entity is flagged with the name of this indicator in its
 * {@link IndicatorData#getIndications()}.
 * <p>
 * For complex computations, it is advisable to enable <b>batch execution</b>. Note however, that a
 * {@link BatchIndicatorCheck} has to be present for the underlying entity type.
 *
 * @param <E> the type of entities being checked
 */
public interface Indicator<E extends BaseEntity<?> & IndicatedEntity> {

    /**
     * Returns the type of entities being checked by this indicator.
     *
     * @return the type of entities being checked
     */
    Class<E> getType();

    /**
     * Determines if the indicator should be executed in a batch run.
     *
     * @return <tt>true</tt> to execute the indicator in daily batch runs, <tt>false</tt> to execute them within the
     * <tt>before save</tt> handlers of the entity.
     */
    boolean isBatch();

    /**
     * Executes the check for the given entity.
     *
     * @param entity the entity to execute the indicator for
     * @return <tt>true</tt> if the indication is present, <tt>false</tt> otherwise
     */
    boolean executeFor(E entity);

    /**
     * Returns the name of this indicator / indication.
     *
     * @return the name representing this indicator
     */
    String getName();
}
