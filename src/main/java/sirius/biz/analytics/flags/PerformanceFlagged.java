/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.flags;

import sirius.db.mixing.Mapping;
import sirius.kernel.commons.Explain;

/**
 * Marks entities which contain a {@link PerformanceData}.
 */
@SuppressWarnings("java:S1214")
@Explain("We rather keep this constant here in shared place.")
public interface PerformanceFlagged {

    /**
     * Contains the name used for the performance data composite.
     * <p>
     * Note that when exporting flags, the mapping {@link PerformanceDataImportExtender#PERFORMANCE_FLAGS} should be
     * used to obtain a readable list of flags instead of the internal representation.
     */
    Mapping PERFORMANCE_DATA = Mapping.named("performanceData");

    /**
     * Returns the performance data of the underlying entity.
     *
     * @return the performance data composite of this entity
     */
    PerformanceData getPerformanceData();
}
