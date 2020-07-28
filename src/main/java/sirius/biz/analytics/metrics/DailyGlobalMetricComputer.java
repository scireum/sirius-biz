/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.metrics;

import sirius.kernel.di.std.Part;

import javax.annotation.Nullable;
import java.time.LocalDate;

/**
 * Provides a base class for all metric computers which are invoked on a daily basis to compute a global metric.
 * <p>
 * Subclasses have to be {@link sirius.kernel.di.std.Register registered} as <tt>DailyGlobalMetricComputer</tt> so that
 * they are visible to the framework.
 */
public abstract class DailyGlobalMetricComputer {

    @Part
    @Nullable
    protected Metrics metrics;

    /**
     * Performs the computation for the given date.
     *
     * @param date the date for which the computation should be performed
     */
    public abstract void compute(LocalDate date);
}
