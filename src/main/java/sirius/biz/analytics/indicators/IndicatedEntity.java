/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.indicators;

import sirius.db.mixing.Mapping;
import sirius.kernel.commons.Explain;

/**
 * Marks an entity as being eligible for {@link Indicator indicators}.
 * <p>
 * This basically ensures that the required {@link IndicatorData} composite is present.
 */
@SuppressWarnings("squid:S1214")
@Explain("The constant is best kept here for consistency reasons.")
public interface IndicatedEntity {

    /**
     * Provides the default mapping for accessing the indicator data.
     */
    Mapping INDICATORS = Mapping.named("indicators");

    /**
     * Returns the collected indicators for this entity.
     *
     * @return the indicators present for this entity
     */
    IndicatorData getIndicators();
}
